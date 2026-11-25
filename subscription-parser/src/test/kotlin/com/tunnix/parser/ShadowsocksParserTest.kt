package com.tunnix.parser

import com.tunnix.core.models.ProtocolType
import org.junit.Assert.*
import org.junit.Test

class ShadowsocksParserTest {

    @Test
    fun `test standard SIP002 Shadowsocks link parsing`() {
        // Link provided by user:
        val link = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpuMXI4YmF2dTgwMjhwNmJ6@ir.jorqino.ir:443?security=none&type=tcp#%F0%93%86%A9%F0%9F%9A%80%F0%93%86%AA%F0%9D%97%98%F0%9D%97%A9%F0%9D%97%A3%F0%9D%97%A1-test3"
        val server = ShadowsocksParser.parse(link)

        assertNotNull("Server must parse successfully", server)
        server!!

        assertEquals(ProtocolType.SHADOWSOCKS, server.protocol)
        assertEquals("ir.jorqino.ir", server.address)
        assertEquals(443, server.port)
        assertEquals("chacha20-ietf-poly1305", server.encryption)
        assertEquals("n1r8bavu8028p6bz", server.uuid)
        assertTrue(server.name.contains("test3"))
    }

    @Test
    fun `test SubscriptionParser parses multi-protocol list including Shadowsocks`() {
        val list = """
            vless://1cd96b76-bf6c-439a-9229-6716d22ff382@ir.jorqino.ir:442?security=tls&type=tcp#Vless1
            ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpuMXI4YmF2dTgwMjhwNmJ6@ir.jorqino.ir:443?security=none&type=tcp#SS1
        """.trimIndent()

        val parsed = SubscriptionParser.parse(list)
        assertEquals(2, parsed.size)
        assertEquals(ProtocolType.VLESS, parsed[0].protocol)
        assertEquals(ProtocolType.SHADOWSOCKS, parsed[1].protocol)
    }
}
