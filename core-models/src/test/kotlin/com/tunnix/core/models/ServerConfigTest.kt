package com.tunnix.core.models

import org.junit.Assert.*
import org.junit.Test

class ServerConfigTest {

    @Test
    fun `test isTransportUnencrypted identifies unencrypted protocols correctly`() {
        val vlessNone = ServerConfig(
            id = "1", name = "Vless None", address = "a.com", port = 443,
            protocol = ProtocolType.VLESS, security = "none"
        )
        assertTrue(vlessNone.isTransportUnencrypted)

        val vlessBlank = vlessNone.copy(security = "")
        assertTrue(vlessBlank.isTransportUnencrypted)

        val vlessTls = vlessNone.copy(security = "tls")
        assertFalse(vlessTls.isTransportUnencrypted)

        val vlessReality = vlessNone.copy(security = "reality")
        assertFalse(vlessReality.isTransportUnencrypted)

        // VMess with encryption (auto, aes-128-gcm, etc.) is encrypted and should NOT show No TLS
        val vmessAuto = vlessNone.copy(protocol = ProtocolType.VMESS, security = "none", encryption = "auto")
        assertFalse(vmessAuto.isTransportUnencrypted)

        val vmessAes = vlessNone.copy(protocol = ProtocolType.VMESS, security = "none", encryption = "aes-128-gcm")
        assertFalse(vmessAes.isTransportUnencrypted)

        // VMess default (without explicit none scy) uses auto in Xray core -> not unencrypted
        val vmessDefault = vlessNone.copy(protocol = ProtocolType.VMESS, security = "none")
        assertFalse(vmessDefault.isTransportUnencrypted)

        // VMess explicitly disabled (scy=none and encryption=none) is unencrypted
        val vmessExplicitNone = vlessNone.copy(
            protocol = ProtocolType.VMESS, security = "none", encryption = "none",
            queryParams = mapOf("scy" to "none")
        )
        assertTrue(vmessExplicitNone.isTransportUnencrypted)

        val vmessTls = vlessNone.copy(protocol = ProtocolType.VMESS, security = "tls")
        assertFalse(vmessTls.isTransportUnencrypted)

        val trojanNone = vlessNone.copy(protocol = ProtocolType.TROJAN, security = "none")
        assertTrue(trojanNone.isTransportUnencrypted)

        val trojanTls = vlessNone.copy(protocol = ProtocolType.TROJAN, security = "tls")
        assertFalse(trojanTls.isTransportUnencrypted)

        // Shadowsocks uses its own authenticated cipher, not considered unencrypted
        val ssNone = vlessNone.copy(protocol = ProtocolType.SHADOWSOCKS, security = "none")
        assertFalse(ssNone.isTransportUnencrypted)
    }
}
