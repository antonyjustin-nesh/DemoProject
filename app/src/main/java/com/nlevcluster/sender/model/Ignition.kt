package com.nlevcluster.sender.model

enum class Ignition(val value: String) {
    PARK("00"),
    REVERSE("01"),
    NEUTRAL("10"),
    DRIVE("11")
}

//fun Int.asIgnition(): Ignition =
//    Ignition.values().first { it.value == this }

fun String?.asIgnition(): Ignition = when (this) {
    null -> Ignition.PARK
    else -> {
        Ignition.values().firstOrNull { it.value == this } ?: Ignition.PARK
    }
}