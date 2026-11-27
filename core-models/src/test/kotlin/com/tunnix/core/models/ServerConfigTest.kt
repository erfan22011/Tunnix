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

        val vmessNone = vlessNone.copy(protocol = ProtocolType.VMESS, security = "none")
        assertTrue(vmessNone.isTransportUnencrypted)

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
