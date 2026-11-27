package com.tunnix.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.tunnix.core.models.ServerConfig

object TunInterfaceBuilder {

    const val VPN_MTU = 1400
    const val VPN_IPV4_ADDRESS = "172.19.0.1"
    const val VPN_IPV4_PREFIX = 30
    const val VPN_IPV6_ADDRESS = "fdfe:dcba:9876::1"
    const val VPN_IPV6_PREFIX = 126

    const val PRIMARY_DNS = "1.1.1.1"
    const val SECONDARY_DNS = "8.8.8.8"

    /**
     * Builds and establishes the TUN interface for the VPN.
     * Prevents routing loops by excluding the app's own package.
     */
    fun establishTun(
        vpnService: VpnService,
        server: ServerConfig
    ): ParcelFileDescriptor? {
        val builder = vpnService.Builder()

        // 1. Session Name
        builder.setSession("Tunnix - ${server.name}")

        // 2. MTU
        builder.setMtu(VPN_MTU)

        // 3. IPv4 Configuration
        builder.addAddress(VPN_IPV4_ADDRESS, VPN_IPV4_PREFIX)
        builder.addRoute("0.0.0.0", 0)

        // 4. IPv6 Configuration (safe fallback with leak prevention attempt)
        var ipv6Success = false
        try {
            builder.addAddress(VPN_IPV6_ADDRESS, VPN_IPV6_PREFIX)
            builder.addRoute("::", 0)
            ipv6Success = true
        } catch (e: Exception) {
            android.util.Log.w(
                "TunInterfaceBuilder",
                "IPv6 TUN configuration failed on this device - IPv6 traffic may not be captured by TUN / هشدار: راه‌اندازی اینترفیس IPv6 ناموفق بود",
                e
            )
            // Attempt to capture default route ::/0 anyway to prevent IPv6 traffic leakage
            try {
                builder.addRoute("::", 0)
            } catch (_: Exception) {}
        }
        VpnTunnelManager.setIpv6Supported(ipv6Success)

        // 5. Secure DNS
        builder.addDnsServer(PRIMARY_DNS)
        builder.addDnsServer(SECONDARY_DNS)

        // 6. Anti-Loopback: Exclude our own app from being captured by the TUN
        try {
            builder.addDisallowedApplication(vpnService.packageName)
        } catch (_: Exception) {
            // Ignored on legacy devices
        }

        // 7. Blocking mode (Required by Go/Xray-core tun2socks loop)
        builder.setBlocking(true)

        return try {
            builder.establish()
        } catch (_: Exception) {
            null
        }
    }
}
