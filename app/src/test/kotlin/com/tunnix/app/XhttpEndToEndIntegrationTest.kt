package com.tunnix.app

import com.google.gson.JsonParser
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import com.tunnix.modifier.XhttpConfigModifier
import com.tunnix.xray.XrayConfigGenerator
import com.tunnix.xray.XrayConfigValidator
import org.junit.Assert.*
import org.junit.Test

class XhttpEndToEndIntegrationTest {

    private fun sampleServer(
        port: Int,
        address: String,
        protocol: ProtocolType = ProtocolType.VLESS,
        mode: String = "auto",
        extraJson: String = ""
    ): ServerConfig {
        return ServerConfig(
            id = "test-$protocol-$port-$address",
            name = "Server-$port",
            address = address,
            port = port,
            protocol = protocol,
            network = NetworkType.XHTTP,
            security = "tls",
            uuid = "03fa381a-21f2-4b75-9b6e-a4595d62106e",
            sni = "ovv.genus.ir",
            host = "ovv.genus.ir",
            path = "/xhttp",
            mode = mode,
            extraJson = extraJson,
            rawUri = "vless://03fa381a-21f2-4b75-9b6e-a4595d62106e@$address:$port?mode=$mode#test"
        )
    }

    @Test
    fun `test matrix ports 2053 and 6793 across all address types and custom configs`() {
        val ports = listOf(2053, 6793)
        val addresses = listOf("ovv.pelqexa.ir", "185.10.20.30", "cdn.example.net")
        val customs = listOf(null, DownloadSettingsConfig.DEFAULT)

        for (port in ports) {
            for (address in addresses) {
                for (custom in customs) {
                    val rawServer = sampleServer(port, address)
                    val modifiedList = XhttpConfigModifier.modifyAll(listOf(rawServer), custom)
                    assertEquals(1, modifiedList.size)
                    val server = modifiedList[0]

                    val msg = "Failed for port=$port, address=$address, custom=$custom"
                    assertTrue("$msg: isXhttpModified must be true", server.isXhttpModified)
                    assertEquals("$msg: mode must be stream-up", "stream-up", server.mode)
                    assertNotNull("$msg: downloadSettings must not be null", server.downloadSettings)

                    val expectedAddress = if (port == 2053) "2053ipapp.genus.ir" else "6793ipapp.genus.ir"
                    assertEquals("$msg: download address mismatch", expectedAddress, server.downloadSettings!!.address)
                    assertEquals("$msg: download port mismatch", port, server.downloadSettings!!.port)

                    // Test generator
                    val json = XrayConfigGenerator.generate(server, customDownloadSettings = custom)
                    val root = JsonParser.parseString(json).asJsonObject
                    val proxyOutbound = root.getAsJsonArray("outbounds")
                        .first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
                    val streamSettings = proxyOutbound.getAsJsonObject("streamSettings")

                    assertTrue("$msg: generator must include downloadSettings", streamSettings.has("downloadSettings"))
                    val dlSettings = streamSettings.getAsJsonObject("downloadSettings")
                    assertEquals(expectedAddress, dlSettings.get("address").asString)

                    val xhttp = streamSettings.getAsJsonObject("xhttpSettings")
                    val extra = xhttp.getAsJsonObject("extra")
                    assertTrue("$msg: extra must have xPaddingBytes", extra.has("xPaddingBytes"))

                    // Test validator
                    val validation = XrayConfigValidator.validate(json)
                    assertTrue("$msg: validation failed: ${validation.issues}", validation.isValid)
                }
            }
        }
    }

    @Test
    fun `test matrix ports 443 and 8443 remain standard XHTTP and pass validation even with stream-up`() {
        val ports = listOf(443, 8443)
        val addresses = listOf("ovv.pelqexa.ir", "185.10.20.30", "cdn.example.net")
        val customs = listOf(null, DownloadSettingsConfig.DEFAULT)

        for (port in ports) {
            for (address in addresses) {
                for (custom in customs) {
                    // Test both mode auto and mode stream-up on non-preset ports
                    for (mode in listOf("auto", "stream-up")) {
                        val rawServer = sampleServer(port, address, mode = mode)
                        val modifiedList = XhttpConfigModifier.modifyAll(listOf(rawServer), custom)
                        val server = modifiedList[0]

                        val msg = "Failed for port=$port, address=$address, mode=$mode, custom=$custom"
                        assertFalse("$msg: port $port must NOT be marked as xhttp modified", server.isXhttpModified)
                        assertNull("$msg: port $port must not have downloadSettings injected", server.downloadSettings)

                        // Generator produces standard XHTTP
                        val json = XrayConfigGenerator.generate(server, customDownloadSettings = custom)
                        val root = JsonParser.parseString(json).asJsonObject
                        val proxyOutbound = root.getAsJsonArray("outbounds")
                            .first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
                        val streamSettings = proxyOutbound.getAsJsonObject("streamSettings")

                        assertFalse("$msg: standard XHTTP must NOT have root downloadSettings", streamSettings.has("downloadSettings"))
                        val xhttp = streamSettings.getAsJsonObject("xhttpSettings")
                        assertEquals(mode, xhttp.get("mode").asString)

                        // Validation MUST pass even if mode=stream-up without downloadSettings
                        val validation = XrayConfigValidator.validate(json)
                        assertTrue("$msg: validation must pass: ${validation.issues}", validation.isValid)
                    }
                }
            }
        }
    }

