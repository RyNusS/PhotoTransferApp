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
                // 1) 파일 크기 조회 (스트림을 열기 전에 미리)
                val fileSize = withContext(Dispatchers.IO) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use {
                        it.statSize
                    } ?: 0L
                }

                Log.d(TAG, "파일 크기 확인: $fileName ($fileSize bytes)")

                // 2) 해시 계산 - 1차 스트림 (중복 확인용)
                val fileHash = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        HashUtil.sha256(stream)
                    }
                }

                if (fileHash == null) {
                    val msg = "파일을 열 수 없습니다: $fileName"
                    Log.e(TAG, msg)
                    emit(TransferEvent.Failed(fileName, msg))
                    repository.recordFailed(fileName, 0L, sourceDeviceName)
                    return@forEachIndexed
                }

                // 3) 송신측 DB 중복 확인
                if (repository.isDuplicate(fileHash)) {
                    Log.d(TAG, "중복 파일 건너뜀: $fileName")
                    emit(TransferEvent.Duplicate(fileName))
                    repository.recordDuplicate(fileName, fileHash, fileSize, sourceDeviceName)
                    return@forEachIndexed
                }

                // 4) 소켓 전송 - 2차 스트림 (스트리밍 전송, OOM 없음)
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        client.sendStream(
                            inputStream  = stream,
                            fileSize     = fileSize,
                            fileHash     = fileHash,
                            fileName     = fileName,
                            receiverIp   = receiverIp,
                            sourceDevice = sourceDeviceName,  // ✅ 기기명 전달
                            onProgress   = { progress ->
                                // 진행률은 TransferEvent.Progress로 emit 불가(flow 외부)
                                // UploadViewModel에서 uploadProgress 별도 관리
                            }
                        )
                    } ?: TransferResult.Failed(fileName, "파일 스트림을 열 수 없습니다")
                }

                // 5) 결과 처리
                when (result) {
                    is TransferResult.Success -> {
                        Log.d(TAG, "전송 성공: $fileName")
                        emit(TransferEvent.Completed(fileName, result.fileSize))
                        repository.recordSuccess(fileName, fileHash, result.fileSize, sourceDeviceName)
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
