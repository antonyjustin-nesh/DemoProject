package com.nlevcluster.sender.model

data class OBD2(
    var abs: Int = 0,
    var tripMeter: Int = 0,
    var odometer: Int = 0,
    var engineOilTemp: Int = 0,
    var fuel: Int = 0,
    var lowOilIndicator: Int = 0,
    var tractionControlStatus: Int = 0,
    var engineCheckStatus: Int = 0,
    var batteryCheckStatus: Int = 0
)
