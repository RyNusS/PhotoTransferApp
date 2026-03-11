package com.family.phototransfer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 전송 기록 엔티티
@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val fileHash: String,       // SHA-256 해시 (중복 감지)
    val fileSize: Long,
    val sourceDevice: String,
    val status: String,         // SUCCESS / FAILED / SKIPPED_DUPLICATE
    val transferredAt: Long = System.currentTimeMillis()
)

// 전송 기록 DAO
@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY transferredAt DESC")
    fun getAllRecords(): Flow<List<TransferRecord>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: TransferRecord)

    // 중복 확인: 해시값으로 이미 전송된 파일인지 체크
    @Query("SELECT COUNT(*) FROM transfer_records WHERE fileHash = :hash AND status = 'SUCCESS'")
    suspend fun countByHash(hash: String): Int

    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transfer_records WHERE status = 'SUCCESS'")
    suspend fun getSuccessCount(): Int
}
