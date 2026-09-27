package com.family.phototransfer.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

private const val TAG = "DeviceCare"
private const val PREFS_NAME = "device_care"

/**
 * 배터리 없이 상시 전원으로 운영되는 수신기(Pixel 1)의 순간 전력 피크를 줄이기 위한 설정.
 *
 * - 수신 속도를 MAX_BYTES_PER_SEC 이하로 제한 (TCP 흐름제어로 송신 측도 자동으로 느려짐)
 * - 파일 한 개 저장 후 PAUSE_AFTER_FILE_MS 만큼 쉬고 나서 OK 응답
 */
object ReceivePowerSaver {
    const val KEY_ENABLED = "low_power_receive"

    /** 저전력 수신 모드 사용 여부 (기본 켜짐) */
    @Volatile
    var enabled: Boolean = true

    /** 저전력 모드 수신 속도 상한: 초당 약 2MB */
    const val MAX_BYTES_PER_SEC: Long = 2_000_000L

    /** 파일 한 개 수신 후 쉬는 시간 */
    const val PAUSE_AFTER_FILE_MS: Long = 1_500L

    /** 수신 루프에서 청크마다 호출 — 목표 속도보다 빠르면 잠깐 쉰다 */
    fun throttle(startNanos: Long, totalBytes: Long) {
        if (!enabled) return
        val expectedMs = totalBytes * 1000L / MAX_BYTES_PER_SEC
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
        val waitMs = expectedMs - elapsedMs
        if (waitMs > 5) {
            try { Thread.sleep(waitMs) } catch (_: InterruptedException) {}
        }
    }

    /** 파일 한 개 저장 완료 후 호출 */
    fun pauseBetweenFiles() {
        if (!enabled) return
        try { Thread.sleep(PAUSE_AFTER_FILE_MS) } catch (_: InterruptedException) {}
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }
}

/**
 * 재부팅 기록.
 *
 * - 앱 프로세스가 뜰 때마다 "부팅 횟수"를 확인해 새 부팅이면 한 줄 기록
 * - 1분마다 "마지막 동작 시각"을 남겨, 꺼지기 직전 시각을 추정할 수 있게 함
 */
object BootLog {
    private const val KEY_LOG = "boot_log"
    private const val KEY_HEARTBEAT = "last_heartbeat"
    private const val KEY_LAST_BOOT_COUNT = "last_boot_count"
    private const val KEY_LAST_BOOT_TIME = "last_boot_time"
    private const val MAX_ENTRIES = 50

    /**
     * @param bootTime  부팅된 시각 (epoch ms)
     * @param lastAlive 직전 부팅에서 마지막으로 앱이 동작한 시각 (0 = 알 수 없음)
     * @param reason    시스템이 알려준 부팅 원인 (못 읽으면 "알 수 없음")
     */
    data class Entry(val bootTime: Long, val lastAlive: Long, val reason: String)

    fun heartbeat(context: Context) {
        prefs(context).edit().putLong(KEY_HEARTBEAT, System.currentTimeMillis()).apply()
    }

    /** 새 부팅이면 기록 추가. Application.onCreate 에서 호출 */
    fun checkNewBoot(context: Context) {
        try {
            val p = prefs(context)
            val bootCount = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            } catch (_: Exception) { -1 }
            val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()

            val isNewBoot = if (bootCount >= 0) {
                bootCount != p.getInt(KEY_LAST_BOOT_COUNT, -2)
            } else {
                val lastBootTime = p.getLong(KEY_LAST_BOOT_TIME, 0L)
                Math.abs(bootTime - lastBootTime) > 120_000L
            }
            if (!isNewBoot) return

            val lastAlive = p.getLong(KEY_HEARTBEAT, 0L)
            val reason = readBootReason()
            val line = listOf(bootTime.toString(), lastAlive.toString(), sanitize(reason)).joinToString("|")

            val lines = (p.getString(KEY_LOG, "") ?: "")
                .split('\n').filter { it.isNotBlank() }
                .toMutableList()
            lines.add(0, line)
            while (lines.size > MAX_ENTRIES) lines.removeAt(lines.lastIndex)

            p.edit()
                .putString(KEY_LOG, lines.joinToString("\n"))
                .putInt(KEY_LAST_BOOT_COUNT, bootCount)
                .putLong(KEY_LAST_BOOT_TIME, bootTime)
                .putLong(KEY_HEARTBEAT, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "재부팅 기록 추가: $line")
        } catch (e: Exception) {
            Log.e(TAG, "재부팅 기록 실패: ${e.message}")
        }
    }

    /** 최신순 기록 목록 */
    fun entries(context: Context): List<Entry> =
        (prefs(context).getString(KEY_LOG, "") ?: "")
            .split('\n')
            .mapNotNull { line ->
                val parts = line.split('|', limit = 3)
                if (parts.size < 3) return@mapNotNull null
                val boot = parts[0].toLongOrNull() ?: return@mapNotNull null
                Entry(boot, parts[1].toLongOrNull() ?: 0L, parts[2])
            }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_LOG).apply()
    }

    private fun readBootReason(): String {
        for (prop in listOf("sys.boot.reason", "ro.boot.bootreason", "persist.sys.boot.reason")) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("getprop", prop))
                val value = proc.inputStream.bufferedReader().use { it.readText() }.trim()
                proc.waitFor()
                if (value.isNotEmpty()) return value
            } catch (_: Exception) {}
        }
        return "알 수 없음"
    }

    private fun sanitize(s: String) = s.replace('|', '/').replace('\n', ' ').take(80)
}

/** Application.onCreate 에서 한 번 호출 */
object DeviceCare {
    @Volatile
    private var heartbeatStarted = false

    fun init(context: Context) {
        val app = context.applicationContext
        ReceivePowerSaver.enabled = prefs(app).getBoolean(ReceivePowerSaver.KEY_ENABLED, true)
        BootLog.checkNewBoot(app)
        startHeartbeat(app)
    }

    private fun startHeartbeat(app: Context) {
        if (heartbeatStarted) return
        heartbeatStarted = true
        Thread {
            while (true) {
                BootLog.heartbeat(app)
                try { Thread.sleep(60_000L) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "DeviceCareHeartbeat" }.start()
    }
}

private fun prefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
