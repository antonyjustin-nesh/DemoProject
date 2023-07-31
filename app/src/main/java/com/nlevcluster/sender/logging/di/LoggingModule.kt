package com.nlevcluster.sender.logging.di

import com.nlevcluster.sender.logging.LogHelper
import com.nlevcluster.sender.logging.RoomLogHelper
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
    fun bindLogging(roomLogHelper: RoomLogHelper): LogHelper
}