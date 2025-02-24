package com.nlevcluster.sender.model

data class VIP(
    val customerCode: String,
    val version: String,
    val customerName: String,
    val vehicle: Vehicle,
    val appConfig: AppConfig,
)

data class Vehicle(
    val modelNumber: String,
    val modelName: String,
    val vehicleType: String,
    val specifications: Specifications,
    val ecuArchitecture: String,
    val diagnostics: String,
    val dbc: String,
    val resources: String,
    val calculations: String,
)

data class Specifications(
    val tyreDiameter: Double
)

data class AppConfig(val feed: Feed)

data class Feed(
    val mqttUrl: String,
    val ignOnDelay: Int,
    val ignOffDelay: Int,
)