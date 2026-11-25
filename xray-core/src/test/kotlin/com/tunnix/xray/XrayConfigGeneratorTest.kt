package com.tunnix.xray

import com.google.gson.JsonParser
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.DownloadTlsSettings
import com.tunnix.core.models.DownloadXhttpSettings
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import org.junit.Assert.*
import org.junit.Test

class XrayConfigGeneratorTest {

    @Test
    fun `test generating full Xray JSON for VLESS XHTTP with asymmetric downloadSettings`() {
        val downloadConfig = DownloadSettingsConfig(
            address = "ipapp.genus.ir",
            port = 2053,
            network = "xhttp",
            security = "tls",
            tlsSettings = DownloadTlsSettings(
                serverName = "ovapp.genus.ir",
                alpn = listOf("h2"),
                fingerprint = "chrome"
            ),
            xhttpSettings = DownloadXhttpSettings(
                host = "ovapp.genus.ir",
                path = "/xhttp"
            ),
            xPaddingBytes = "100-1000",
            mode = "stream-up"
        )

        val server = ServerConfig(
            id = "server-1",
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
            mode = "stream-up",
            downloadSettings = downloadConfig
        )

        val jsonString = XrayConfigGenerator.generate(server, customDownloadSettings = downloadConfig)
        assertNotNull(jsonString)

        val root = JsonParser.parseString(jsonString).asJsonObject
        assertTrue(root.has("inbounds"))
        assertTrue(root.has("outbounds"))
        assertTrue(root.has("dns"))
        assertTrue(root.has("routing"))
        assertTrue(root.has("policy"))
        assertTrue(root.has("stats"))

        // Inspect Proxy Outbound
        val outbounds = root.getAsJsonArray("outbounds")
        val proxy = outbounds.first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
        assertEquals("vless", proxy.get("protocol").asString)

        // StreamSettings
        val streamSettings = proxy.getAsJsonObject("streamSettings")
        assertEquals("xhttp", streamSettings.get("network").asString)
        assertEquals("tls", streamSettings.get("security").asString)

        // XHTTP Settings
        val xhttpSettings = streamSettings.getAsJsonObject("xhttpSettings")
        assertEquals("ovv.genus.ir", xhttpSettings.get("host").asString)
        assertEquals("/xhttp", xhttpSettings.get("path").asString)
        assertEquals("stream-up", xhttpSettings.get("mode").asString)
        assertEquals("100-1000", xhttpSettings.getAsJsonObject("extra").get("xPaddingBytes").asString)

        val extraDl = xhttpSettings.getAsJsonObject("extra").getAsJsonObject("downloadSettings")
        assertNotNull("downloadSettings must be inside extra for Xray-core", extraDl)
        assertEquals("ipapp.genus.ir", extraDl.get("address").asString)
        assertEquals(2053, extraDl.get("port").asInt)

        // Asymmetric DownloadSettings
        val dlSettings = streamSettings.getAsJsonObject("downloadSettings")
        assertNotNull("downloadSettings must be present in streamSettings", dlSettings)
        assertEquals("ipapp.genus.ir", dlSettings.get("address").asString)
        assertEquals(2053, dlSettings.get("port").asInt)
        assertEquals("xhttp", dlSettings.get("network").asString)
        assertEquals("tls", dlSettings.get("security").asString)
        assertEquals("ovapp.genus.ir", dlSettings.getAsJsonObject("tlsSettings").get("serverName").asString)
        assertEquals("ovapp.genus.ir", dlSettings.getAsJsonObject("xhttpSettings").get("host").asString)
        assertEquals("/xhttp", dlSettings.getAsJsonObject("xhttpSettings").get("path").asString)
    }

    @Test
    fun `test generating Trojan configuration`() {
        val server = ServerConfig(
            id = "trojan-1",
            name = "Trojan Server",
            address = "trojan.genus.ir",
            port = 443,
            protocol = ProtocolType.TROJAN,
            network = NetworkType.TCP,
            security = "tls",
            uuid = "mypassword"
        )

        val jsonString = XrayConfigGenerator.generate(server)
        val root = JsonParser.parseString(jsonString).asJsonObject
        val outbounds = root.getAsJsonArray("outbounds")
        val proxy = outbounds.first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject

        assertEquals("trojan", proxy.get("protocol").asString)
        val serversArray = proxy.getAsJsonObject("settings").getAsJsonArray("servers")
        assertEquals("mypassword", serversArray[0].asJsonObject.get("password").asString)
    }
}
