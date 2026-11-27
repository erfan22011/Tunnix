package com.tunnix.core.models

import org.junit.Assert.*
import org.junit.Test

class XhttpExtraSanitizerTest {

    @Test
    fun `test standard XHTTP keys are preserved`() {
        val input = """
            {
              "xPaddingBytes": "100-1000",
              "xmux": {"maxConcurrency": 16},
              "headers": {"User-Agent": "TestAgent"},
              "scMinPostsIntervalMs": 30,
              "scMaxBufferedPosts": 100,
              "scMaxEachPostBytes": 1000000,
              "scStreamUpServerSecs": 10,
              "noGRPCHeader": true,
              "noSSEHeader": true
            }
        """.trimIndent()

        val result = XhttpExtraSanitizer.sanitize(input)
        assertEquals("100-1000", result.get("xPaddingBytes").asString)
        assertTrue(result.has("xmux"))
        assertEquals(16, result.getAsJsonObject("xmux").get("maxConcurrency").asInt)
        assertTrue(result.has("headers"))
        assertEquals(30, result.get("scMinPostsIntervalMs").asInt)
        assertEquals(100, result.get("scMaxBufferedPosts").asInt)
        assertEquals(1000000, result.get("scMaxEachPostBytes").asInt)
        assertEquals(10, result.get("scStreamUpServerSecs").asInt)
        assertTrue(result.get("noGRPCHeader").asBoolean)
        assertTrue(result.get("noSSEHeader").asBoolean)
    }

    @Test
    fun `test forbidden keys are removed`() {
        val input = """
            {
              "xPaddingBytes": "100-1000",
              "sockopt": {"mark": 255},
              "command": "rm -rf /",
              "exec": "evil.sh",
              "script": "alert(1)",
              "browserforwarder": "http://evil.com",
              "reverse": true
            }
        """.trimIndent()

        val result = XhttpExtraSanitizer.sanitize(input)
        assertEquals("100-1000", result.get("xPaddingBytes").asString)
        assertFalse(result.has("sockopt"))
        assertFalse(result.has("command"))
        assertFalse(result.has("exec"))
        assertFalse(result.has("script"))
        assertFalse(result.has("browserforwarder"))
        assertFalse(result.has("reverse"))
    }

    @Test
    fun `test downloadSettings with loopback or private IP is dropped completely`() {
        val loopback = """{"downloadSettings": {"address": "127.0.0.1", "port": 443, "network": "xhttp", "security": "tls"}}"""
        val res1 = XhttpExtraSanitizer.sanitize(loopback)
        assertFalse("Loopback downloadSettings must be dropped", res1.has("downloadSettings"))

        val privateIp = """{"downloadSettings": {"address": "192.168.1.1", "port": 443, "network": "xhttp", "security": "tls"}}"""
        val res2 = XhttpExtraSanitizer.sanitize(privateIp)
        assertFalse("Private IP downloadSettings must be dropped", res2.has("downloadSettings"))

        val localhost = """{"downloadSettings": {"address": "localhost", "port": 443, "network": "xhttp", "security": "tls"}}"""
        val res3 = XhttpExtraSanitizer.sanitize(localhost)
        assertFalse("localhost downloadSettings must be dropped", res3.has("downloadSettings"))
    }

    @Test
    fun `test downloadSettings with public address is kept and sanitized`() {
        val valid = """
            {
              "downloadSettings": {
                "address": "2053ipapp.genus.ir",
                "port": 2053,
                "network": "xhttp",
                "security": "tls",
                "sockopt": {"mark": 1},
                "tlsSettings": {
                  "serverName": "2053ovapp.genus.ir",
                  "allowInsecure": true,
                  "alpn": ["h2"]
                }
              }
            }
        """.trimIndent()

        val res = XhttpExtraSanitizer.sanitize(valid)
        assertTrue(res.has("downloadSettings"))
        val dl = res.getAsJsonObject("downloadSettings")
        assertEquals("2053ipapp.genus.ir", dl.get("address").asString)
        assertEquals(2053, dl.get("port").asInt)
        assertFalse("sockopt must be removed from downloadSettings", dl.has("sockopt"))

        val tls = dl.getAsJsonObject("tlsSettings")
        assertEquals("2053ovapp.genus.ir", tls.get("serverName").asString)
        assertFalse("allowInsecure must be forced to false", tls.get("allowInsecure").asBoolean)
    }

    @Test
    fun `test excessively nested or oversized payload returns empty object`() {
        val deep = """{"a":{"b":{"c":{"d":{"e":{"f":1}}}}}}"""
        val resDeep = XhttpExtraSanitizer.sanitize(deep)
        assertEquals(0, resDeep.size())

        val huge = "{\"a\":\"" + "x".repeat(9000) + "\"}"
        val resHuge = XhttpExtraSanitizer.sanitize(huge)
        assertEquals(0, resHuge.size())
    }
}
