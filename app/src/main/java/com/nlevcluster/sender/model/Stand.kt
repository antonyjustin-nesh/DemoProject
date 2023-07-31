package com.nlevcluster.sender.model

enum class Stand(val value: Int) {
    OFF(0),
    ON(1),
}

fun Int.asStand(): Stand =
    Stand.values().first { it.value == this }