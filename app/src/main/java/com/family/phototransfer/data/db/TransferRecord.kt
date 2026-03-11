package com.family.phototransfer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true)
    val id:            Long   = 0,
    val fileName:      String,
    val fileHash:      String,
    val fileSize:      Long,
    val sourceDevice:  String,
    val status:        String,              // SUCCESS / FAILED / SKIPPED_DUPLICATE / RECEIVED
    val direction:     String = "SEND",     // SEND / RECEIVE  ← DB version 2에서 추가
    val transferredAt: Long   = System.currentTimeMillis()
)
