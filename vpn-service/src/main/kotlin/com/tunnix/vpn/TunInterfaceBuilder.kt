package com.tunnix.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.tunnix.core.models.ServerConfig

object TunInterfaceBuilder {

    const val VPN_MTU = 1500
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

        // 4. IPv6 Configuration (optional, safe fallback)
        try {
            builder.addAddress(VPN_IPV6_ADDRESS, VPN_IPV6_PREFIX)
            builder.addRoute("::", 0)
        } catch (_: Exception) {
            // Some devices don't have IPv6 TUN support enabled
        }

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

        return builder.establish()
    }
}
