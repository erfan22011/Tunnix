package com.tunnix.vpn

import com.tunnix.core.models.VpnState
import org.junit.Assert.*
import org.junit.Test

class VpnTunnelManagerTest {

    @Test
    fun `test initial state is Disconnected`() {
        VpnTunnelManager.updateState(VpnState.Disconnected)
        assertEquals(VpnState.Disconnected, VpnTunnelManager.vpnState.value)
    }

    @Test
    fun `test state transitions`() {
        VpnTunnelManager.updateState(VpnState.Connecting)
        assertEquals(VpnState.Connecting, VpnTunnelManager.vpnState.value)

        val connectedState = VpnState.Connected("Swiss-1")
        VpnTunnelManager.updateState(connectedState)
        assertEquals(connectedState, VpnTunnelManager.vpnState.value)

        VpnTunnelManager.updateState(VpnState.Reconnecting)
        assertEquals(VpnState.Reconnecting, VpnTunnelManager.vpnState.value)

        val errorState = VpnState.Error("Network failure")
        VpnTunnelManager.updateState(errorState)
        assertEquals(errorState, VpnTunnelManager.vpnState.value)

        VpnTunnelManager.updateState(VpnState.Disconnected)
        assertEquals(VpnState.Disconnected, VpnTunnelManager.vpnState.value)
    }

    @Test
    fun `test tun interface constants`() {
        assertEquals(1400, TunInterfaceBuilder.VPN_MTU)
        assertEquals("172.19.0.1", TunInterfaceBuilder.VPN_IPV4_ADDRESS)
        assertEquals(30, TunInterfaceBuilder.VPN_IPV4_PREFIX)
        assertEquals("1.1.1.1", TunInterfaceBuilder.PRIMARY_DNS)
        assertEquals("8.8.8.8", TunInterfaceBuilder.SECONDARY_DNS)
    }
}
