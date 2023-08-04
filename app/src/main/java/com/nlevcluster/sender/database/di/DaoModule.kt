package com.nlevcluster.sender.database.di

import com.nlevcluster.sender.database.EvDatabase
import com.nlevcluster.sender.database.dao.CanParseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.nlevcluster.sender.database.dao.LogDao

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun providesLogDao(
        database: EvDatabase,
    ): LogDao = database.logDao()

    @Provides
    fun providesCanParseDao(
        database: EvDatabase
    ): CanParseDao = database.canParseDao()

}