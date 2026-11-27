package com.tunnix.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object VpnTunnelManager {

    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _isIpv6Supported = MutableStateFlow<Boolean?>(null)
    val isIpv6Supported: StateFlow<Boolean?> = _isIpv6Supported.asStateFlow()

    fun setIpv6Supported(supported: Boolean) {
        _isIpv6Supported.value = supported
    }

    private val isConnectingOrConnected = AtomicBoolean(false)
    private var activeServer: ServerConfig? = null

    const val ACTION_START = "com.tunnix.vpn.ACTION_START"
    const val ACTION_STOP = "com.tunnix.vpn.ACTION_STOP"
    const val EXTRA_SERVER = "extra_server"

    fun updateState(newState: VpnState) {
        _vpnState.value = newState
        when (newState) {
            is VpnState.Connected -> isConnectingOrConnected.set(true)
            is VpnState.Connecting -> isConnectingOrConnected.set(true)
            is VpnState.Reconnecting -> isConnectingOrConnected.set(true)
            is VpnState.Disconnected, is VpnState.Error -> {
                isConnectingOrConnected.set(false)
                _isIpv6Supported.value = null
            }
        }
    }

    fun startVpn(context: Context, server: ServerConfig) {
        if (isConnectingOrConnected.get() && activeServer?.id == server.id) {
            return // Already connecting or connected to this server
        }

        activeServer = server
        updateState(VpnState.Connecting)

        val intent = Intent(context, TunnixVpnService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_SERVER, server)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            updateState(VpnState.Error("عدم امکان شروع سرویس VPN: ${e.message}", e))
        }
    }

    fun stopVpn(context: Context) {
        val intent = Intent(context, TunnixVpnService::class.java).apply {
            action = ACTION_STOP
        }
        context.startService(intent)
        updateState(VpnState.Disconnected)
        activeServer = null
    }

    fun reconnect(context: Context) {
        val server = activeServer
        if (server != null) {
            startVpn(context, server)
        }
    }

    fun getActiveServer(): ServerConfig? = activeServer
}
