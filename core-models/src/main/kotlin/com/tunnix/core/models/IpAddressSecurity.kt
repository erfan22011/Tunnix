package com.tunnix.core.models

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Utility for detecting loopback, private, carrier-grade NAT, link-local,
 * multicast, reserved, and internal/local addresses without performing network DNS lookups.
 */
object IpAddressSecurity {

    /**
     * Returns true if the address represents a loopback, private, link-local,
     * carrier-grade NAT, multicast, reserved, or internal local domain/IP literal.
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

        // 2. Check IPv6 literal (contains ':')
        if (clean.contains(":")) {
            val noZone = if (clean.contains("%")) clean.substringBefore("%") else clean
            return try {
                val inet = InetAddress.getByName(noZone)
                if (inet.isLoopbackAddress || inet.isSiteLocalAddress || inet.isLinkLocalAddress ||
                    inet.isAnyLocalAddress || inet.isMulticastAddress
                ) {
                    return true
                }
                if (inet is Inet6Address) {
                    val bytes = inet.address
                    val firstByte = bytes[0].toInt() and 0xFF

                    // fc00::/7 (Unique Local Address / ULA: first byte 0xfc or 0xfd)
                    if ((firstByte and 0xFE) == 0xFC) {
                        return true
                    }
                    // fe80::/10 (Link-Local unicast)
                    if (firstByte == 0xFE && (bytes[1].toInt() and 0xC0) == 0x80) {
                        return true
                    }
                    // ff00::/8 (Multicast)
                    if (firstByte == 0xFF) {
                        return true
                    }
                    // 64:ff9b::/96 (NAT64 Well-Known Prefix)
                    val isNat64 = bytes[0] == 0x00.toByte() && bytes[1] == 0x64.toByte() &&
                            bytes[2] == 0xFF.toByte() && bytes[3] == 0x9B.toByte() &&
                            (4..11).all { bytes[it] == 0.toByte() }
                    if (isNat64) {
                        val nat64V4 = ((bytes[12].toLong() and 0xFF) shl 24) or
                                ((bytes[13].toLong() and 0xFF) shl 16) or
                                ((bytes[14].toLong() and 0xFF) shl 8) or
                                (bytes[15].toLong() and 0xFF)
                        return isPrivateOrReservedIpv4(nat64V4)
                    }
                    // ::ffff:0:0/96 (IPv4-mapped IPv6)
                    val isIpv4Mapped = (0..9).all { bytes[it] == 0.toByte() } &&
                            bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
                    if (isIpv4Mapped) {
                        val mappedV4 = ((bytes[12].toLong() and 0xFF) shl 24) or
                                ((bytes[13].toLong() and 0xFF) shl 16) or
                                ((bytes[14].toLong() and 0xFF) shl 8) or
                                (bytes[15].toLong() and 0xFF)
                        return isPrivateOrReservedIpv4(mappedV4)
                    }
                    // ::a.b.c.d (IPv4-compatible IPv6, deprecated RFC 4291)
                    val isIpv4Compatible = (0..11).all { bytes[it] == 0.toByte() } &&
                            !(bytes[12] == 0.toByte() && bytes[13] == 0.toByte() && bytes[14] == 0.toByte() && bytes[15] == 1.toByte())
                    if (isIpv4Compatible) {
                        val compatV4 = ((bytes[12].toLong() and 0xFF) shl 24) or
                                ((bytes[13].toLong() and 0xFF) shl 16) or
                                ((bytes[14].toLong() and 0xFF) shl 8) or
                                (bytes[15].toLong() and 0xFF)
                        return isPrivateOrReservedIpv4(compatV4)
                    }
                }
                false
            } catch (_: Exception) {
                // String contains ':' so it looks like IPv6; if unparseable -> treat as unsafe (fail-closed)
                true
            }
        }

        // 3. Check IPv4 literal (standard 4-part or non-standard 1-3 part decimal/hex/octal)
        val v4Ip = parseIpv4Literal(clean)
        if (v4Ip != null) {
            return isPrivateOrReservedIpv4(v4Ip)
        }

        // Public domains are allowed
        return false
    }

    private fun parsePart(part: String): Long? {
        if (part.isEmpty()) return null
        return if (part.startsWith("0x") || part.startsWith("0X")) {
            val hex = part.substring(2)
            if (hex.isEmpty()) null else hex.toLongOrNull(16)
        } else if (part.length > 1 && part.startsWith("0") && part.all { it in '0'..'7' }) {
            part.toLongOrNull(8)
        } else if (part.all { it.isDigit() }) {
            part.toLongOrNull(10)
        } else {
            null
        }
    }

    private fun parseIpv4Literal(input: String): Long? {
        val parts = input.split('.')
        when (parts.size) {
            1 -> {
                // Single 32-bit integer: only treat as IPv4 literal if purely numeric or 0x hex
                if (input.startsWith("0x") || input.startsWith("0X")) {
                    val hex = input.substring(2)
                    if (hex.isNotEmpty() && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                        val v = hex.toLongOrNull(16)
                        if (v != null && v in 0..0xFFFFFFFFL) return v
                    }
                } else if (input.all { it.isDigit() }) {
                    val v = input.toLongOrNull(10)
                    if (v != null && v in 0..0xFFFFFFFFL) return v
                }
                return null
            }
            2 -> {
                val p0 = parsePart(parts[0]) ?: return null
                val p1 = parsePart(parts[1]) ?: return null
                if (p0 in 0..255 && p1 in 0..0xFFFFFFL) {
                    return (p0 shl 24) or p1
                }
                return null
            }
            3 -> {
                val p0 = parsePart(parts[0]) ?: return null
                val p1 = parsePart(parts[1]) ?: return null
                val p2 = parsePart(parts[2]) ?: return null
                if (p0 in 0..255 && p1 in 0..255 && p2 in 0..0xFFFFL) {
                    return (p0 shl 24) or (p1 shl 16) or p2
                }
                return null
            }
            4 -> {
                val p0 = parsePart(parts[0]) ?: return null
                val p1 = parsePart(parts[1]) ?: return null
                val p2 = parsePart(parts[2]) ?: return null
                val p3 = parsePart(parts[3]) ?: return null
                if (p0 in 0..255 && p1 in 0..255 && p2 in 0..255 && p3 in 0..255) {
                    return (p0 shl 24) or (p1 shl 16) or (p2 shl 8) or p3
                }
                return null
            }
            else -> return null
        }
    }

    private fun isPrivateOrReservedIpv4(ip: Long): Boolean {
        val o1 = ((ip ushr 24) and 0xFF).toInt()
        val o2 = ((ip ushr 16) and 0xFF).toInt()
        val o3 = ((ip ushr 8) and 0xFF).toInt()
        val o4 = (ip and 0xFF).toInt()

        // 0.0.0.0/8 (Current network / "this" host)
        if (o1 == 0) return true
        // 10.0.0.0/8 (RFC 1918 Private)
        if (o1 == 10) return true
        // 100.64.0.0/10 (Carrier-Grade NAT: 100.64.0.0 - 100.127.255.255)
        if (o1 == 100 && o2 in 64..127) return true
        // 127.0.0.0/8 (Loopback)
        if (o1 == 127) return true
        // 169.254.0.0/16 (Link-Local)
        if (o1 == 169 && o2 == 254) return true
        // 172.16.0.0/12 (RFC 1918 Private: 172.16.0.0 - 172.31.255.255)
        if (o1 == 172 && o2 in 16..31) return true
        // 192.0.0.0/24 (IETF Protocol Assignments)
        if (o1 == 192 && o2 == 0 && o3 == 0) return true
        // 192.0.2.0/24 (TEST-NET-1)
        if (o1 == 192 && o2 == 0 && o3 == 2) return true
        // 192.168.0.0/16 (RFC 1918 Private)
        if (o1 == 192 && o2 == 168) return true
        // 198.18.0.0/15 (Benchmarking: 198.18.0.0 - 198.19.255.255)
        if (o1 == 198 && (o2 == 18 || o2 == 19)) return true
        // 198.51.100.0/24 (TEST-NET-2)
        if (o1 == 198 && o2 == 51 && o3 == 100) return true
        // 203.0.113.0/24 (TEST-NET-3)
        if (o1 == 203 && o2 == 0 && o3 == 113) return true
        // 224.0.0.0/4 (Multicast: 224.0.0.0 - 239.255.255.255)
        if (o1 in 224..239) return true
        // 240.0.0.0/4 (Reserved / Broadcast: 240.0.0.0 - 255.255.255.255)
        if (o1 in 240..255) return true

        return false
    }
}
