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
        assertEquals("ipapp.genus.ir", modified.downloadSettings!!.address)
        assertEquals(2053, modified.downloadSettings!!.port)
        assertEquals("ovapp.genus.ir", modified.downloadSettings!!.tlsSettings.serverName)
        assertEquals("ovapp.genus.ir", modified.downloadSettings!!.xhttpSettings.host)
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
        assertEquals("ipapp.genus.ir", dlObj.get("address").asString)
        assertEquals(2053, dlObj.get("port").asInt)
    }

    @Test
    fun `test non-XHTTP configs remain completely untouched`() {
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
}
