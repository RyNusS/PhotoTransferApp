package com.family.phototransfer.data.repository

import com.family.phototransfer.data.db.TransferDao
import com.family.phototransfer.data.db.TransferRecord
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val transferDao: TransferDao
) {
    suspend fun isDuplicate(fileHash: String): Boolean =
        transferDao.countByHash(fileHash) > 0

    suspend fun recordSuccess(fileName: String, fileHash: String, fileSize: Long, sourceDevice: String) {
        transferDao.insert(TransferRecord(fileName=fileName, fileHash=fileHash, fileSize=fileSize, sourceDevice=sourceDevice, status="SUCCESS", direction="SEND"))
    }

    suspend fun recordDuplicate(fileName: String, fileHash: String, fileSize: Long, sourceDevice: String) {
        transferDao.insert(TransferRecord(fileName=fileName, fileHash=fileHash, fileSize=fileSize, sourceDevice=sourceDevice, status="SKIPPED_DUPLICATE", direction="SEND"))
    }

    suspend fun recordFailed(fileName: String, fileSize: Long, sourceDevice: String) {
        transferDao.insert(TransferRecord(fileName=fileName, fileHash="", fileSize=fileSize, sourceDevice=sourceDevice, status="FAILED", direction="SEND"))
    }

    suspend fun recordReceived(fileName: String, fileSize: Long, fromDevice: String) {
        transferDao.insert(TransferRecord(fileName=fileName, fileHash="", fileSize=fileSize, sourceDevice=fromDevice, status="RECEIVED", direction="RECEIVE"))
    }

    fun getRecentRecords() = transferDao.getRecentRecords(
        since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    )

    fun getAllRecords() = transferDao.getAllRecords()

    suspend fun getSuccessCount() = transferDao.getSuccessCount()
}
