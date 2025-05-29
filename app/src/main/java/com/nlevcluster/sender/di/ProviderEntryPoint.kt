package com.nlevcluster.sender.di

import com.google.gson.Gson
import com.nlevcluster.sender.nllogger.LogHelper
import com.nlevcluster.sender.trip.db.TripReportDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderEntryPoint {
    fun gson(): Gson
    fun tripDao(): TripReportDao
    //fun logHelper(): LogHelper
}
