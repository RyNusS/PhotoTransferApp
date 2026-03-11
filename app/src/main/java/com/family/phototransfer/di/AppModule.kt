package com.family.phototransfer.di

import android.content.Context
import androidx.room.Room
import com.family.phototransfer.data.db.AppDatabase
import com.family.phototransfer.data.db.MIGRATION_1_2
import com.family.phototransfer.data.db.TransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "photo_transfer_db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideTransferDao(db: AppDatabase): TransferDao = db.transferDao()
}
