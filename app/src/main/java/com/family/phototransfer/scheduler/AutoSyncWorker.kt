package com.family.phototransfer.scheduler

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.family.phototransfer.network.TransferEvent
import com.family.phototransfer.network.TransferManager
import com.family.phototransfer.network.WifiDeviceScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "AutoSyncWorker"

// WorkManager Input Data 키
const val KEY_RECEIVER_IP     = "receiver_ip"
const val KEY_SYNC_DAYS_BACK  = "sync_days_back"   // 며칠 전 사진까지 동기화할지
const val KEY_WIFI_ONLY       = "wifi_only"

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

            // 2) 수신 기기(픽셀1) 탐색
            val receiverIp = findReceiverDevice()
            if (receiverIp == null) {
                Log.d(TAG, "수신 기기 없음 - 동기화 건너뜀")
                return Result.retry()
            }

            // 3) 동기화할 사진 목록 조회
            val daysBack = inputData.getInt(KEY_SYNC_DAYS_BACK, 30)
            val mediaFiles = getRecentMediaFiles(daysBack)

            if (mediaFiles.isEmpty()) {
                Log.d(TAG, "동기화할 파일 없음")
                return Result.success()
            }

            Log.d(TAG, "동기화 대상 파일: ${mediaFiles.size}개")

            // 4) 전송 실행
            val events = transferManager.transferFiles(
                context        = context,
                files          = mediaFiles,
                receiverIp     = receiverIp,
                sourceDeviceName = android.os.Build.MODEL
            ).toList()

            val successCount   = events.count { it is TransferEvent.Completed }
            val duplicateCount = events.count { it is TransferEvent.Duplicate }
            val failedCount    = events.count { it is TransferEvent.Failed }

            Log.d(TAG, "동기화 완료 - 성공: $successCount, 중복: $duplicateCount, 실패: $failedCount")

            // 실패가 있어도 일부 성공이면 success (다음 주기에 재시도)
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

    // WiFi 연결 여부 확인
    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    // 수신 기기 탐색 (저장된 IP 우선, 없으면 스캔)
    private suspend fun findReceiverDevice(): String? {
        // 설정에 저장된 IP가 있으면 먼저 시도
        val savedIp = inputData.getString(KEY_RECEIVER_IP)
        if (!savedIp.isNullOrEmpty()) {
            Log.d(TAG, "저장된 IP 사용: $savedIp")
            return savedIp
        }

        // 없으면 네트워크 스캔
        return withContext(Dispatchers.IO) {
            val scanner = WifiDeviceScanner(context)
            val devices = scanner.scanNetwork()
            devices.firstOrNull()?.ipAddress
        }
    }

    // 최근 N일 이내 미디어 파일 조회
    private suspend fun getRecentMediaFiles(daysBack: Int): List<Pair<android.net.Uri, String>> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<Pair<android.net.Uri, String>>()
            val cutoffTime = System.currentTimeMillis() / 1000 - (daysBack * 24 * 60 * 60L)

            // 사진
            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                imageUri,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                "${MediaStore.Images.Media.DATE_MODIFIED} >= ?",
                arrayOf(cutoffTime.toString()),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id   = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    result.add(android.net.Uri.parse("$imageUri/$id") to name)
                }
            }

            // 동영상
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            context.contentResolver.query(
                videoUri,
                arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME),
                "${MediaStore.Video.Media.DATE_MODIFIED} >= ?",
                arrayOf(cutoffTime.toString()),
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id   = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol)
                    result.add(android.net.Uri.parse("$videoUri/$id") to name)
                }
            }

            result
        }
    }
}

// ── 스케줄러 설정 헬퍼 ─────────────────────────────────────────
object AutoSyncScheduler {

    private const val WORK_NAME = "auto_sync_work"

    /**
     * 정기 자동 동기화 등록
     * @param intervalHours 동기화 주기 (시간 단위, 최소 15분)
     * @param receiverIp    픽셀1 IP (비어있으면 매번 자동 탐색)
     * @param daysBack      며칠 전 사진까지 동기화
     */
    fun schedule(
        context: Context,
        intervalHours: Int = 24,
        receiverIp: String = "",
        daysBack: Int = 30,
        wifiOnly: Boolean = true
    ) {
        val constraints = Constraints.Builder()
            .apply {
                if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED) // WiFi 전용
                setRequiresBatteryNotLow(true)  // 배터리 부족 시 중지
            }
            .build()

        val inputData = workDataOf(
            KEY_RECEIVER_IP    to receiverIp,
            KEY_SYNC_DAYS_BACK to daysBack,
            KEY_WIFI_ONLY      to wifiOnly
        )

        val workRequest = PeriodicWorkRequestBuilder<AutoSyncWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,  // 설정 변경 시 기존 작업 업데이트
            workRequest
        )
    }

    /**
     * 즉시 1회 동기화 실행 (테스트 또는 수동 트리거용)
     */
    fun runOnce(
        context: Context,
        receiverIp: String = "",
        daysBack: Int = 30
    ) {
        val inputData = workDataOf(
            KEY_RECEIVER_IP    to receiverIp,
            KEY_SYNC_DAYS_BACK to daysBack
        )

        val workRequest = OneTimeWorkRequestBuilder<AutoSyncWorker>()
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    /**
     * 자동 동기화 취소
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * 현재 스케줄 상태 확인
     */
    fun getStatus(context: Context) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(WORK_NAME)
}
