package com.nlevcluster.sender.model

/**
 * various state of the USB
 */
sealed interface UsbState {
    object Unknown : UsbState
    object Attached : UsbState
    object Detached : UsbState
    data class Error(val message: String) : UsbState
}