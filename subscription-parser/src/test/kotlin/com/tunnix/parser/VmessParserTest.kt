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
        assertTrue(server.queryParams.isEmpty())
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
        assertEquals("4", server.queryParams["alterId"])
    }
}
