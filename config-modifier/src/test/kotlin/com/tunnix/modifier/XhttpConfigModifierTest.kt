package com.tunnix.modifier

import com.google.gson.JsonParser
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import org.junit.Assert.*
import org.junit.Test

class XhttpConfigModifierTest {

    private fun createSampleVlessXhttp(): ServerConfig {
        return ServerConfig(
            id = "test-vless-xhttp",
            name = "DIRECT-CDN-Cdn",
            address = "ovv.pelqexa.ir",
            port = 2053,
            protocol = ProtocolType.VLESS,
            network = NetworkType.XHTTP,
            security = "tls",
            uuid = "03fa381a-21f2-4b75-9b6e-a4595d62106e",
            sni = "ovv.genus.ir",
            host = "ovv.genus.ir",
            path = "/xhttp",
            alpn = "h2,http/1.1",
            fingerprint = "chrome",
            flow = "xtls-rprx-vision",
            encryption = "none",
            mode = "auto",
            extraJson = """{"existingCustomKey":"preservedValue"}""",
            queryParams = mapOf(
                "type" to "xhttp",
                "mode" to "auto",
                "path" to "/xhttp",
                "security" to "tls",
                "alpn" to "h2,http/1.1",
                "encryption" to "none",
                "host" to "ovv.genus.ir",
                "fp" to "chrome",
                "sni" to "ovv.genus.ir",
                "flow" to "xtls-rprx-vision",
                "insecure" to "0",
                "allowInsecure" to "0",
                "unknownParam" to "magicValue123"
            ),
            rawUri = "vless://03fa381a-21f2-4b75-9b6e-a4595d62106e@ovv.pelqexa.ir:2053?mode=auto#DIRECT-CDN-Cdn",
            originalUri = "vless://03fa381a-21f2-4b75-9b6e-a4595d62106e@ovv.pelqexa.ir:2053?mode=auto#DIRECT-CDN-Cdn"
        )
    }

    @Test
    fun `test VLESS XHTTP modification applies downloadSettings and stream-up`() {
        val original = createSampleVlessXhttp()
        val modified = XhttpConfigModifier.modify(original)

        assertTrue("Must be marked as modified", modified.isXhttpModified)
        assertEquals("Mode must be set to stream-up", "stream-up", modified.mode)
        assertEquals("xPaddingBytes must be set", "100-1000", modified.xPaddingBytes)
        assertNotNull("downloadSettings must be populated", modified.downloadSettings)

        // Verify specific downloadSettings values requested by user
        assertEquals("2053ipapp.genus.ir", modified.downloadSettings!!.address)
        assertEquals(2053, modified.downloadSettings!!.port)
        assertEquals("2053ovapp.genus.ir", modified.downloadSettings!!.tlsSettings.serverName)
        assertEquals("2053ovapp.genus.ir", modified.downloadSettings!!.xhttpSettings.host)
        assertEquals("/xhttp", modified.downloadSettings!!.xhttpSettings.path)
        assertEquals(listOf("h2"), modified.downloadSettings!!.tlsSettings.alpn)
        assertEquals("chrome", modified.downloadSettings!!.tlsSettings.fingerprint)
    }

    @Test
    fun `test full parameter preservation after modification`() {
        val original = createSampleVlessXhttp()
        val modified = XhttpConfigModifier.modify(original)

        // 1. UUID preserved
        assertEquals(original.uuid, modified.uuid)
        // 2. Address & Port preserved
        assertEquals(original.address, modified.address)
        assertEquals(original.port, modified.port)
        // 3. SNI & Host preserved
        assertEquals(original.sni, modified.sni)
        assertEquals(original.host, modified.host)
        // 4. Path preserved
        assertEquals(original.path, modified.path)
        // 5. ALPN preserved
        assertEquals(original.alpn, modified.alpn)
        // 6. Fingerprint preserved
        assertEquals(original.fingerprint, modified.fingerprint)
        // 7. Flow & Encryption preserved
        assertEquals(original.flow, modified.flow)
        assertEquals(original.encryption, modified.encryption)
        // 8. Name / Remark preserved
        assertEquals(original.name, modified.name)
        // 9. Unknown Query Parameters preserved
        assertEquals("0", modified.queryParams["insecure"])
        assertEquals("0", modified.queryParams["allowInsecure"])
        assertEquals("magicValue123", modified.queryParams["unknownParam"])
        // 10. Mode in query params updated
        assertEquals("stream-up", modified.queryParams["mode"])
    }

