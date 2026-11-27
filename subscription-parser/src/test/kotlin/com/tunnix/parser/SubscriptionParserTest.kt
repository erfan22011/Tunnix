package com.tunnix.parser

import com.tunnix.core.models.ProtocolType
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class SubscriptionParserTest {

    @Test
    fun `test parsing multiline plaintext subscription`() {
        val raw = """
            vless://uuid-1@ovv.genus.ir:2053?type=xhttp#Server-1
            trojan://pass-2@ovv.pelqexa.ir:443?type=tcp#Server-2
            # Comment line
            invalid://protocol@test.com
        """.trimIndent()

        val servers = SubscriptionParser.parse(raw)
        assertEquals(2, servers.size)
        assertEquals("Server-1", servers[0].name)
        assertEquals(ProtocolType.VLESS, servers[0].protocol)
        assertEquals("Server-2", servers[1].name)
        assertEquals(ProtocolType.TROJAN, servers[1].protocol)
    }

    @Test
    fun `test parsing base64 encoded subscription`() {
        val raw = """
            vless://uuid-1@ovv.genus.ir:2053?type=xhttp#Server-1
            vless://uuid-2@ovv.pelqexa.ir:2053?type=xhttp#Server-2
        """.trimIndent()

        val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        val servers = SubscriptionParser.parse(encoded)

        assertEquals(2, servers.size)
        assertEquals("Server-1", servers[0].name)
        assertEquals("Server-2", servers[1].name)
    }

    @Test
    fun `test deduplicate identical server lines`() {
        val raw = """
            vless://uuid-1@ovv.genus.ir:2053?type=xhttp#Server-1
            vless://uuid-1@ovv.genus.ir:2053?type=xhttp#Server-1
            vless://uuid-1@ovv.genus.ir:2053?type=xhttp#Server-1
            vless://uuid-2@ovv.pelqexa.ir:2053?type=xhttp#Server-2
        """.trimIndent()

        val servers = SubscriptionParser.parse(raw)
        assertEquals("Duplicate servers must be deduplicated", 2, servers.size)
        assertEquals("Server-1", servers[0].name)
        assertEquals("Server-2", servers[1].name)
    }

    @Test
    fun `test ignore excessively long malformed lines`() {
        val hugeLine = "vless://uuid-1@ovv.genus.ir:2053?type=xhttp#" + "A".repeat(5000)
        val validLine = "vless://uuid-2@ovv.pelqexa.ir:2053?type=xhttp#Server-Valid"
        val raw = "$hugeLine\n$validLine"

        val servers = SubscriptionParser.parse(raw)
        assertEquals("Lines exceeding MAX_LINE_LENGTH must be skipped", 1, servers.size)
        assertEquals("Server-Valid", servers[0].name)
    }

    @Test(expected = SubscriptionParserException.EmptySubscriptionException::class)
    fun `test empty subscription throws EmptySubscriptionException`() {
        SubscriptionParser.parse("   \r\n  ")
    }

    @Test
    fun `test cap maximum servers at 500`() {
        val sb = StringBuilder()
        for (i in 1..550) {
            sb.append("vless://uuid-$i@server$i.com:443?type=tcp#Server-$i\n")
        }
        val servers = SubscriptionParser.parse(sb.toString())
        assertEquals(500, servers.size)
        assertEquals("Server-1", servers.first().name)
        assertEquals("Server-500", servers.last().name)
    }
}
