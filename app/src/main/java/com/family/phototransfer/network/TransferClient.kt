package com.family.phototransfer.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "TransferClient"

sealed class TransferResult {
    data class Success(val fileName: String, val fileSize: Long) : TransferResult()
    data class Skipped(val fileName: String, val reason: String) : TransferResult()
    data class Failed(val fileName: String, val error: String) : TransferResult()
}

class TransferClient {

    /**
     * 이미 읽어둔 바이트 배열과 해시를 받아서 전송
     * → TransferManager에서 해시 계산 후 바로 넘기므로 파일을 두 번 읽지 않음
     */
    suspend fun sendBytes(
        fileBytes:  ByteArray,
        fileHash:   String,
        fileName:   String,
        receiverIp: String,
        onProgress: (Float) -> Unit = {}
    ): TransferResult = withContext(Dispatchers.IO) {

        val fileSize = fileBytes.size.toLong()
        Log.d(TAG, "전송 시작: $fileName ($fileSize bytes) → $receiverIp:$TRANSFER_PORT")

        try {
            Socket().use { socket ->
                socket.soTimeout = TRANSFER_TIMEOUT_MS
                socket.connect(
                    InetSocketAddress(receiverIp, TRANSFER_PORT),
                    TRANSFER_TIMEOUT_MS
                )

                val output = DataOutputStream(socket.getOutputStream().buffered())
                val input  = DataInputStream(socket.getInputStream())

                // 메타데이터 전송
                output.writeUTF(fileName)
                output.writeLong(fileSize)
                output.writeUTF(fileHash)
                output.flush()

                // 파일 데이터 청크 전송
                val chunkSize = 8192
                var sent = 0
                while (sent < fileBytes.size) {
                    val end = minOf(sent + chunkSize, fileBytes.size)
                    output.write(fileBytes, sent, end - sent)
                    sent = end
                    onProgress(sent.toFloat() / fileSize)
                }
                output.flush()

                // 서버 응답 대기
                val response = input.readUTF()
                Log.d(TAG, "서버 응답: $response ($fileName)")

                when (response) {
                    "OK"   -> TransferResult.Success(fileName, fileSize)
                    "SKIP" -> TransferResult.Skipped(fileName, "수신 기기에 이미 존재")
                    else   -> TransferResult.Failed(fileName, "서버 오류: $response")
                }
            }
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "연결 실패 ($receiverIp:$TRANSFER_PORT): ${e.message}")
            TransferResult.Failed(fileName, "수신 기기에 연결할 수 없습니다. 수신 앱이 실행 중인지 확인하세요.")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "타임아웃: ${e.message}")
            TransferResult.Failed(fileName, "전송 시간 초과. 네트워크 상태를 확인하세요.")
        } catch (e: Exception) {
            Log.e(TAG, "전송 오류 ($fileName): ${e.javaClass.simpleName} - ${e.message}")
            TransferResult.Failed(fileName, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
