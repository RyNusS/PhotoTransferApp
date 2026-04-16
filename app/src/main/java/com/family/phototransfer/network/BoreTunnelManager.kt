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
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BoreTunnelManager"

/**
 * 잘 안 쓰는 10개 후보 포트 — 수신/송신 양쪽에 하드코딩하여
 * 외부 서비스 없이 "어느 포트에 bore가 살아있는가"를 자동 탐지한다.
 */
val BORE_CANDIDATE_PORTS = listOf(
    57392, 58471, 59283, 61047, 62815,
    53847, 64291, 55638, 60174, 63529
)

sealed class BoreTunnelState {
    object Idle       : BoreTunnelState()
    object Connecting : BoreTunnelState()
    data class Connected(val port: Int) : BoreTunnelState()
    data class Error(val message: String) : BoreTunnelState()
}

@Singleton
class BoreTunnelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _state = MutableStateFlow<BoreTunnelState>(BoreTunnelState.Idle)
    val state: StateFlow<BoreTunnelState> = _state.asStateFlow()

    private var boreProcess: Process? = null

    // ── 시작 ──────────────────────────────────────────────────────────
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value is BoreTunnelState.Connected) return@withContext true

        // APK에 번들된 bore 바이너리 (nativeLibraryDir → 실행 가능)
        val boreBin = getBoreBinary()
        if (boreBin == null) {
            _state.value = BoreTunnelState.Error("bore 바이너리를 찾을 수 없습니다 (libbore.so)")
            return@withContext false
        }

        _state.value = BoreTunnelState.Connecting

        // 후보 포트 순서대로 시도
        for (port in BORE_CANDIDATE_PORTS) {
            Log.d(TAG, "포트 $port 시도 중...")
            val process = tryStartBore(boreBin, port)
            if (process != null) {
                boreProcess = process
                _state.value = BoreTunnelState.Connected(port)
                Log.d(TAG, "bore 터널 시작: bore.pub:$port")
                monitorProcess(process)
                return@withContext true
            }
        }

        _state.value = BoreTunnelState.Error("후보 포트 10개 모두 사용 불가")
        return@withContext false
    }

    // ── 중지 ──────────────────────────────────────────────────────────
    fun stop() {
        boreProcess?.destroy()
        boreProcess = null
        _state.value = BoreTunnelState.Idle
        Log.d(TAG, "bore 터널 중지")
    }

    // ── APK 번들 바이너리 경로 반환 ───────────────────────────────────
    private fun getBoreBinary(): File? {
        // extractNativeLibs=true 로 설치 시 nativeLibraryDir에 추출됨
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val boreBin = File(nativeDir, "libbore.so")
        Log.d(TAG, "bore 경로: ${boreBin.absolutePath}, exists=${boreBin.exists()}, canExec=${boreBin.canExecute()}")

        if (!boreBin.exists()) {
            Log.e(TAG, "libbore.so 없음 — APK에 번들되지 않은 것 같습니다")
            return null
        }
        if (!boreBin.canExecute()) {
            boreBin.setExecutable(true, false)
        }
        return boreBin
    }

    // ── bore 프로세스 단일 포트 시도 ──────────────────────────────────
    private fun tryStartBore(boreBin: File, port: Int): Process? {
        return try {
            val process = ProcessBuilder(
                boreBin.absolutePath, "local", "9876",
                "--to", "bore.pub",
                "--port", port.toString()
            )
                .redirectErrorStream(true)
                .start()

            // 최대 6초간 출력을 읽어 성공/실패 판단
            val reader   = process.inputStream.bufferedReader()
            val deadline = System.currentTimeMillis() + 6_000L

            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "bore[$port] $line")

                    // 성공: "Listening at bore.pub:PORT" 포함 시
                    if (line.contains("bore.pub:$port", ignoreCase = true) ||
                        line.contains("Listening at", ignoreCase = true)) {
                        Thread { reader.forEachLine { Log.d(TAG, "bore: $it") } }.start()
                        return process
                    }

                    // 명확한 실패 키워드
                    if (line.contains("error",   ignoreCase = true) ||
                        line.contains("failed",  ignoreCase = true) ||
                        line.contains("refused", ignoreCase = true) ||
                        line.contains("already", ignoreCase = true)) {
                        process.destroy()
                        return null
                    }
                } else {
                    Thread.sleep(100)
                }

                // 프로세스가 이미 종료됐으면 실패
                try {
                    val exit = process.exitValue()
                    // 종료 후 남은 출력 전체 드레인 → bore의 실제 에러 메시지 확인
                    try {
                        val remaining = reader.readText()
                        if (remaining.isNotBlank()) {
                            Log.d(TAG, "bore[$port] 종료 시 출력: '${remaining.trim()}'")
                        } else {
                            Log.d(TAG, "bore[$port] 종료 시 출력 없음")
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "bore[$port] 출력 드레인 실패: ${e.message}")
                    }
                    Log.d(TAG, "bore[$port] 조기 종료: exitCode=$exit")
                    return null
                } catch (ignored: IllegalThreadStateException) {
                    // 아직 실행 중 → 계속 대기
                }
            }

            // 타임아웃 — 프로세스가 살아있으면 성공으로 간주
            return try {
                process.exitValue()
                null // 종료됐으면 실패
            } catch (ignored: IllegalThreadStateException) {
                Thread { reader.forEachLine { Log.d(TAG, "bore: $it") } }.start()
                process
            }
        } catch (e: Exception) {
            Log.e(TAG, "bore 실행 오류[$port]: ${e.message}")
            null
        }
    }

    // ── 프로세스 사망 감지 ─────────────────────────────────────────────
    private fun monitorProcess(process: Process) {
        Thread {
            val exitCode = process.waitFor()
            Log.d(TAG, "bore 프로세스 종료: exitCode=$exitCode")
            if (_state.value is BoreTunnelState.Connected) {
                _state.value = BoreTunnelState.Error("bore 연결 끊김 (exitCode=$exitCode)")
            }
        }.start()
    }

    // ── 특정 bore.pub 포트가 살아있는지 TCP 연결로 확인 ─────────────────
    fun probePort(port: Int, timeoutMs: Int = 2_000): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("bore.pub", port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
