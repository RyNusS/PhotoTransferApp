package com.family.phototransfer.data.repository

import com.family.phototransfer.data.db.TransferDao
import com.family.phototransfer.data.db.TransferRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val transferDao: TransferDao
) {
    /**
     * 이미 전송된 파일인지 SHA-256 해시로 확인
     * @return true = 중복 (이미 전송됨), false = 새 파일
     */
    suspend fun isDuplicate(fileHash: String): Boolean {
        return transferDao.countByHash(fileHash) > 0
    }

    /**
     * 전송 성공 기록 저장
     */
    suspend fun recordSuccess(
        fileName: String,
        fileHash: String,
        fileSize: Long,
        sourceDevice: String
    ) {
        transferDao.insert(
            TransferRecord(
                fileName     = fileName,
                fileHash     = fileHash,
                fileSize     = fileSize,
                sourceDevice = sourceDevice,
                status       = "SUCCESS"
            )
        )
    }

    /**
     * 중복으로 건너뜀 기록 저장
     */
    suspend fun recordDuplicate(
        fileName: String,
        fileHash: String,
        fileSize: Long,
        sourceDevice: String
    ) {
        transferDao.insert(
            TransferRecord(
                fileName     = fileName,
                fileHash     = fileHash,
                fileSize     = fileSize,
                sourceDevice = sourceDevice,
                status       = "SKIPPED_DUPLICATE"
            )
        )
    }

    /**
     * 전송 실패 기록 저장
     */
    suspend fun recordFailed(
        fileName: String,
        fileSize: Long,
        sourceDevice: String
    ) {
        transferDao.insert(
            TransferRecord(
                fileName     = fileName,
                fileHash     = "",
                fileSize     = fileSize,
                sourceDevice = sourceDevice,
                status       = "FAILED"
            )
        )
    }

    /**
     * 전체 전송 기록 Flow로 반환 (History 화면에서 사용)
     */
    fun getAllRecords() = transferDao.getAllRecords()

    /**
     * 성공한 전송 총 개수
     */
    suspend fun getSuccessCount() = transferDao.getSuccessCount()
}
