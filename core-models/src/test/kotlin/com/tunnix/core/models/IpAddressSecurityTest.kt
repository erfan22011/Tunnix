package com.tunnix.core.models

import org.junit.Assert.*
import org.junit.Test

class IpAddressSecurityTest {

    @Test
    fun `test loopback addresses and non-standard IPv4 literals detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("127.0.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("127.1.2.3"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("127.1")) // 2-part IPv4 (127.0.0.1)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("0x7f.0.0.1")) // hex part
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("2130706433")) // 1-part decimal (127.0.0.1)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("0x7f000001")) // 1-part hex (127.0.0.1)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("localhost"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("::1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("[::1]"))
    }

    @Test
    fun `test private RFC 1918 addresses detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("10.0.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("10.1")) // 2-part IPv4
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("10.255.255.255"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("172.16.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("172.31.255.255"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("192.168.1.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("192.168.0.254"))
    }

    @Test
    fun `test special and reserved IPv4 ranges detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("0.0.0.0"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("100.64.0.1")) // CGNAT
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("100.127.255.255")) // CGNAT
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("169.254.1.1")) // Link-Local
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("192.0.0.1")) // IETF Protocol Assignments (192.0.0.0/24)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("192.0.2.1")) // TEST-NET-1 (192.0.2.0/24)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("198.18.0.1")) // Benchmarking (198.18.0.0/15)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("198.19.255.254")) // Benchmarking (198.18.0.0/15)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("198.51.100.1")) // TEST-NET-2 (198.51.100.0/24)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("203.0.113.1")) // TEST-NET-3 (203.0.113.0/24)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("224.0.0.1")) // Multicast (224.0.0.0/4)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("239.255.255.250")) // Multicast (224.0.0.0/4)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("240.0.0.1")) // Reserved (240.0.0.0/4)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("255.255.255.255")) // Broadcast (240.0.0.0/4)
    }

    @Test
    fun `test IPv6 ULA link local NAT64 and unparseable detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fd00::1")) // ULA
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fc00::1")) // ULA
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fe80::1")) // Link-local
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fe80::1%wlan0")) // Link-local with zone
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fe80::dead:beef%eth0")) // Link-local with zone
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("::ffff:127.0.0.1")) // IPv4-mapped loopback
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("::ffff:192.168.1.1")) // IPv4-mapped private
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("64:ff9b::127.0.0.1")) // NAT64 loopback
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("64:ff9b::192.168.1.1")) // NAT64 private
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("64:ff9b::")) // NAT64 prefix (embeds 0.0.0.0)
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("bad:ipv6:value")) // Unparseable IPv6 treated as unsafe
    }

    @Test
    fun `test local domain suffixes detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("router.local"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("server.internal"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("device.lan"))
    }

    @Test
    fun `test public IP addresses and valid domains allowed`() {
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("1.1.1.1"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("8.8.8.8"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("185.10.20.30"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("172.32.0.1")) // outside 172.16..31
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("100.63.255.255")) // outside CGNAT
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("198.20.0.1")) // outside 198.18..19
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("2001:4860:4860::8888")) // Google IPv6
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("2606:4700:4700::1111")) // Cloudflare IPv6
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("ovv.pelqexa.ir"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("cdn.example.net"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("2053ipapp.genus.ir"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("example.com"))
    }
}
