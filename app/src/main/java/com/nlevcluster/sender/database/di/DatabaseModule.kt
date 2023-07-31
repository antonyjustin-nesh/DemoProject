package com.nlevcluster.sender.database.di

import android.content.Context
import androidx.room.Room
import com.nlevcluster.sender.database.EvDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesEvDatabase(
        @ApplicationContext context: Context,
    ): EvDatabase = Room.databaseBuilder(
        context,
        EvDatabase::class.java,
        "ev-cluster-db",
    ).build()
}