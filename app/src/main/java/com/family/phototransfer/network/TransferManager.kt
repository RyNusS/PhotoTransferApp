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
import java.io.File
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

        val client  = TransferClient()
        val cacheDir = context.cacheDir

        files.forEachIndexed { index, (uri, fileName) ->

            emit(TransferEvent.Started(fileName, index + 1, files.size))

            var tmpFile: File? = null
            try {
                // ── 1) content:// URI → 캐시 임시파일로 복사 ────────────
                // 이유: content:// URI는 openInputStream을 두 번 열면
                //       두 번째(특히 동영상)가 빈 스트림이나 오류를 반환하는 경우 있음
                // 임시파일 사용으로 해시계산/중복확인/전송을 각각 안전하게 처리
                tmpFile = withContext(Dispatchers.IO) {
                    val tmp = File(cacheDir, "xfer_${System.currentTimeMillis()}_$fileName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.exists() && tmp.length() > 0) tmp else null
                }

                if (tmpFile == null || !tmpFile.exists()) {
                    val msg = "파일을 읽을 수 없습니다: $fileName"
                    Log.e(TAG, msg)
                    emit(TransferEvent.Failed(fileName, msg))
                    repository.recordFailed(fileName, 0L, sourceDeviceName)
                    return@forEachIndexed
                }

                val fileSize = tmpFile.length()
                Log.d(TAG, "임시파일 준비: $fileName ($fileSize bytes)")

                // ── 2) 해시 계산 (임시파일로 안전하게) ──────────────────
                val fileHash = withContext(Dispatchers.IO) {
                    tmpFile.inputStream().use { HashUtil.sha256(it) }
                }

                // ── 3) 송신측 DB 중복 확인 ───────────────────────────────
                if (repository.isDuplicate(fileHash)) {
                    Log.d(TAG, "중복 파일 건너뜀: $fileName")
                    emit(TransferEvent.Duplicate(fileName))
                    repository.recordDuplicate(fileName, fileHash, fileSize, sourceDeviceName)
                    return@forEachIndexed
                }

                // ── 4) 임시파일로 전송 ───────────────────────────────────
                val result = withContext(Dispatchers.IO) {
                    tmpFile.inputStream().use { stream ->
                        client.sendStream(
                            inputStream  = stream,
                            fileSize     = fileSize,
                            fileName     = fileName,
                            receiverIp   = receiverIp,
                            sourceDevice = sourceDeviceName
                        )
                    }
                }

                // ── 5) 결과 처리 ─────────────────────────────────────────
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
            } finally {
                // ── 6) 임시파일 반드시 삭제 ─────────────────────────────
                tmpFile?.delete()
            }
        }

        emit(TransferEvent.AllDone)
        Log.d(TAG, "모든 파일 전송 완료")
    }
}
