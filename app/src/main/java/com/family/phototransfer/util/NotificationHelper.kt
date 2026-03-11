package com.family.phototransfer.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID   = "transfer_complete"
    private const val CHANNEL_NAME = "전송 완료 알림"
    private const val NOTIFY_SEND_ID    = 1001
    private const val NOTIFY_RECEIVE_ID = 1002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "사진 전송/수신 완료 시 알림" }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    /**
     * 송신 완료 알림
     * @param enabled 설정에서 알림 ON 여부 - false면 알림 미표시
     */
    fun showTransferComplete(
        context:       Context,
        successCount:  Int,
        duplicateCount: Int,
        failedCount:   Int,
        enabled:       Boolean = true   // ✅ 설정값 반영
    ) {
        if (!enabled) return

        val body = buildString {
            append("성공 ${successCount}개")
            if (duplicateCount > 0) append(" · 중복 ${duplicateCount}개")
            if (failedCount    > 0) append(" · 실패 ${failedCount}개")
        }
        notify(context, NOTIFY_SEND_ID, "전송 완료", body)
    }

    /**
     * 수신 완료 알림
     * @param enabled 설정에서 알림 ON 여부 - false면 알림 미표시
     */
    fun showReceiveComplete(
        context:  Context,
        fileName: String,
        enabled:  Boolean = true        // ✅ 설정값 반영
    ) {
        if (!enabled) return
        notify(context, NOTIFY_RECEIVE_ID, "사진 수신 완료", fileName)
    }

    private fun notify(context: Context, id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) { /* POST_NOTIFICATIONS 미허용 시 무시 */ }
    }
}
