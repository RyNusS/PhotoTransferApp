package com.family.phototransfer.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID   = "transfer_complete"
    private const val CHANNEL_NAME = "전송 완료 알림"
    private const val NOTIFY_ID    = 1001

    // 앱 시작 시 or Application.onCreate에서 호출
    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "사진 전송 완료 시 알림"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * 전송 완료 알림 표시
     * @param successCount  성공 건수
     * @param duplicateCount 중복 건너뜀 건수
     * @param failedCount   실패 건수
     */
    fun showTransferComplete(
        context: Context,
        successCount: Int,
        duplicateCount: Int,
        failedCount: Int
    ) {
        val title = "전송 완료"
        val body  = buildString {
            append("성공 ${successCount}개")
            if (duplicateCount > 0) append(" · 중복 ${duplicateCount}개")
            if (failedCount    > 0) append(" · 실패 ${failedCount}개")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS 권한 미승인 시 무시
        }
    }

    /**
     * 수신 완료 알림 (픽셀1용)
     */
    fun showReceiveComplete(context: Context, fileName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("사진 수신 완료")
            .setContentText(fileName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID + 1, notification)
        } catch (e: SecurityException) {
            // 무시
        }
    }
}
