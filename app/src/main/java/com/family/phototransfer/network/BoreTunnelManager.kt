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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BoreTunnelManager"

// bore v0.6.0 aarch64-unknown-linux-musl 정적 바이너리 (Android ARM64 호환)
private const val BORE_DOWNLOAD_URL =
    "https://github.com/ekzhang/bore/releases/download/v0.6.0/bore-v0.6.0-aarch64-unknown-linux-musl.tar.gz"
private const val BORE_VERSION = "0.6.0"

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
    object Downloading: BoreTunnelState()
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

        // 1. bore 바이너리 확인 / 다운로드
        val boreBin = ensureBoreInstalled()
        if (boreBin == null) {
            _state.value = BoreTunnelState.Error("bore 바이너리 설치 실패")
            return@withContext false
        }

        _state.value = BoreTunnelState.Connecting

        // 2. 후보 포트 순서대로 시도
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
            val reader  = process.inputStream.bufferedReader()
            val deadline = System.currentTimeMillis() + 6_000L

            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "bore[$port] $line")

                    // 성공: "Listening at bore.pub:PORT" 포함 시
                    if (line.contains("bore.pub:$port", ignoreCase = true) ||
                        line.contains("Listening at", ignoreCase = true)) {
                        // 백그라운드에서 나머지 로그 소비 (버퍼 블로킹 방지)
                        Thread { reader.forEachLine { Log.d(TAG, "bore: $it") } }.start()
                        return process
                    }

                    // 명확한 실패 키워드
                    if (line.contains("error",   ignoreCase = true) ||
                        line.contains("failed",  ignoreCase = true) ||
                        line.contains("refused",  ignoreCase = true) ||
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

    // ── bore 바이너리 확보 (다운로드 + tar.gz 추출) ────────────────────
    private suspend fun ensureBoreInstalled(): File? = withContext(Dispatchers.IO) {
        val boreFile    = File(context.filesDir, "bore")
        val versionFile = File(context.filesDir, "bore_version")

        if (boreFile.exists() && boreFile.canExecute() &&
            versionFile.exists() && versionFile.readText().trim() == BORE_VERSION) {
            Log.d(TAG, "bore 이미 설치됨 (v$BORE_VERSION)")
            return@withContext boreFile
        }

        _state.value = BoreTunnelState.Downloading
        Log.d(TAG, "bore v$BORE_VERSION 다운로드 중...")

        return@withContext try {
            val tarGzFile = File(context.cacheDir, "bore.tar.gz")

            val conn = URL(BORE_DOWNLOAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout    = 120_000
            conn.connect()

            conn.inputStream.use { input ->
                FileOutputStream(tarGzFile).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            Log.d(TAG, "다운로드 완료: ${tarGzFile.length()} bytes")

            val ok = extractBoreFromTarGz(tarGzFile, boreFile)
            tarGzFile.delete()

            if (!ok) {
                Log.e(TAG, "tar.gz 추출 실패")
                return@withContext null
            }

            boreFile.setExecutable(true, false)
            versionFile.writeText(BORE_VERSION)
            Log.d(TAG, "bore 설치 완료: ${boreFile.absolutePath}")
            boreFile
        } catch (e: Exception) {
            Log.e(TAG, "bore 설치 실패: ${e.message}")
            null
        }
    }

    // ── tar.gz에서 'bore' 바이너리 추출 ───────────────────────────────
    private fun extractBoreFromTarGz(tarGzFile: File, outputFile: File): Boolean {
        return try {
            GZIPInputStream(tarGzFile.inputStream().buffered()).use { gzip ->
                val header = ByteArray(512)

                while (true) {
                    val n = gzip.read(header, 0, 512)
                    if (n < 512) break
                    if (header.all { it == 0.toByte() }) break  // end-of-archive

                    val name     = String(header, 0, 100).trimEnd('\u0000')
                    val sizeStr  = String(header, 124, 12).trim().trimEnd('\u0000')
                    val size     = if (sizeStr.isEmpty()) 0L else sizeStr.toLong(8) // octal
                    val typeFlag = header[156]

                    val isRegular = typeFlag == '0'.code.toByte() || typeFlag == 0.toByte()

                    if (isRegular && size > 0) {
                        val baseName = name.substringAfterLast('/')
                        if (baseName == "bore") {
                            FileOutputStream(outputFile).use { out ->
                                val buf = ByteArray(8192)
                                var remaining = size
                                while (remaining > 0) {
                                    val toRead = minOf(buf.size.toLong(), remaining).toInt()
                                    val read   = gzip.read(buf, 0, toRead)
                                    if (read <= 0) break
                                    out.write(buf, 0, read)
                                    remaining -= read
                                }
                            }
                            val padding = ((size + 511) / 512 * 512 - size).toInt()
                            if (padding > 0) gzip.skip(padding.toLong())
                            Log.d(TAG, "추출 완료: $name (${outputFile.length()} bytes)")
                            return true
                        } else {
                            // 다른 파일 → 512-byte 블록 단위로 스킵
                            gzip.skip((size + 511) / 512 * 512)
                        }
                    }
                    // 디렉토리·심볼릭링크 등 → 데이터 없음, 헤더만 소비됨
                }
                Log.e(TAG, "tar.gz에서 'bore' 파일을 찾지 못함")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "tar.gz 추출 오류: ${e.message}")
            false
        }
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
