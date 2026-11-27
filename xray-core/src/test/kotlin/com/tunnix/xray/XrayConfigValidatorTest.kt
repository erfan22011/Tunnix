package com.tunnix.xray

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import org.junit.Assert.*
import org.junit.Test

class XrayConfigValidatorTest {

    @Test
    fun `test valid generated config passes validation`() {
        val server = ServerConfig(
            id = "server-1",
            name = "VLESS XHTTP",
            address = "ovv.pelqexa.ir",
            port = 2053,
            protocol = ProtocolType.VLESS,
            network = NetworkType.XHTTP,
            security = "tls",
            uuid = "03fa381a-21f2-4b75-9b6e-a4595d62106e"
        )

        val json = XrayConfigGenerator.generate(server)
        val result = XrayConfigValidator.validate(json)

        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `test invalid empty json fails validation`() {
        val result = XrayConfigValidator.validate("")
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("empty") })
    }

    @Test
    fun `test malformed json syntax fails validation gracefully`() {
        val brokenJson = "{ \"inbounds\": [ invalid_json_syntax"
        val result = XrayConfigValidator.validate(brokenJson)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("Invalid JSON syntax") })
    }

    @Test
    fun `test non-object root fails validation`() {
        val arrayRoot = "[\"inbounds\", \"outbounds\"]"
        val result = XrayConfigValidator.validate(arrayRoot)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("must be a JSON Object") })
    }

    @Test
    fun `test missing mandatory fields fails validation`() {
        val missingOutbounds = """{"inbounds": [{"tag": "tun", "protocol": "tun"}], "routing": {}}"""
        val result = XrayConfigValidator.validate(missingOutbounds)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("outbounds") })
    }

    @Test
    fun `test invalid port range rejected`() {
        // Test port > 65535
        val invalidHighPort = """
            {
              "inbounds": [{"tag": "in", "protocol": "socks", "port": 99999}],
              "outbounds": [{"tag": "proxy", "protocol": "vless"}],
              "routing": {}
            }
        """.trimIndent()
        val resultHigh = XrayConfigValidator.validate(invalidHighPort)
        assertFalse(resultHigh.isValid)
        assertTrue(resultHigh.issues.any { it.contains("invalid port") })

        // Test negative port
        val invalidNegativePort = """
            {
              "inbounds": [{"tag": "in", "protocol": "socks", "port": -5}],
              "outbounds": [{"tag": "proxy", "protocol": "vless"}],
              "routing": {}
            }
        """.trimIndent()
        val resultNeg = XrayConfigValidator.validate(invalidNegativePort)
        assertFalse(resultNeg.isValid)
        assertTrue(resultNeg.issues.any { it.contains("invalid port") })
    }

    @Test
    fun `test unexpected nested types handled safely without crash`() {
        val weirdTypes = """
            {
              "inbounds": [{"tag": 123, "protocol": []}],
              "outbounds": [{"tag": "proxy", "protocol": "vless", "streamSettings": "should_be_object"}],
              "routing": 456
            }
        """.trimIndent()
        val result = XrayConfigValidator.validate(weirdTypes)
        assertFalse(result.isValid)
        assertTrue(result.issues.isNotEmpty())
    }

    @Test
    fun `test excessively large json payload rejected`() {
        val hugePadding = " ".repeat(XrayConfigValidator.MAX_CONFIG_JSON_BYTES + 10)
        val hugeJson = "{$hugePadding}"
        val result = XrayConfigValidator.validate(hugeJson)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("exceeds size limit") })
    }

    @Test
    fun `test json with stream-up mode without downloadSettings is valid in standard XHTTP`() {
        val standardStreamUp = """
            {
              "inbounds": [{"tag": "tun", "protocol": "tun"}],
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "streamSettings": {
                    "network": "xhttp",
                    "xhttpSettings": { "mode": "stream-up", "extra": { "xPaddingBytes": "100-1000" } }
                  }
                }
              ],
              "routing": {}
            }
        """.trimIndent()

        val result = XrayConfigValidator.validate(standardStreamUp)
        assertTrue("Standard XHTTP stream-up without downloadSettings must be valid: ${result.issues}", result.isValid)
    }

    @Test
    fun `test downloadSettings in nested extra with private IP fails validation`() {
        val nestedWithLoopback = """
            {
              "inbounds": [{"tag": "tun-in", "protocol": "tun", "port": 0}],
              "outbounds": [{
                "tag": "proxy",
                "protocol": "vless",
                "settings": {"vnext": [{"address": "ovv.pelqexa.ir", "port": 2053}]},
                "streamSettings": {
                  "network": "xhttp",
                  "xhttpSettings": {
                    "extra": {
                      "downloadSettings": {
                        "address": "10.0.0.1",
                        "port": 2053,
                        "tlsSettings": {},
                        "xhttpSettings": {}
                      }
                    }
                  }
                }
              }],
              "routing": {"rules": [{"outboundTag": "proxy"}]}
            }
        """.trimIndent()

        val result = XrayConfigValidator.validate(nestedWithLoopback)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("loopback or private IP") })
    }

    @Test
    fun `test downloadSettings with loopback or private IP fails validation`() {
        val ssWithLoopback = """
            {
              "inbounds": [{"tag": "tun-in", "protocol": "tun", "port": 0}],
              "outbounds": [{
                "tag": "proxy",
                "protocol": "vless",
                "settings": {"vnext": [{"address": "ovv.pelqexa.ir", "port": 2053}]},
                "streamSettings": {
                  "network": "xhttp",
                  "xhttpSettings": {},
                  "downloadSettings": {
                    "address": "127.0.0.1",
                    "port": 2053,
                    "tlsSettings": {},
                    "xhttpSettings": {}
                  }
                }
              }],
              "routing": {"rules": [{"outboundTag": "proxy"}]}
            }
        """.trimIndent()

        val result = XrayConfigValidator.validate(ssWithLoopback)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("loopback or private IP") })
    }
}