    @Test
    fun `test extra parameters preserved for standard XHTTP on port 443`() {
        val extraPayload = """{"xmux":{"maxConcurrency":16},"headers":{"X-Custom":"true"},"scMinPostsIntervalMs":30,"scMaxBufferedPosts":100}"""
        val rawServer = sampleServer(443, "ovv.pelqexa.ir", extraJson = extraPayload)

        val json = XrayConfigGenerator.generate(rawServer, customDownloadSettings = DownloadSettingsConfig.DEFAULT)
        val root = JsonParser.parseString(json).asJsonObject
        val streamSettings = root.getAsJsonArray("outbounds")
            .first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
            .getAsJsonObject("streamSettings")
        val extra = streamSettings.getAsJsonObject("xhttpSettings").getAsJsonObject("extra")

        assertNotNull("extra must be present", extra)
        assertTrue("xmux must be preserved", extra.has("xmux"))
        assertTrue("headers must be preserved", extra.has("headers"))
        assertEquals(30, extra.get("scMinPostsIntervalMs").asInt)
        assertEquals(100, extra.get("scMaxBufferedPosts").asInt)

        val validation = XrayConfigValidator.validate(json)
        assertTrue("Config with rich extra must pass validation: ${validation.issues}", validation.isValid)
    }

    @Test
    fun `test downloadSettings with loopback or private address is dropped and connection continues`() {
        val dangerousAddresses = listOf("127.0.0.1", "10.0.0.5", "192.168.1.1", "172.16.0.1", "::1", "[::1]", "fd00::1", "localhost")
        for (addr in dangerousAddresses) {
            val maliciousExtra = """{"downloadSettings":{"address":"$addr","port":443,"network":"xhttp","security":"tls"}}"""
            val rawServer = sampleServer(443, "ovv.pelqexa.ir", extraJson = maliciousExtra)

            val json = XrayConfigGenerator.generate(rawServer, customDownloadSettings = DownloadSettingsConfig.DEFAULT)
            val root = JsonParser.parseString(json).asJsonObject
            val streamSettings = root.getAsJsonArray("outbounds")
                .first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
                .getAsJsonObject("streamSettings")

            assertFalse("Dangerous downloadSettings must NOT be in streamSettings for addr=$addr", streamSettings.has("downloadSettings"))
            val xhttp = streamSettings.getAsJsonObject("xhttpSettings")
            if (xhttp.has("extra") && xhttp.get("extra").isJsonObject) {
                assertFalse("Dangerous downloadSettings must NOT be in extra for addr=$addr", xhttp.getAsJsonObject("extra").has("downloadSettings"))
            }

            val validation = XrayConfigValidator.validate(json)
            assertTrue("Sanitized config must pass validation for addr=$addr: ${validation.issues}", validation.isValid)
        }
    }

    @Test
    fun `test allowInsecure is strictly rejected and never reaches generator output`() {
        val serverWithInsecure = sampleServer(443, "ovv.pelqexa.ir").copy(
            queryParams = mapOf("allowInsecure" to "1", "insecure" to "1"),
            extraJson = """{"downloadSettings":{"address":"ovapp.genus.ir","port":443,"network":"xhttp","security":"tls","tlsSettings":{"allowInsecure":true}}}"""
        )

        val json = XrayConfigGenerator.generate(serverWithInsecure, customDownloadSettings = DownloadSettingsConfig.DEFAULT)
        assertFalse("Generated json must never contain allowInsecure true", json.contains("\"allowInsecure\":true") || json.contains("\"allowInsecure\": true"))

        val validation = XrayConfigValidator.validate(json)
        assertTrue("Validation must pass with allowInsecure=false: ${validation.issues}", validation.isValid)
    }

    @Test
    fun `test non-VLESS server on port 2053 is untouched`() {
        val trojanServer = sampleServer(2053, "ovv.pelqexa.ir", protocol = ProtocolType.TROJAN)
        val modifiedList = XhttpConfigModifier.modifyAll(listOf(trojanServer), DownloadSettingsConfig.DEFAULT)

        assertFalse(modifiedList[0].isXhttpModified)
        assertNull(modifiedList[0].downloadSettings)
    }

    @Test
    fun `test modifyAll is idempotent`() {
        val server2053 = sampleServer(2053, "185.10.20.30")
        val server6793 = sampleServer(6793, "cdn.example.net")
        val server443 = sampleServer(443, "ovv.pelqexa.ir")
        val initialList = listOf(server2053, server6793, server443)

        val firstPass = XhttpConfigModifier.modifyAll(initialList, DownloadSettingsConfig.DEFAULT)
        val secondPass = XhttpConfigModifier.modifyAll(firstPass, DownloadSettingsConfig.DEFAULT)

        assertEquals(firstPass.size, secondPass.size)
        for (i in firstPass.indices) {
            assertEquals("Idempotency failed at index $i: isXhttpModified mismatch", firstPass[i].isXhttpModified, secondPass[i].isXhttpModified)
            assertEquals("Idempotency failed at index $i: mode mismatch", firstPass[i].mode, secondPass[i].mode)
            assertEquals("Idempotency failed at index $i: xPaddingBytes mismatch", firstPass[i].xPaddingBytes, secondPass[i].xPaddingBytes)
            assertEquals("Idempotency failed at index $i: downloadSettings mismatch", firstPass[i].downloadSettings, secondPass[i].downloadSettings)
            assertEquals("Idempotency failed at index $i: rawUri mismatch", firstPass[i].rawUri, secondPass[i].rawUri)
        }
    }
}
