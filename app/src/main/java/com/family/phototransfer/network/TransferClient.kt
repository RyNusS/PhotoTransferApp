package com.family.phototransfer.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "TransferClient"

// 동영상 등 대용량 파일 전송을 위해 타임아웃을 넉넉하게 설정
private const val CONNECT_TIMEOUT_MS = 10_000          // 접속 타임아웃: 10초
private const val VIDEO_TRANSFER_TIMEOUT_MS = 300_000  // 동영상 전송 타임아웃: 5분
private const val IMAGE_TRANSFER_TIMEOUT_MS = 60_000   // 사진 전송 타임아웃: 1분
private const val CHUNK_SIZE = 65_536                  // 64KB 청크 (동영상에 최적화)

sealed class TransferResult {
    data class Success(val fileName: String, val fileSize: Long) : TransferResult()
    data class Skipped(val fileName: String, val reason: String) : TransferResult()
    data class Failed(val fileName: String, val error: String) : TransferResult()
}

class TransferClient {

    /**
     * 파일을 스트리밍 방식으로 전송 (메모리에 전체를 올리지 않음)
     * → 동영상 등 대용량 파일도 OOM 없이 안정적으로 전송 가능
     */
    suspend fun sendStream(
        inputStream: InputStream,
        fileSize:    Long,
        fileHash:    String,
        fileName:    String,
        receiverIp:  String,
        onProgress:  (Float) -> Unit = {}
    ): TransferResult = withContext(Dispatchers.IO) {

        val isVideo = isVideoFileName(fileName)
        val transferTimeout = if (isVideo) VIDEO_TRANSFER_TIMEOUT_MS else IMAGE_TRANSFER_TIMEOUT_MS

        Log.d(TAG, "전송 시작: $fileName ($fileSize bytes, video=$isVideo) → $receiverIp:$TRANSFER_PORT")

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(receiverIp, TRANSFER_PORT), CONNECT_TIMEOUT_MS)
                socket.soTimeout = transferTimeout

                val output = DataOutputStream(socket.getOutputStream().buffered(CHUNK_SIZE))
                val input  = DataInputStream(socket.getInputStream())

                // 메타데이터 전송
                output.writeUTF(fileName)
                output.writeLong(fileSize)
                output.writeUTF(fileHash)
                output.flush()

                // 스트리밍 청크 전송 (메모리에 전체를 올리지 않음)
                val buffer = ByteArray(CHUNK_SIZE)
                var totalSent = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    if (fileSize > 0) onProgress(totalSent.toFloat() / fileSize)
                }
                output.flush()

                Log.d(TAG, "스트림 전송 완료: $fileName ($totalSent bytes 전송)")

                // 서버 응답 대기
                val response = input.readUTF()
                Log.d(TAG, "서버 응답: $response ($fileName)")

                when (response) {
                    "OK"   -> TransferResult.Success(fileName, totalSent)
                    "SKIP" -> TransferResult.Skipped(fileName, "수신 기기에 이미 존재")
                    else   -> TransferResult.Failed(fileName, "서버 오류: $response")
                }
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "연결 실패 ($receiverIp:$TRANSFER_PORT): ${e.message}")
            TransferResult.Failed(fileName, "수신 기기에 연결할 수 없습니다. 수신 앱이 실행 중인지 확인하세요.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "타임아웃 ($fileName, video=$isVideo): ${e.message}")
            val hint = if (isVideo) "동영상 파일이 너무 크거나 네트워크가 느립니다." else "네트워크 상태를 확인하세요."
            TransferResult.Failed(fileName, "전송 시간 초과. $hint")
        } catch (e: Exception) {
            Log.e(TAG, "전송 오류 ($fileName): ${e.javaClass.simpleName} - ${e.message}")
            TransferResult.Failed(fileName, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun isVideoFileName(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mov") ||
               lower.endsWith(".avi") || lower.endsWith(".mkv") ||
               lower.endsWith(".3gp") || lower.endsWith(".wmv") ||
               lower.endsWith(".m4v") || lower.endsWith(".ts")
    }
}
