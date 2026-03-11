package com.family.phototransfer.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecord)

    /** 최근 N일 기록 (since = 밀리초 타임스탬프) */
    @Query("SELECT * FROM transfer_records WHERE transferredAt >= :since ORDER BY transferredAt DESC")
    fun getRecentRecords(since: Long): Flow<List<TransferRecord>>

    /** 전체 기록 */
    @Query("SELECT * FROM transfer_records ORDER BY transferredAt DESC")
    fun getAllRecords(): Flow<List<TransferRecord>>

    /** 해시로 중복 확인 */
    @Query("SELECT COUNT(*) FROM transfer_records WHERE fileHash = :hash AND fileHash != ''")
    suspend fun countByHash(hash: String): Int

    /** 성공 건수 */
    @Query("SELECT COUNT(*) FROM transfer_records WHERE status = 'SUCCESS'")
    suspend fun getSuccessCount(): Int

    /** 전체 삭제 */
    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()
}
