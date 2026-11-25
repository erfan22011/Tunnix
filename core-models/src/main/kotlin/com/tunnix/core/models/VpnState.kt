package com.tunnix.core.models

sealed class VpnState {
    object Disconnected : VpnState()
    object Connecting : VpnState()
    data class Connected(val serverName: String, val connectedAtMillis: Long = System.currentTimeMillis()) : VpnState()
    object Reconnecting : VpnState()
    data class Error(val message: String, val cause: Throwable? = null) : VpnState()
}
