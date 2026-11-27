package com.tunnix.parser

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class VmessParserTest {

    @Test
    fun `test standard VMess URI parsing`() {
        val json = """
            {
              "v": "2",
              "ps": "VMess Netherlands",
              "add": "nl.server.com",
              "port": 443,
              "id": "b831381d-6324-4d53-ad4f-8cda48b30811",
              "aid": 0,
              "scy": "auto",
              "net": "ws",
              "type": "none",
              "host": "nl.server.com",
              "path": "/vmessws",
              "tls": "tls",
              "sni": "nl.server.com",
              "alpn": "h2"
            }
        """.trimIndent()

        val base64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val uri = "vmess://$base64"

        val server = VmessParser.parse(uri)
        assertNotNull(server)
        assertEquals("VMess Netherlands", server!!.name)
        assertEquals("nl.server.com", server.address)
        assertEquals(443, server.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", server.uuid)
        assertEquals(ProtocolType.VMESS, server.protocol)
        assertEquals(NetworkType.WS, server.network)
        assertEquals("/vmessws", server.path)
        assertEquals("tls", server.security)
        assertEquals("auto", server.encryption)
        assertEquals("auto", server.queryParams["scy"])
    }

    @Test
    fun `test VMess parsing with custom scy and alterId`() {
        val json = """
            {
              "v": "2",
              "ps": "VMess Custom",
              "add": "custom.server.com",
              "port": 10086,
              "id": "b831381d-6324-4d53-ad4f-8cda48b30811",
              "aid": 4,
              "scy": "aes-128-gcm",
              "net": "tcp",
              "type": "none"
            }
        """.trimIndent()

        val base64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val uri = "vmess://$base64"

        val server = VmessParser.parse(uri)
        assertNotNull(server)
        assertEquals("aes-128-gcm", server!!.encryption)
        assertEquals("aes-128-gcm", server.queryParams["scy"])
        assertEquals("4", server.queryParams["alterId"])
    }

    @Test
    fun `test VMess parsing with malformed or non-primitive aid and scy does not drop server`() {
        val malformedCases = listOf(
            """{"v":"2","ps":"S1","add":"s1.com","port":443,"id":"uuid-1","aid":"","scy":"auto"}""",
            """{"v":"2","ps":"S2","add":"s2.com","port":443,"id":"uuid-2","aid":"x","scy":"auto"}""",
            """{"v":"2","ps":"S3","add":"s3.com","port":443,"id":"uuid-3","aid":{},"scy":"auto"}""",
            """{"v":"2","ps":"S4","add":"s4.com","port":443,"id":"uuid-4","aid":[],"scy":"auto"}""",
            """{"v":"2","ps":"S5","add":"s5.com","port":443,"id":"uuid-5","aid":0,"scy":{}}""",
            """{"v":"2","ps":"S6","add":"s6.com","port":443,"id":"uuid-6","aid":0,"scy":null}"""
        )

        for ((idx, json) in malformedCases.withIndex()) {
            val b64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
            val server = VmessParser.parse("vmess://$b64")
            assertNotNull("Server case $idx must not be dropped due to malformed aid/scy", server)
            assertEquals("auto", server!!.queryParams["scy"])
            assertFalse(server.queryParams.containsKey("alterId"))
        }
    }

    @Test
    fun `test VMess parsing with string aid converts to integer`() {
        val json = """{"v":"2","ps":"S","add":"s.com","port":443,"id":"uuid-1","aid":"64","scy":"auto"}"""
        val b64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val server = VmessParser.parse("vmess://$b64")
        assertNotNull(server)
        assertEquals("64", server!!.queryParams["alterId"])
    }

    @Test
    fun `test VMess parsing with explicit none scy`() {
        val json = """{"v":"2","ps":"S","add":"s.com","port":443,"id":"uuid-1","aid":0,"scy":"none"}"""
        val b64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val server = VmessParser.parse("vmess://$b64")
        assertNotNull(server)
        assertEquals("none", server!!.queryParams["scy"])
    }

    @Test
    fun `test VMess parsing with invalid port returns null`() {
        val json = """{"v":"2","ps":"S","add":"s.com","port":70000,"id":"uuid-1"}"""
        val b64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        assertNull(VmessParser.parse("vmess://$b64"))
    }
}
