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

    /**
     * InputStream을 스트리밍으로 전송
     * 해시 계산은 TransferManager에서 임시파일로 미리 처리함
     * → 이 함수는 순수하게 전송만 담당
     */
    suspend fun sendStream(
        inputStream:  InputStream,
        fileSize:     Long,
        fileName:     String,
        receiverIp:   String,
        sourceDevice: String = android.os.Build.MODEL,
        onProgress:   (Float) -> Unit = {}
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

                // 1) 메타데이터 전송
                output.writeUTF(fileName)
                output.writeLong(fileSize)
                output.writeUTF("")           // 해시 자리 (수신측에서 사용 안 함)
                output.writeUTF(sourceDevice)
                output.flush()

                // 2) 파일 스트리밍 전송
                val buffer    = ByteArray(CHUNK_SIZE)
                var totalSent = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalSent += bytesRead
                    if (fileSize > 0) onProgress(totalSent.toFloat() / fileSize)
                }
                output.flush()

                Log.d(TAG, "전송 완료: $fileName ($totalSent bytes)")

                // 3) 서버 응답 대기
                val response = input.readUTF()
                Log.d(TAG, "서버 응답: $response ($fileName)")

                when (response) {
                    "OK"   -> TransferResult.Success(fileName, totalSent)
                    "SKIP" -> TransferResult.Skipped(fileName, "수신 기기에 이미 존재")
                    else   -> TransferResult.Failed(fileName, "서버 오류: $response")
                }
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "연결 실패: ${e.message}")
            TransferResult.Failed(fileName, "수신 기기에 연결할 수 없습니다.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "타임아웃 ($fileName, video=$isVideo): ${e.message}")
            val hint = if (isVideo) "동영상이 너무 크거나 네트워크가 느립니다." else "네트워크를 확인하세요."
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