    @Test
    fun `test extra merging preserves existing keys and adds downloadSettings`() {
        val original = createSampleVlessXhttp()
        val modified = XhttpConfigModifier.modify(original)

        val extraObj = JsonParser.parseString(modified.extraJson).asJsonObject

        // Must preserve existing key
        assertTrue(extraObj.has("existingCustomKey"))
        assertEquals("preservedValue", extraObj.get("existingCustomKey").asString)

        // Must add xPaddingBytes and downloadSettings
        assertTrue(extraObj.has("xPaddingBytes"))
        assertEquals("100-1000", extraObj.get("xPaddingBytes").asString)

        assertTrue(extraObj.has("downloadSettings"))
        val dlObj = extraObj.getAsJsonObject("downloadSettings")
        assertEquals("2053ipapp.genus.ir", dlObj.get("address").asString)
        assertEquals(2053, dlObj.get("port").asInt)
    }

    @Test
    fun `test non-XHTTP configs remain completely untouched`() {
        // VLESS WS on port 2053
        val vlessWs2053 = ServerConfig(
            id = "vless-ws-2053",
            name = "VLESS WS 2053",
            address = "ws.com",
            port = 2053,
            protocol = ProtocolType.VLESS,
            network = NetworkType.WS,
            rawUri = "vless://uuid@ws.com:2053?type=ws"
        )
        assertFalse("VLESS WS on port 2053 must NOT be eligible for XHTTP modification", XhttpConfigModifier.isEligible(vlessWs2053))
        val modWs = XhttpConfigModifier.modify(vlessWs2053)
        assertFalse(modWs.isXhttpModified)
        assertEquals(vlessWs2053.rawUri, modWs.rawUri)

        // VLESS TCP on port 2053
        val vlessTcp2053 = ServerConfig(
            id = "vless-tcp-2053",
            name = "VLESS TCP 2053",
            address = "tcp.com",
            port = 2053,
            protocol = ProtocolType.VLESS,
            network = NetworkType.TCP,
            rawUri = "vless://uuid@tcp.com:2053?type=tcp"
        )
        assertFalse("VLESS TCP on port 2053 must NOT be eligible for XHTTP modification", XhttpConfigModifier.isEligible(vlessTcp2053))
        val modTcp = XhttpConfigModifier.modify(vlessTcp2053)
        assertFalse(modTcp.isXhttpModified)
        assertEquals(vlessTcp2053.rawUri, modTcp.rawUri)

        // 1. Trojan Server
        val trojan = ServerConfig(
            id = "trojan-1",
            name = "Trojan Server",
            address = "trojan.com",
            port = 443,
            protocol = ProtocolType.TROJAN,
            network = NetworkType.TCP,
            rawUri = "trojan://pass@trojan.com:443"
        )
        val modTrojan = XhttpConfigModifier.modify(trojan)
        assertFalse(modTrojan.isXhttpModified)
        assertEquals(trojan.rawUri, modTrojan.rawUri)

        // 2. VMess Server
        val vmess = ServerConfig(
            id = "vmess-1",
            name = "VMess Server",
            address = "vmess.com",
            port = 443,
            protocol = ProtocolType.VMESS,
            network = NetworkType.WS,
            rawUri = "vmess://eyJhZGQiOiJ2bWVzcy5jb20ifQ=="
        )
        val modVmess = XhttpConfigModifier.modify(vmess)
        assertFalse(modVmess.isXhttpModified)
        assertEquals(vmess.rawUri, modVmess.rawUri)

        // 3. VLESS WS Server (not XHTTP)
        val vlessWs = ServerConfig(
            id = "vless-ws",
            name = "VLESS WS",
            address = "ws.com",
            port = 443,
            protocol = ProtocolType.VLESS,
            network = NetworkType.WS,
            rawUri = "vless://uuid@ws.com:443?type=ws"
        )
        val modVlessWs = XhttpConfigModifier.modify(vlessWs)
        assertFalse(modVlessWs.isXhttpModified)
        assertEquals(vlessWs.rawUri, modVlessWs.rawUri)
    }

    @Test
    fun `test modifyAll handles mixed list correctly`() {
        val list = listOf(
            createSampleVlessXhttp(),
            ServerConfig(
                id = "trojan-1",
                name = "Trojan",
                address = "t.com",
                port = 443,
                protocol = ProtocolType.TROJAN,
                network = NetworkType.TCP,
                rawUri = "trojan://pass@t.com:443"
            )
        )

        val result = XhttpConfigModifier.modifyAll(list)
        assertEquals(2, result.size)
        assertTrue("First server (VLESS XHTTP) must be modified", result[0].isXhttpModified)
        assertFalse("Second server (Trojan) must NOT be modified", result[1].isXhttpModified)
    }

    @Test
    fun `test VLESS XHTTP on different port is untouched`() {
        val serverOnPort443 = createSampleVlessXhttp().copy(port = 443)
        val result = XhttpConfigModifier.modify(serverOnPort443)
        assertFalse("Server on port 443 must NOT be modified", result.isXhttpModified)
        assertEquals(serverOnPort443.rawUri, result.rawUri)
    }

