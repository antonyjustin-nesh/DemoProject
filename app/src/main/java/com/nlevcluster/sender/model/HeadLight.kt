package com.nlevcluster.sender.model

import kotlinx.serialization.Serializable

/**
 * Head light data class for showing light
 *
 * @property status
 * @property isHighBeam
 */
@Serializable

data class HeadLight(
    val status: Boolean = false,
    val isHighBeam: Boolean = false
)