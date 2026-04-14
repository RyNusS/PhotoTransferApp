package com.family.phototransfer.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.family.phototransfer.network.TransferEvent
import com.family.phototransfer.network.TransferManager
import com.family.phototransfer.network.WifiDeviceScanner
import com.family.phototransfer.ui.settings.dataStore
import com.family.phototransfer.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

private const val TAG = "AutoSyncWorker"

// WorkManager Input Data 키
const val KEY_RECEIVER_IP    = "receiver_ip"
const val KEY_SYNC_FROM_DATE = "sync_from_date"
const val KEY_SYNC_FOLDERS   = "sync_folders"
const val KEY_WIFI_ONLY      = "wifi_only"
const val KEY_NOTIFY_ON_SEND = "notify_on_send"

@HiltWorker
class AutoSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val transferManager: TransferManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "자동 동기화 시작")
        return try {
            if (!isWifiConnected()) {
                Log.d(TAG, "WiFi 미연결 - 재시도")
                return Result.retry()
            }

            val receiverIp = findReceiverDevice()
            if (receiverIp == null) {
                Log.d(TAG, "수신 기기 없음 - 재시도")
                return Result.retry()
            }

            // DataStore에서 최신 설정 읽기
            val prefs = context.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
            val syncFromDate  = prefs[stringPreferencesKey("sync_from_date")]  ?: "ALL"
            val syncFolderRaw = prefs[stringPreferencesKey("sync_folders")]    ?: ""
            val notifyOnSend  = prefs[booleanPreferencesKey("notify_on_send")] ?: true

            val enabledFolders = parseSyncFolders(syncFolderRaw)
            val cutoffMs       = resolveCutoffMs(syncFromDate)

            Log.d(TAG, "동기화 조건 - 날짜: $syncFromDate, 폴더: ${enabledFolders.ifEmpty { setOf("전체") }}")

            val mediaFiles = getFilteredMediaFiles(cutoffMs, enabledFolders)
            if (mediaFiles.isEmpty()) {
                Log.d(TAG, "동기화할 파일 없음")
                return Result.success()
            }
            Log.d(TAG, "동기화 대상: ${mediaFiles.size}개")

            val events = transferManager.transferFiles(
                context          = context,
                files            = mediaFiles,
                receiverIp       = receiverIp,
                sourceDeviceName = android.os.Build.MODEL
            ).toList()

            val successCount   = events.count { it is TransferEvent.Completed }
            val duplicateCount = events.count { it is TransferEvent.Duplicate }
            val failedCount    = events.count { it is TransferEvent.Failed }
            Log.d(TAG, "완료 - 성공: $successCount, 중복: $duplicateCount, 실패: $failedCount")

            NotificationHelper.showTransferComplete(
                context        = context,
                successCount   = successCount,
                duplicateCount = duplicateCount,
                failedCount    = failedCount,
                enabled        = notifyOnSend
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "자동 동기화 오류: ${e.message}")
            Result.retry()
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun findReceiverDevice(): String? {
        val prefs = context.dataStore.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .first()

        // 원격 연결 모드: Tailscale 호스트 주소 우선 사용, WiFi 스캔 생략
        val useRemoteMode = prefs[booleanPreferencesKey("use_remote_mode")] ?: false
        if (useRemoteMode) {
            return prefs[stringPreferencesKey("remote_host")]?.takeIf { it.isNotBlank() }
        }

        // 로컬 모드: DataStore에 저장된 IP 우선, 없으면 WiFi 스캔
        val saved = prefs[stringPreferencesKey("pixel_ip")]?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        return withContext(Dispatchers.IO) {
            WifiDeviceScanner(context).scanNetwork().firstOrNull()?.ipAddress
        }
    }

    private fun resolveCutoffMs(syncFromDate: String): Long {
        val cal = Calendar.getInstance()
        return when (syncFromDate) {
            "ALL"      -> 0L
            "RECENT_3" -> { cal.add(Calendar.DAY_OF_YEAR, -2); cal.timeInMillis }
            "RECENT_7" -> { cal.add(Calendar.DAY_OF_YEAR, -6); cal.timeInMillis }
            else       -> runCatching {
                val parts = syncFromDate.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
                cal.timeInMillis
            }.getOrDefault(0L)
        }
    }

    // "path::name::enabled::count|..." 형식에서 enabled=true인 경로만 추출
    private fun parseSyncFolders(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return raw.split("|").mapNotNull { entry ->
            val parts   = entry.split("::")
            val path    = parts.getOrNull(0) ?: return@mapNotNull null
            val enabled = parts.getOrNull(2)?.toBoolean() ?: true
            if (enabled) path else null
        }.toSet()
    }

    private suspend fun getFilteredMediaFiles(
        cutoffMs:       Long,
        enabledFolders: Set<String>
    ): List<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        val result    = mutableListOf<Pair<Uri, String>>()
        val cutoffSec = cutoffMs / 1000

        val imageUri  = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageProj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        val imageSel  = if (cutoffSec > 0) "${MediaStore.Images.Media.DATE_MODIFIED} >= ?" else null
        val imageArgs = if (cutoffSec > 0) arrayOf(cutoffSec.toString()) else null

        context.contentResolver.query(imageUri, imageProj, imageSel, imageArgs,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val filePath   = cursor.getString(dataCol) ?: continue
                val folderPath = java.io.File(filePath).parent ?: continue
                if (enabledFolders.isNotEmpty() && folderPath !in enabledFolders) continue
                val id = cursor.getLong(idCol)
                result.add(Uri.parse("$imageUri/$id") to cursor.getString(nameCol))
            }
        }

        val videoUri  = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoProj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA
        )
        val videoSel  = if (cutoffSec > 0) "${MediaStore.Video.Media.DATE_MODIFIED} >= ?" else null
        val videoArgs = if (cutoffSec > 0) arrayOf(cutoffSec.toString()) else null

        context.contentResolver.query(videoUri, videoProj, videoSel, videoArgs,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            while (cursor.moveToNext()) {
                val filePath   = cursor.getString(dataCol) ?: continue
                val folderPath = java.io.File(filePath).parent ?: continue
                if (enabledFolders.isNotEmpty() && folderPath !in enabledFolders) continue
                val id = cursor.getLong(idCol)
                result.add(Uri.parse("$videoUri/$id") to cursor.getString(nameCol))
            }
        }

        result
    }
}

