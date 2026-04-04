package com.family.phototransfer.scheduler

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.family.phototransfer.network.TransferEvent
import com.family.phototransfer.network.TransferManager
import com.family.phototransfer.network.WifiDeviceScanner
import com.family.phototransfer.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "AutoSyncWorker"

const val KEY_RECEIVER_IP    = "receiver_ip"
const val KEY_SYNC_FROM_DATE = "sync_from_date"   // "YYYY-MM-DD", "ALL", "RECENT_3", "RECENT_7"
const val KEY_SYNC_FOLDERS   = "sync_folders"     // "path1|path2|..." (빈 문자열 = 전체)
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
            // 1) WiFi 연결 확인
            if (!isWifiConnected()) {
                Log.d(TAG, "WiFi 미연결 - 동기화 건너뜀")
                return Result.retry()
            }

            // 2) 수신 기기 IP 결정
            val receiverIp = findReceiverDevice()
            if (receiverIp == null) {
                Log.d(TAG, "수신 기기 없음 - 동기화 건너뜀")
                return Result.retry()
            }

            // 3) 동기화 조건 읽기
            val syncFromDate   = inputData.getString(KEY_SYNC_FROM_DATE) ?: "ALL"
            val syncFolderRaw  = inputData.getString(KEY_SYNC_FOLDERS)   ?: ""
            // ✅ 활성화된 폴더 경로 목록 (비어있으면 전체 폴더)
            val allowedFolders = if (syncFolderRaw.isBlank()) emptySet()
                                 else syncFolderRaw.split("|").filter { it.isNotBlank() }.toSet()

            Log.d(TAG, "동기화 조건 - 날짜: $syncFromDate, 폴더: ${allowedFolders.ifEmpty { setOf("전체") }}")

            // 4) ✅ 날짜 + 폴더 필터 적용한 파일 조회
            val mediaFiles = getMediaFiles(syncFromDate, allowedFolders)

            if (mediaFiles.isEmpty()) {
                Log.d(TAG, "동기화할 파일 없음")
                return Result.success()
            }

            Log.d(TAG, "동기화 대상: ${mediaFiles.size}개")

            // 5) 전송 실행
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

            // 6) ✅ 알림 표시
            val notifyEnabled = inputData.getBoolean(KEY_NOTIFY_ON_SEND, true)
            NotificationHelper.showTransferComplete(
                context        = context,
                successCount   = successCount,
                duplicateCount = duplicateCount,
                failedCount    = failedCount,
                enabled        = notifyEnabled
            )

            Result.success(
                workDataOf(
                    "success_count"   to successCount,
                    "duplicate_count" to duplicateCount,
                    "failed_count"    to failedCount
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "자동 동기화 오류: ${e.message}")
            Result.retry()
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    private suspend fun findReceiverDevice(): String? {
        val savedIp = inputData.getString(KEY_RECEIVER_IP)
        if (!savedIp.isNullOrEmpty()) return savedIp
        return withContext(Dispatchers.IO) {
            WifiDeviceScanner(context).scanNetwork().firstOrNull()?.ipAddress
        }
    }

    // ✅ 날짜 + 폴더 필터링 통합 쿼리
    private suspend fun getMediaFiles(
        syncFromDate:   String,
        allowedFolders: Set<String>
    ): List<Pair<android.net.Uri, String>> = withContext(Dispatchers.IO) {

        val result = mutableListOf<Pair<android.net.Uri, String>>()

        // 날짜 조건 계산
        val cutoffMillis: Long? = when (syncFromDate) {
            "ALL"      -> null
            "RECENT_3" -> System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L)
            "RECENT_7" -> System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L)
            else       -> runCatching {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                sdf.parse(syncFromDate)?.time
            }.getOrNull()
        }
        // MediaStore DATE_MODIFIED는 초 단위
        val cutoffSec = cutoffMillis?.let { it / 1000 }

        // 날짜 조건 쿼리 문자열
        val dateSelection   = cutoffSec?.let { "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?" }
        val dateSelectionArgs = cutoffSec?.let { arrayOf(it.toString()) }

        // 사진 + 동영상 각각 쿼리
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { baseUri ->
            context.contentResolver.query(
                baseUri,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA          // 파일 경로 (폴더 필터용)
                ),
                dateSelection,
                dateSelectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

                while (cursor.moveToNext()) {
                    val id       = cursor.getLong(idCol)
                    val name     = cursor.getString(nameCol) ?: continue
                    val filePath = cursor.getString(dataCol) ?: continue
                    val folder   = java.io.File(filePath).parent ?: continue

                    // ✅ 폴더 필터: allowedFolders가 비어있으면 전체, 있으면 해당 폴더만
                    if (allowedFolders.isEmpty() || folder in allowedFolders) {
                        result.add(android.net.Uri.parse("$baseUri/$id") to name)
                    }
                }
            }
        }
        result
    }
}

// ── 스케줄러 헬퍼 ──────────────────────────────────────────────
object AutoSyncScheduler {

    private const val WORK_NAME = "auto_sync_work"

    /**
     * ✅ 시작 시각(hour, minute)을 반영한 정기 동기화 등록
     * WorkManager는 정확한 시각 보장이 안 되므로
     * initialDelay로 첫 실행을 다음 지정 시각에 맞춤
     */
    fun schedule(
        context:       Context,
        intervalHours: Int    = 24,
        startHour:     Int    = 8,
        startMinute:   Int    = 0,
        receiverIp:    String = "",
        syncFromDate:  String = "ALL",
        syncFolders:   String = "",     // "path1|path2|..." 활성 폴더만
        notifyOnSend:  Boolean = true,
        wifiOnly:      Boolean = true
    ) {
        val constraints = Constraints.Builder()
            .apply {
                if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED)
                setRequiresBatteryNotLow(true)
            }
            .build()

        val inputData = workDataOf(
            KEY_RECEIVER_IP    to receiverIp,
            KEY_SYNC_FROM_DATE to syncFromDate,
            KEY_SYNC_FOLDERS   to syncFolders,
            KEY_NOTIFY_ON_SEND to notifyOnSend,
            KEY_WIFI_ONLY      to wifiOnly
        )

        // ✅ 다음 지정 시각까지 대기 시간 계산
        val initialDelay = calcInitialDelay(startHour, startMinute)
        Log.d("AutoSyncScheduler", "다음 동기화까지 ${initialDelay / 60000}분 후 (${startHour}:${"%02d".format(startMinute)})")

        val workRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /** 다음 지정 시각(hour:minute)까지의 밀리초 계산 */
    private fun calcInitialDelay(hour: Int, minute: Int): Long {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        // 이미 지난 시각이면 다음 날로
        if (target.before(now)) target.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis - now.timeInMillis
    }

    fun runOnce(
        context:      Context,
        receiverIp:   String = "",
        syncFromDate: String = "ALL",
        syncFolders:  String = "",
        notifyOnSend: Boolean = true
    ) {
        val workRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setInputData(workDataOf(
                KEY_RECEIVER_IP    to receiverIp,
                KEY_SYNC_FROM_DATE to syncFromDate,
                KEY_SYNC_FOLDERS   to syncFolders,
                KEY_NOTIFY_ON_SEND to notifyOnSend
            ))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun getStatus(context: Context) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
}
