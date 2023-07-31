package com.nlevcluster.sender.model

/**
 * mode supported by ev vehicle
 */
enum class EvMode(
    val mode: String,
    val colorCode: String
) {
    ECO("00", "#2BC143"),
    SPORTS("01", "#FF7A00"),
    POWER("10", "#BE2C0C")
}

fun String?.asEvMode(): EvMode = when (this) {
    null -> EvMode.ECO
    else -> {
        EvMode.values().firstOrNull { it.mode == this } ?: EvMode.ECO
    }
}

fun String?.evMode(): EvMode = when (this) {
    null -> EvMode.ECO
    else -> {
        EvMode.values().firstOrNull { it.name == this } ?: EvMode.ECO
    }
}