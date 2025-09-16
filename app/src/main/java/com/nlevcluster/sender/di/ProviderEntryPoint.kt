package com.nlevcluster.sender.di

import com.google.gson.Gson
import com.nlevcluster.sender.report.alerts.AlertDao
import com.nlevcluster.sender.report.charge.db.ChargingReportDao
import com.nlevcluster.sender.report.trip.db.TripReportDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderEntryPoint {
    fun gson(): Gson
    fun tripDao(): TripReportDao
    fun chargingDao(): ChargingReportDao
    fun alertDao(): AlertDao
    //fun logHelper(): LogHelper
}
