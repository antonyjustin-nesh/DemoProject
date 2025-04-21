package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

@Serializable
data class Indicator(
    val left: Boolean = false,
    val right: Boolean = false,
)
