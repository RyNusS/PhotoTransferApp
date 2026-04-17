package com.family.phototransfer.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CloudflaredManager"

sealed class CloudflaredState {
    object Idle       : CloudflaredState()
    object Connecting : CloudflaredState()
    data class Connected(val url: String) : CloudflaredState()
    data class Error(val message: String) : CloudflaredState()
}

@Singleton
class CloudflaredManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _state = MutableStateFlow<CloudflaredState>(CloudflaredState.Idle)
    val state: StateFlow<CloudflaredState> = _state.asStateFlow()

    private var cfProcess: Process? = null

    // ── 시작 ──────────────────────────────────────────────────────────
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value is CloudflaredState.Connected) return@withContext true

        val bin = getCloudflaredBinary()
        if (bin == null) {
            _state.value = CloudflaredState.Error("cloudflared 바이너리를 찾을 수 없습니다 (libcloudflared.so)")
            return@withContext false
        }

        _state.value = CloudflaredState.Connecting

        try {
            val proc = ProcessBuilder(
                bin.absolutePath,
                "tunnel",
                "--url", "http://localhost:$HTTP_TRANSFER_PORT",
                "--no-autoupdate"
            )
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"]    = context.filesDir.absolutePath
                    environment()["GODEBUG"] = "netdns=cgo"  // Android DNS 사용
                }
                .start()

            cfProcess = proc

            val reader   = proc.inputStream.bufferedReader()
            val deadline = System.currentTimeMillis() + 30_000L   // 30초 대기

            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "cloudflared: $line")

                    // trycloudflare.com URL 파싱
                    val match = Regex("https://[a-z0-9-]+\\.trycloudflare\\.com").find(line)
                    if (match != null) {
                        val url = match.value
                        _state.value = CloudflaredState.Connected(url)
                        Log.d(TAG, "터널 URL 획득: $url")
                        // 백그라운드 로그 소비
                        Thread { reader.forEachLine { Log.d(TAG, "cloudflared: $it") } }.start()
                        monitorProcess(proc)
                        return@withContext true
                    }

                    // 오류 키워드 감지 (로그만 남기고 계속 시도)
                    if (line.contains("error", ignoreCase = true) &&
                        line.contains("failed", ignoreCase = true)) {
                        Log.w(TAG, "cloudflared 오류 로그: $line")
                    }
                } else {
                    Thread.sleep(100)
                }

                // 프로세스 조기 종료 감지
                try {
                    val exit = proc.exitValue()
                    val remaining = try { reader.readText() } catch (e: Exception) { "" }
                    Log.e(TAG, "cloudflared 조기 종료 exitCode=$exit: ${remaining.trim()}")
                    _state.value = CloudflaredState.Error("cloudflared 종료 (exitCode=$exit): ${remaining.trim().take(80)}")
                    return@withContext false
                } catch (ignored: IllegalThreadStateException) {
                    // 아직 실행 중 → 계속 대기
                }
            }

            // 30초 타임아웃
            try {
                proc.exitValue()
                _state.value = CloudflaredState.Error("URL 생성 전 프로세스 종료")
            } catch (ignored: IllegalThreadStateException) {
                _state.value = CloudflaredState.Error("터널 URL 생성 타임아웃 (30초)")
                proc.destroy()
            }
            false

        } catch (e: Exception) {
            Log.e(TAG, "cloudflared 실행 오류: ${e.message}")
            _state.value = CloudflaredState.Error("실행 오류: ${e.message}")
            false
        }
    }

    // ── 중지 ──────────────────────────────────────────────────────────
    fun stop() {
        cfProcess?.destroy()
        cfProcess = null
        _state.value = CloudflaredState.Idle
        Log.d(TAG, "cloudflared 터널 중지")
    }

    // ── APK 번들 바이너리 경로 반환 ───────────────────────────────────
    private fun getCloudflaredBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, "libcloudflared.so")
        Log.d(TAG, "cloudflared 경로: ${bin.absolutePath}, exists=${bin.exists()}, canExec=${bin.canExecute()}")
        if (!bin.exists()) {
            Log.e(TAG, "libcloudflared.so 없음 — APK에 번들되지 않은 것 같습니다")
            return null
        }
        if (!bin.canExecute()) {
            bin.setExecutable(true, false)
        }
        return bin
    }

    // ── 프로세스 사망 감지 ─────────────────────────────────────────────
    private fun monitorProcess(process: Process) {
        Thread {
            val exit = process.waitFor()
            Log.d(TAG, "cloudflared 프로세스 종료: exitCode=$exit")
            if (_state.value is CloudflaredState.Connected) {
                _state.value = CloudflaredState.Error("터널 연결 끊김 (exitCode=$exit)")
            }
        }.start()
    }
}
