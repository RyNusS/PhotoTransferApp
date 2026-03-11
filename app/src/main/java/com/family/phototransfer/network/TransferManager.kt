package com.family.phototransfer.network

import android.content.Context
import android.net.Uri
import android.util.Log
import com.family.phototransfer.data.repository.TransferRepository
import com.family.phototransfer.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransferManager"

sealed class TransferEvent {
    data class Started(val fileName: String, val current: Int, val total: Int) : TransferEvent()
    data class Progress(val fileName: String, val progress: Float) : TransferEvent()
    data class Completed(val fileName: String, val fileSize: Long) : TransferEvent()
    data class Duplicate(val fileName: String) : TransferEvent()
    data class Failed(val fileName: String, val error: String) : TransferEvent()
    object AllDone : TransferEvent()
}

@Singleton
class TransferManager @Inject constructor(
    private val repository: TransferRepository
) {
    fun transferFiles(
        context: Context,
        files: List<Pair<Uri, String>>,
        receiverIp: String,
        sourceDeviceName: String = android.os.Build.MODEL
    ): Flow<TransferEvent> = flow {

        val client = TransferClient()

        files.forEachIndexed { index, (uri, fileName) ->

            emit(TransferEvent.Started(fileName, index + 1, files.size))

            try {
                // 1) 파일을 1회만 읽기
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }

                if (bytes == null) {
                    val msg = "파일을 열 수 없습니다: $fileName"
                    Log.e(TAG, msg)
                    emit(TransferEvent.Failed(fileName, msg))
                    repository.recordFailed(fileName, 0L, sourceDeviceName)
                    return@forEachIndexed
                }

                val fileSize = bytes.size.toLong()
                Log.d(TAG, "파일 읽기 완료: $fileName (${fileSize} bytes)")

                // 2) 해시 계산 (읽은 바이트로 바로 계산)
                val fileHash = HashUtil.sha256(bytes)

                // 3) 송신측 DB 중복 확인
                if (repository.isDuplicate(fileHash)) {
                    Log.d(TAG, "중복 파일 건너뜀: $fileName")
                    emit(TransferEvent.Duplicate(fileName))
                    repository.recordDuplicate(fileName, fileHash, fileSize, sourceDeviceName)
                    return@forEachIndexed
                }

                // 4) 소켓 전송 (읽어둔 바이트 그대로 전달 - 파일 재읽기 없음)
                val result = client.sendBytes(
                    fileBytes  = bytes,
                    fileHash   = fileHash,
                    fileName   = fileName,
                    receiverIp = receiverIp,
                    onProgress = { progress ->
                        // 진행률 이벤트 (필요시 사용)
                    }
                )

                // 5) 결과 처리
                when (result) {
                    is TransferResult.Success -> {
                        Log.d(TAG, "전송 성공: $fileName")
                        emit(TransferEvent.Completed(fileName, fileSize))
                        repository.recordSuccess(fileName, fileHash, fileSize, sourceDeviceName)
                    }
                    is TransferResult.Skipped -> {
                        Log.d(TAG, "수신측 중복: $fileName")
                        emit(TransferEvent.Duplicate(fileName))
                        repository.recordDuplicate(fileName, fileHash, fileSize, sourceDeviceName)
                    }
                    is TransferResult.Failed -> {
                        Log.e(TAG, "전송 실패: $fileName - ${result.error}")
                        emit(TransferEvent.Failed(fileName, result.error))
                        repository.recordFailed(fileName, fileSize, sourceDeviceName)
                    }
                }

            } catch (e: OutOfMemoryError) {
                // 대용량 파일 OOM 대응
                val msg = "파일이 너무 큽니다 (메모리 부족): $fileName"
                Log.e(TAG, msg)
                emit(TransferEvent.Failed(fileName, msg))
                repository.recordFailed(fileName, 0L, sourceDeviceName)
            } catch (e: Exception) {
                val msg = "${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "예외 발생 ($fileName): $msg")
                emit(TransferEvent.Failed(fileName, msg))
                repository.recordFailed(fileName, 0L, sourceDeviceName)
            }
        }

        emit(TransferEvent.AllDone)
        Log.d(TAG, "모든 파일 전송 완료")
    }
}