    @Test
    fun `test VLESS XHTTP when downloadSettings is disabled is untouched`() {
        val server = createSampleVlessXhttp()
        val disabledConfig = com.tunnix.core.models.DownloadSettingsConfig(isEnabled = false)
        val result = XhttpConfigModifier.modify(server, disabledConfig)
        assertFalse("Disabled config must NOT modify server", result.isXhttpModified)
        assertEquals(server.rawUri, result.rawUri)
    }

    @Test
    fun `test custom targetPort applies only to matching port`() {
        val server8443 = createSampleVlessXhttp().copy(port = 8443)
        val customConfig = com.tunnix.core.models.DownloadSettingsConfig(
            isEnabled = true,
            targetPort = 8443,
            port = 2053
        )
        val result = XhttpConfigModifier.modify(server8443, customConfig)
        assertTrue("Server on port 8443 must be modified when targetPort is 8443", result.isXhttpModified)
        assertEquals("stream-up", result.mode)
        assertNotNull(result.downloadSettings)

        // Server on port 2053 with targetPort 8443:
        val server2053 = createSampleVlessXhttp().copy(port = 2053)
        val result2053 = XhttpConfigModifier.modify(server2053, customConfig)
        assertTrue("Server on port 2053 also matches config.port fallback", result2053.isXhttpModified)
    }

    @Test
    fun `test port 6793 receives 6793 encrypted downloadSettings even when default custom is passed`() {
        val server6793 = createSampleVlessXhttp().copy(port = 6793)
        // With null custom
        val modifiedNull = XhttpConfigModifier.modify(server6793, null)
        assertTrue(modifiedNull.isXhttpModified)
        assertEquals("6793ipapp.genus.ir", modifiedNull.downloadSettings!!.address)
        assertEquals(6793, modifiedNull.downloadSettings!!.port)
        assertEquals("6793ovapp.genus.ir", modifiedNull.downloadSettings!!.tlsSettings.serverName)
        assertEquals("6793ovapp.genus.ir", modifiedNull.downloadSettings!!.xhttpSettings.host)
        assertEquals("/xhttp", modifiedNull.downloadSettings!!.xhttpSettings.path)

        // With DEFAULT custom (which has targetPort = 2053)
        val modifiedDefault = XhttpConfigModifier.modify(server6793, com.tunnix.core.models.DownloadSettingsConfig.DEFAULT)
        assertTrue("Port 6793 must be modified even when custom is DEFAULT", modifiedDefault.isXhttpModified)
        assertEquals("6793ipapp.genus.ir", modifiedDefault.downloadSettings!!.address)
        assertEquals(6793, modifiedDefault.downloadSettings!!.port)
    }

    @Test
    fun `test external public domain and IP are eligible for XHTTP modification`() {
        val publicIpServer = createSampleVlessXhttp().copy(address = "185.10.20.30")
        assertTrue("Public IP server on 2053 must be eligible", XhttpConfigModifier.isEligible(publicIpServer))
        val modIp = XhttpConfigModifier.modify(publicIpServer)
        assertTrue(modIp.isXhttpModified)

        val externalDomainServer = createSampleVlessXhttp().copy(address = "cdn.example.net")
        assertTrue("External domain server on 2053 must be eligible", XhttpConfigModifier.isEligible(externalDomainServer))
        val modDomain = XhttpConfigModifier.modify(externalDomainServer)
        assertTrue(modDomain.isXhttpModified)
    }

    @Test
    fun `test private or loopback server address is not eligible`() {
        val loopbackServer = createSampleVlessXhttp().copy(address = "127.0.0.1")
        assertFalse(XhttpConfigModifier.isEligible(loopbackServer))
        assertFalse(XhttpConfigModifier.modify(loopbackServer).isXhttpModified)

        val privateServer = createSampleVlessXhttp().copy(address = "192.168.1.1")
        assertFalse(XhttpConfigModifier.isEligible(privateServer))
        assertFalse(XhttpConfigModifier.modify(privateServer).isXhttpModified)

        val localhostServer = createSampleVlessXhttp().copy(address = "localhost")
        assertFalse(XhttpConfigModifier.isEligible(localhostServer))
        assertFalse(XhttpConfigModifier.modify(localhostServer).isXhttpModified)
    }

    @Test
    fun `test server with existing downloadSettings is overwritten by preset for idempotence`() {
        val serverWithSettings = createSampleVlessXhttp().copy(
            downloadSettings = com.tunnix.core.models.DownloadSettingsConfig(address = "old.upstream.com")
        )
        assertTrue("Server with existing downloadSettings is eligible to be updated", XhttpConfigModifier.isEligible(serverWithSettings))
        val modified = XhttpConfigModifier.modify(serverWithSettings)
        assertTrue(modified.isXhttpModified)
        assertEquals("2053ipapp.genus.ir", modified.downloadSettings!!.address)
    }
}
