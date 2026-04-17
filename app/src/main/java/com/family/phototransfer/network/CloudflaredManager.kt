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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
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
    private var dnsProxySocket: DatagramSocket? = null

    // ── DNS 프록시: [::1]:53 -> 8.8.8.8:53 ───────────────────────
    // Android에서 Go static 바이너리는 /etc/resolv.conf의 nameserver ::1을 사용하는데
    // [::1]:53 에 아무것도 없어서 DNS 실패. 여기서 직접 [::1]:53을 열어 포워딩.
    private fun startDnsProxy() {
        Thread {
            try {
                val bindAddr = InetSocketAddress(InetAddress.getByName("::1"), 53)
                val sock = DatagramSocket(bindAddr)
                dnsProxySocket = sock
                Log.d(TAG, "DNS 프록시 시작: [::1]:53 -> 8.8.8.8:53")
                val buf = ByteArray(512)
                while (!Thread.currentThread().isInterrupted) {
                    val pkt = DatagramPacket(buf.clone(), 512)
                    try {
                        sock.receive(pkt)
                    } catch (e: Exception) {
                        break
                    }
                    val captured = pkt
                    Thread {
                        try {
                            val up = DatagramSocket()
                            up.setSoTimeout(5000)
                            val q = captured.data.copyOf(captured.length)
                            up.send(DatagramPacket(q, q.size, InetAddress.getByName("8.8.8.8"), 53))
                            val r = ByteArray(512)
                            val rp = DatagramPacket(r, r.size)
                            up.receive(rp)
                            up.close()
                            val resp = rp.data.copyOf(rp.length)
                            sock.send(DatagramPacket(resp, resp.size, captured.socketAddress))
                        } catch (e: Exception) {
                            Log.w(TAG, "DNS forward error: ${e.message}")
                        }
                    }.apply {
                        isDaemon = true
                        start()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "DNS proxy failed (port 53 bind): ${e.message}")
            }
        }.apply {
            isDaemon = true
            name = "cloudflared-dns-proxy"
            start()
        }
    }

    private fun stopDnsProxy() {
        dnsProxySocket?.close()
        dnsProxySocket = null
    }

    // ── 시작 ──────────────────────────────────────────────────────────
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (_state.value is CloudflaredState.Connected) return@withContext true

        val bin = getCloudflaredBinary()
        if (bin == null) {
            _state.value = CloudflaredState.Error("cloudflared 바이너리를 찾을 수 없습니다 (libcloudflared.so)")
            return@withContext false
        }

        // DNS 프록시 먼저 시작 (Go 바이너리가 [::1]:53 사용)
        startDnsProxy()
        Thread.sleep(200)

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
                    environment()["HOME"] = context.filesDir.absolutePath
                }
                .start()

            cfProcess = proc

            val reader   = proc.inputStream.bufferedReader()
            val deadline = System.currentTimeMillis() + 30_000L

            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG, "cloudflared: $line")

                    val match = Regex("https://[a-z0-9-]+\\.trycloudflare\\.com").find(line)
                    if (match != null) {
                        val url = match.value
                        _state.value = CloudflaredState.Connected(url)
                        Log.d(TAG, "터널 URL 획득: $url")
                        Thread { reader.forEachLine { Log.d(TAG, "cloudflared: $it") } }.start()
                        monitorProcess(proc)
                        return@withContext true
                    }

                    if (line.contains("error", ignoreCase = true) &&
                        line.contains("failed", ignoreCase = true)) {
                        Log.w(TAG, "cloudflared 오류 로그: $line")
                    }
                } else {
                    Thread.sleep(100)
                }

                try {
                    val exit = proc.exitValue()
                    val remaining = try { reader.readText() } catch (e: Exception) { "" }
                    Log.e(TAG, "cloudflared 조기 종료 exitCode=$exit: ${remaining.trim()}")
                    _state.value = CloudflaredState.Error("cloudflared 종료 (exitCode=$exit): ${remaining.trim().take(80)}")
                    return@withContext false
                } catch (ignored: IllegalThreadStateException) { }
            }

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
        stopDnsProxy()
        _state.value = CloudflaredState.Idle
        Log.d(TAG, "cloudflared 터널 중지")
    }

    // ── APK 번들 바이너리 경로 반환 ───────────────────────────────────
    private fun getCloudflaredBinary(): File? {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val bin = File(nativeDir, "libcloudflared.so")
        Log.d(TAG, "cloudflared 경로: ${bin.absolutePath}, exists=${bin.exists()}, canExec=${bin.canExecute()}")
        if (!bin.exists()) {
            Log.e(TAG, "libcloudflared.so 없음 -- APK에 번들되지 않은 것 같습니다")
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
