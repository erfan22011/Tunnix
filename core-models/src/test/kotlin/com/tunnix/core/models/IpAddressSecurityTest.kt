package com.tunnix.core.models

import org.junit.Assert.*
import org.junit.Test

class IpAddressSecurityTest {

    @Test
    fun `test loopback addresses detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("127.0.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("127.1.2.3"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("localhost"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("::1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("[::1]"))
    }

    @Test
    fun `test private RFC 1918 addresses detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("10.0.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("10.255.255.255"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("172.16.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("172.31.255.255"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("192.168.1.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("192.168.0.254"))
    }

    @Test
    fun `test carrier grade NAT and link local addresses detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("100.64.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("100.127.255.255"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("169.254.1.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("0.0.0.0"))
    }

    @Test
    fun `test IPv6 ULA and link local addresses detected`() {
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fd00::1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fc00::1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("fe80::1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("::ffff:127.0.0.1"))
        assertTrue(IpAddressSecurity.isPrivateOrLoopback("::ffff:192.168.1.1"))
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
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("ovv.pelqexa.ir"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("cdn.example.net"))
        assertFalse(IpAddressSecurity.isPrivateOrLoopback("2053ipapp.genus.ir"))
    }
}
