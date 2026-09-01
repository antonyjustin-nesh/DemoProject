package com.nlevcluster.sender.model

enum class Ignition(val value: String) {
    PARK("00"),
    REVERSE("01"),
    READY("10"),
    FORWARD("11")
}
