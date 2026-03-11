package com.family.phototransfer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities  = [TransferRecord::class],
    version   = 2,          // direction 컬럼 추가로 버전 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
}

/** version 1 → 2: direction 컬럼 추가 (기존 데이터는 SEND로 기본값 설정) */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE transfer_records ADD COLUMN direction TEXT NOT NULL DEFAULT 'SEND'"
        )
    }
}
