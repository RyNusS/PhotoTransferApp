package com.family.phototransfer.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

private const val TAG = "TransferClient"

private const val CONNECT_TIMEOUT_MS        = 10_000
private const val VIDEO_TRANSFER_TIMEOUT_MS = 300_000  // 동영상: 5분
private const val IMAGE_TRANSFER_TIMEOUT_MS = 60_000   // 사진: 1분
private const val CHUNK_SIZE                = 65_536   // 64KB

sealed class TransferResult {
    data class Success(val fileName: String, val fileSize: Long) : TransferResult()
    data class Skipped(val fileName: String, val reason: String) : TransferResult()
    data class Failed(val fileName: String, val error: String) : TransferResult()
}

class TransferClient {

    suspend fun sendStream(
        inputStream:  InputStream,
        fileSize:     Long,
        fileName:     String,
        receiverIp:   String,
        sourceDevice: String = android.os.Build.MODEL,
        onProgress:   (Float) -> Unit = {}
    ): TransferResult = withContext(Dispatchers.IO) {

        val isVideo = isVideoFileName(fileName)

        // HTTP 모드: cloudflared URL (https:// 또는 http://)
        if (receiverIp.startsWith("http://") || receiverIp.startsWith("https://")) {
            return@withContext sendViaHttp(
                receiverIp.trimEnd('/'), inputStream, fileSize, fileName, sourceDevice, isVideo, onProgress
            )
        }

        // raw TCP 모드: 기존 로컬 WiFi 전송
        val transferTimeout = if (isVideo) VIDEO_TRANSFER_TIMEOUT_MS else IMAGE_TRANSFER_TIMEOUT_MS

        val (host, port) = if (receiverIp.contains(":")) {
            val parts = receiverIp.split(":")
            parts[0] to (parts[1].toIntOrNull() ?: TRANSFER_PORT)
        } else {
            receiverIp to TRANSFER_PORT
        }

        Log.d(TAG, "TCP 전송: $fileName ($fileSize bytes) → $host:$port")

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = transferTimeout

                val output = DataOutputStream(socket.getOutputStream().buffered(CHUNK_SIZE))
                val input  = DataInputStream(socket.getInputStream())

                output.writeUTF(fileName)
                output.writeLong(fileSize)
                output.writeUTF("")           // 해시 자리
                output.writeUTF(sourceDevice)
                output.flush()

                val buffer    = ByteArray(CHUNK_SIZE)
                var totalSent = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    if (fileSize > 0) onProgress(totalSent.toFloat() / fileSize)
                }
                output.flush()

                Log.d(TAG, "TCP 전송 완료: $fileName ($totalSent bytes)")

                val response = input.readUTF()
                Log.d(TAG, "서버 응답: $response ($fileName)")

                when (response) {
                    "OK"   -> TransferResult.Success(fileName, totalSent)
                    "SKIP" -> TransferResult.Skipped(fileName, "수신 기기에 이미 존재")
                    else   -> TransferResult.Failed(fileName, "서버 오류: $response")
                }
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "TCP 연결 실패: ${e.message}")
            TransferResult.Failed(fileName, "수신 기기에 연결할 수 없습니다.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "TCP 타임아웃 ($fileName, video=$isVideo): ${e.message}")
            val hint = if (isVideo) "동영상이 너무 크거나 네트워크가 느립니다." else "네트워크를 확인하세요."
            TransferResult.Failed(fileName, "전송 시간 초과. $hint")
        } catch (e: Exception) {
            Log.e(TAG, "TCP 전송 오류 ($fileName): ${e.javaClass.simpleName} - ${e.message}")
            TransferResult.Failed(fileName, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ── HTTP 전송 (cloudflared Quick Tunnel 경유) ─────────────────────
    private fun sendViaHttp(
        baseUrl:      String,
        inputStream:  InputStream,
        fileSize:     Long,
        fileName:     String,
        sourceDevice: String,
        isVideo:      Boolean,
        onProgress:   (Float) -> Unit
    ): TransferResult {
        val url = "$baseUrl/transfer"
        Log.d(TAG, "HTTP 전송: $fileName → $url")
        return try {
            val conn = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = if (isVideo) VIDEO_TRANSFER_TIMEOUT_MS else IMAGE_TRANSFER_TIMEOUT_MS
                setRequestProperty("X-Filename",   fileName)
                setRequestProperty("X-Filesize",   fileSize.toString())
                setRequestProperty("X-Device",     sourceDevice)
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(fileSize)
            }
            conn.connect()
            conn.outputStream.use { out ->
                val buffer    = ByteArray(CHUNK_SIZE)
                var totalSent = 0L
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    if (fileSize > 0) onProgress(totalSent.toFloat() / fileSize)
                }
                out.flush()
            }
            val responseCode = conn.responseCode
            val body = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            if (responseCode == 200 && body == "OK") TransferResult.Success(fileName, fileSize)
            else TransferResult.Failed(fileName, "HTTP 서버 오류: $responseCode $body")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "HTTP 연결 실패: ${e.message}")
            TransferResult.Failed(fileName, "수신 기기에 연결할 수 없습니다.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "HTTP 타임아웃: ${e.message}")
            TransferResult.Failed(fileName, "전송 시간 초과.")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP 전송 오류 ($fileName): ${e.javaClass.simpleName} - ${e.message}")
            TransferResult.Failed(fileName, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun isVideoFileName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") ||
               lower.endsWith(".mkv") || lower.endsWith(".3gp") || lower.endsWith(".m4v")
    }
}