// ── AlarmReceiver — 지정 시각에 WorkManager 즉시 실행 ─────────
class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "알람 수신 → WorkManager 즉시 실행")
        AutoSyncScheduler.runOnce(context)

        // ✅ 다음 주기 알람 재등록 (설정값 읽어서)
        AutoSyncScheduler.rescheduleNextAlarm(context)
    }
}

// ── 스케줄러 ──────────────────────────────────────────────────
object AutoSyncScheduler {

    private const val WORK_NAME      = "auto_sync_work"
    private const val ALARM_REQ_CODE = 9001
    // SharedPreferences 키 (DataStore 대신 AlarmManager 재등록용으로 간단히 사용)
    private const val PREF_NAME      = "alarm_prefs"
    private const val KEY_START_HOUR = "alarm_start_hour"
    private const val KEY_START_MIN  = "alarm_start_min"
    private const val KEY_INTERVAL   = "alarm_interval_hours"
    private const val KEY_ENABLED    = "alarm_enabled"

    /**
     * 자동 동기화 등록
     * - WorkManager: 주기적 실행 보장 (배터리 최적화 우회)
     * - AlarmManager: 정확한 시작 시각 제어
     */
    fun schedule(
        context:       Context,
        intervalHours: Int     = 24,
        startHour:     Int     = 8,
        startMinute:   Int     = 0,
        receiverIp:    String  = "",
        syncFromDate:  String  = "ALL",
        syncFolders:   String  = "",
        notifyOnSend:  Boolean = true,
        wifiOnly:      Boolean = true
    ) {
        // 설정값 SharedPreferences에 저장 (AlarmReceiver 재등록 시 사용)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_START_HOUR, startHour)
            .putInt(KEY_START_MIN, startMinute)
            .putInt(KEY_INTERVAL, intervalHours)
            .putBoolean(KEY_ENABLED, true)
            .apply()

        // WorkManager 주기 등록 (백업용 — 알람 놓쳤을 때 대비)
        val constraints = Constraints.Builder()
            .apply { if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED) }
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(
            intervalHours.toLong().coerceAtLeast(1L), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(calcDelayMs(startHour, startMinute), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        // ✅ AlarmManager로 정확한 시각 첫 실행 등록
        scheduleAlarm(context, startHour, startMinute)
        Log.d(TAG, "스케줄 등록: ${startHour}:${"%02d".format(startMinute)}, ${intervalHours}h 주기")
    }

    /** AlarmReceiver에서 다음 주기 재등록 시 호출 */
    fun rescheduleNextAlarm(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val enabled  = prefs.getBoolean(KEY_ENABLED, false)
        if (!enabled) return
        val hour     = prefs.getInt(KEY_START_HOUR, 8)
        val minute   = prefs.getInt(KEY_START_MIN, 0)
        val interval = prefs.getInt(KEY_INTERVAL, 24)

        // 다음 실행 시각 = 지금으로부터 interval시간 후 or 내일 같은 시각 중 더 가까운 것
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi       = buildPendingIntent(context)

        val nextMs = System.currentTimeMillis() + (interval * 3_600_000L)
        try {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMs, pi)
            Log.d(TAG, "다음 알람 재등록: ${interval}h 후")
        } catch (e: SecurityException) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, nextMs, pi)
        }
    }

    private fun scheduleAlarm(context: Context, hour: Int, minute: Int) {
        val pi  = buildPendingIntent(context)
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            Log.d(TAG, "알람 등록: ${cal.time}")
        } catch (e: SecurityException) {
            mgr.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun buildPendingIntent(context: Context) =
        PendingIntent.getBroadcast(
            context, ALARM_REQ_CODE,
            Intent(context, SyncAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun calcDelayMs(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis - System.currentTimeMillis()
    }

    /** 즉시 1회 실행 */
    fun runOnce(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        Log.d(TAG, "즉시 동기화 실행")
    }

    /** 스케줄 전체 취소 */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(buildPendingIntent(context))
        } catch (_: Exception) {}
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, false).apply()
        Log.d(TAG, "스케줄 취소")
    }

    fun getStatus(context: Context) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
}
