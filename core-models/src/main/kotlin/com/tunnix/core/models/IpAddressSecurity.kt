package com.tunnix.core.models

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Utility for detecting loopback, private, carrier-grade NAT, link-local,
 * and internal/local addresses without performing network DNS lookups.
 */
object IpAddressSecurity {

    private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /**
     * Returns true if the address represents a loopback, private, link-local,
     * carrier-grade NAT, or internal local domain/IP literal.
     * Never performs network DNS queries.
     */
    fun isPrivateOrLoopback(address: String): Boolean {
        if (address.isBlank()) return true
        val clean = address.trim().lowercase()
            .removePrefix("[").removeSuffix("]")

        // 1. Check local/internal hostnames without DNS
        if (clean == "localhost" || clean == "0.0.0.0" || clean == "::" ||
            clean.endsWith(".local") || clean.endsWith(".internal") || clean.endsWith(".lan")
        ) {
            return true
        }

        // 2. Check IPv4 literal
        val v4Match = IPV4_REGEX.matchEntire(clean)
        if (v4Match != null) {
            val (o1, o2, o3, o4) = v4Match.destructured
            val octet1 = o1.toIntOrNull() ?: return true
            val octet2 = o2.toIntOrNull() ?: return true
            val octet3 = o3.toIntOrNull() ?: return true
            val octet4 = o4.toIntOrNull() ?: return true

            if (octet1 !in 0..255 || octet2 !in 0..255 || octet3 !in 0..255 || octet4 !in 0..255) {
                return true
            }

            // 0.0.0.0/8
            if (octet1 == 0) return true
            // 127.0.0.0/8 (Loopback)
            if (octet1 == 127) return true
            // 10.0.0.0/8 (RFC 1918 Private)
            if (octet1 == 10) return true
            // 172.16.0.0/12 (RFC 1918 Private: 172.16.0.0 - 172.31.255.255)
            if (octet1 == 172 && octet2 in 16..31) return true
            // 192.168.0.0/16 (RFC 1918 Private)
            if (octet1 == 192 && octet2 == 168) return true
            // 169.254.0.0/16 (Link-Local)
            if (octet1 == 169 && octet2 == 254) return true
            // 100.64.0.0/10 (Carrier Grade NAT: 100.64.0.0 - 100.127.255.255)
            if (octet1 == 100 && octet2 in 64..127) return true

            return false
        }

        // 3. Check IPv6 literal
        if (clean.contains(":")) {
            return try {
                // When given an IPv6 literal (without hostnames), InetAddress.getByName does not do DNS lookup.
                val inet = InetAddress.getByName(clean)
                if (inet.isLoopbackAddress || inet.isSiteLocalAddress || inet.isLinkLocalAddress || inet.isAnyLocalAddress) {
                    return true
                }
                if (inet is Inet6Address) {
                    val bytes = inet.address
                    // fc00::/7 (Unique Local Address / ULA: first byte 0xfc or 0xfd)
                    val firstByte = bytes[0].toInt() and 0xFF
                    if ((firstByte and 0xFE) == 0xFC) {
                        return true
                    }
                    // ::ffff:a.b.c.d (IPv4-mapped IPv6)
                    val isIpv4Mapped = (0..9).all { bytes[it] == 0.toByte() } &&
                            bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
                    if (isIpv4Mapped) {
                        val mappedV4 = "${bytes[12].toInt() and 0xFF}.${bytes[13].toInt() and 0xFF}.${bytes[14].toInt() and 0xFF}.${bytes[15].toInt() and 0xFF}"
                        return isPrivateOrLoopback(mappedV4)
                    }
                }
                false
            } catch (_: Exception) {
                // Unparseable IP literal format -> treat as unsafe if looks like an IP
                false
            }
        }

        // Public domains are allowed
        return false
    }
}
