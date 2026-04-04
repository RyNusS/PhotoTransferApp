package com.family.phototransfer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.family.phototransfer.service.TransferForegroundService
import com.family.phototransfer.ui.settings.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

/**
 * 8번 개선사항 - 폰 재시작 시 앱(수신 서비스) 자동 실행
 *
 * 처리하는 인텐트:
 * - BOOT_COMPLETED        : 일반 부팅 완료
 * - QUICKBOOT_POWERON     : 삼성/화웨이 퀵 부트
 * - MY_PACKAGE_REPLACED   : 앱 업데이트 후 자동 재시작
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "수신된 인텐트: $action")

        if (action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON"
            )
        ) return

        // DataStore에서 자동 동기화 설정 읽기 후 서비스 시작
        // goAsync()로 비동기 처리 (BroadcastReceiver 제한 시간 내 완료)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = context.dataStore.data
                    .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                    .first()

                val autoSyncEnabled = prefs[booleanPreferencesKey("auto_sync_enabled")] ?: false
                Log.d(TAG, "자동 동기화 설정: $autoSyncEnabled")

                val autoStartOnBoot = prefs[booleanPreferencesKey("auto_start_on_boot")] ?: false
                Log.d(TAG, "부팅 시 수신 자동 시작 설정: $autoStartOnBoot")

                if (autoStartOnBoot) {
                    startReceiverService(context)
                    Log.d(TAG, "수신 서비스 자동 시작")
                } else {
                    Log.d(TAG, "자동 시작 비활성: 서비스 시작 안 함")
                }
            } catch (e: Exception) {
                Log.e(TAG, "부팅 처리 오류: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun startReceiverService(context: Context) {
        try {
            val serviceIntent = Intent(context, TransferForegroundService::class.java).apply {
                action = TransferForegroundService.ACTION_START_RECEIVER
            }
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "TransferForegroundService 시작 요청 완료")
        } catch (e: Exception) {
            Log.e(TAG, "서비스 시작 실패: ${e.message}")
        }
    }
}
