package com.nlevcluster.sender.model

enum class IgnitionStatus(val status: String) {
    ON("ON"),
    OFF("OFF")
}

fun String?.asIgnitionStatus(): IgnitionStatus = when (this) {
    null -> IgnitionStatus.OFF
    else -> {
        IgnitionStatus.values().firstOrNull() {
            it.status == this
        } ?: IgnitionStatus.OFF
    }
}
fun IgnitionStatus.toTelematicsValue(): Int {
    return when (this) {
        IgnitionStatus.ON -> 1
        else -> 0
    }
}