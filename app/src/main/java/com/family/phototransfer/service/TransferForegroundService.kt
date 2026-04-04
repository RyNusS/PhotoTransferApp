package com.family.phototransfer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.family.phototransfer.MainActivity
import com.family.phototransfer.R
import com.family.phototransfer.network.ReceiverStateHolder
import com.family.phototransfer.network.TransferServer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "TransferForegroundService"

@AndroidEntryPoint
class TransferForegroundService : Service() {

    @Inject lateinit var receiverStateHolder: ReceiverStateHolder

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var transferServer: TransferServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "서비스 생성됨")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        // 포그라운드 서비스 알림 즉시 표시 (ANR 방지)
        startForeground(NOTIFICATION_ID, buildNotification("수신 대기 중..."))

        when (intent?.action) {
            ACTION_START_RECEIVER -> startReceiver()
            ACTION_STOP_RECEIVER  -> stopReceiver()
            else                  -> startReceiver()  // 기본 동작: 수신 시작
        }

        // START_STICKY: 시스템이 서비스를 종료해도 자동 재시작
        return START_STICKY
    }

    private fun startReceiver() {
        if (transferServer != null) {
            Log.d(TAG, "이미 수신 중")
            return
        }

        serviceScope.launch {
            try {
                val saveDir = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                if (!saveDir.exists()) saveDir.mkdirs()

                transferServer = TransferServer(
                    saveDirectory  = saveDir,
                    context        = applicationContext,
                    onFileReceived = { received ->
                        Log.d(TAG, "파일 수신 완료: ${received.fileName}")
                        updateNotification("수신 완료: ${received.fileName}")
                    },
                    onError = { errorMsg ->
                        Log.e(TAG, "수신 오류: $errorMsg")
                        updateNotification("오류: $errorMsg")
                    }
                )

                // ✅ 서버 시작 직전에만 setReceiving(true) → Settings 탐색과 무관
                receiverStateHolder.setReceiving(true)
                updateNotification("수신 대기 중... (포트 9876)")
                Log.d(TAG, "TransferServer 시작")
                transferServer?.start()  // 블로킹 - 소켓 루프

            } catch (e: Exception) {
                Log.e(TAG, "수신 서버 오류: ${e.message}")
                updateNotification("서버 오류: ${e.message}")
                receiverStateHolder.setReceiving(false)
                transferServer = null
            }
        }
    }

    private fun stopReceiver() {
        serviceScope.launch {
            transferServer?.stop()
            transferServer = null
            receiverStateHolder.setReceiving(false)
            Log.d(TAG, "수신 서버 중지")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        transferServer?.stop()
        receiverStateHolder.setReceiving(false)
        Log.d(TAG, "서비스 종료됨")
    }

    // ── 알림 헬퍼 ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "수신 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "사진 수신 대기 서비스"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Photo Transfer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START_RECEIVER = "com.family.phototransfer.START_RECEIVER"
        const val ACTION_STOP_RECEIVER  = "com.family.phototransfer.STOP_RECEIVER"

        private const val CHANNEL_ID      = "transfer_service_channel"
        private const val NOTIFICATION_ID = 1001

        /** 외부에서 수신 시작 시 호출 */
        fun startReceiver(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START_RECEIVER
            }
            context.startForegroundService(intent)
        }

        /** 외부에서 수신 중지 시 호출 */
        fun stopReceiver(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_STOP_RECEIVER
            }
            context.startService(intent)
        }
    }
}

