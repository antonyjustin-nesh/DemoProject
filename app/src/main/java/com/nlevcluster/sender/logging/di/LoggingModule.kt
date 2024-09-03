package com.nlevcluster.sender.logging.di

import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.nllogger.NLLoggerLogHelper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface LoggingModule {

    @Binds
    @Singleton
    fun bindLogging(roomLogHelper: NLLoggerLogHelper): LogHelper
}