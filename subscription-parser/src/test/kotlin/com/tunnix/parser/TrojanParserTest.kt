package com.tunnix.parser

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import org.junit.Assert.*
import org.junit.Test

class TrojanParserTest {

    @Test
    fun `test standard Trojan URI parsing`() {
        val uri = "trojan://secretpassword123@trojan.genus.ir:443?security=tls&sni=trojan.genus.ir&type=tcp#Trojan-Swiss"

        val server = TrojanParser.parse(uri)
        assertNotNull(server)
        assertEquals("secretpassword123", server!!.uuid)
        assertEquals("trojan.genus.ir", server.address)
        assertEquals(443, server.port)
        assertEquals(ProtocolType.TROJAN, server.protocol)
        assertEquals(NetworkType.TCP, server.network)
        assertEquals("tls", server.security)
        assertEquals("trojan.genus.ir", server.sni)
        assertEquals("Trojan-Swiss", server.name)
    }
}
