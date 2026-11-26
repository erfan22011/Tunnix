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
            address = "2053ipapp.genus.ir",
            port = 2053,
            network = "xhttp",
            security = "tls",
            tlsSettings = DownloadTlsSettings(
                serverName = "2053ovapp.genus.ir",
                alpn = listOf("h2"),
                fingerprint = "chrome"
            ),
            xhttpSettings = DownloadXhttpSettings(
                host = "2053ovapp.genus.ir",
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
        assertEquals("2053ipapp.genus.ir", extraDl.get("address").asString)
        assertEquals(2053, extraDl.get("port").asInt)

        // Asymmetric DownloadSettings
        val dlSettings = streamSettings.getAsJsonObject("downloadSettings")
        assertNotNull("downloadSettings must be present in streamSettings", dlSettings)
        assertEquals("2053ipapp.genus.ir", dlSettings.get("address").asString)
        assertEquals(2053, dlSettings.get("port").asInt)
        assertEquals("xhttp", dlSettings.get("network").asString)
        assertEquals("tls", dlSettings.get("security").asString)
        assertEquals("2053ovapp.genus.ir", dlSettings.getAsJsonObject("tlsSettings").get("serverName").asString)
        assertEquals("2053ovapp.genus.ir", dlSettings.getAsJsonObject("xhttpSettings").get("host").asString)
        assertEquals("/xhttp", dlSettings.getAsJsonObject("xhttpSettings").get("path").asString)
    }

    @Test
    fun `test generating Shadowsocks configuration`() {
        val server = ServerConfig(
            id = "ss-1",
            name = "Shadowsocks Server",
            address = "ir.jorqino.ir",
            port = 443,
            protocol = ProtocolType.SHADOWSOCKS,
            network = NetworkType.TCP,
            security = "none",
            uuid = "n1r8bavu8028p6bz",
            encryption = "chacha20-ietf-poly1305"
        )

        val jsonString = XrayConfigGenerator.generate(server)
        val root = JsonParser.parseString(jsonString).asJsonObject
        val outbounds = root.getAsJsonArray("outbounds")
        val proxy = outbounds.first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject

        assertEquals("shadowsocks", proxy.get("protocol").asString)
        val serversArray = proxy.getAsJsonObject("settings").getAsJsonArray("servers")
        val s = serversArray[0].asJsonObject
        assertEquals("ir.jorqino.ir", s.get("address").asString)
        assertEquals(443, s.get("port").asInt)
        assertEquals("chacha20-ietf-poly1305", s.get("method").asString)
        assertEquals("n1r8bavu8028p6bz", s.get("password").asString)
        assertTrue(s.get("uot").asBoolean)
    }

    @Test
    fun `test generating VLESS TCP HTTP header configuration`() {
        val server = ServerConfig(
            id = "tcp-http-1",
            name = "TCP HTTP Server",
            address = "ir.jorqino.ir",
            port = 442,
            protocol = ProtocolType.VLESS,
            network = NetworkType.TCP,
            security = "tls",
            uuid = "1cd96b76-bf6c-439a-9229-6716d22ff382",
            host = "myket.ir",
            path = "/",
            queryParams = mapOf("headerType" to "http", "host" to "myket.ir")
        )

        val jsonString = XrayConfigGenerator.generate(server)
        val root = JsonParser.parseString(jsonString).asJsonObject
        val proxy = root.getAsJsonArray("outbounds").first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
        val streamSettings = proxy.getAsJsonObject("streamSettings")
        assertEquals("tcp", streamSettings.get("network").asString)
        assertTrue("Must contain tcpSettings", streamSettings.has("tcpSettings"))
        val tcpSettings = streamSettings.getAsJsonObject("tcpSettings")
        val header = tcpSettings.getAsJsonObject("header")
        assertEquals("http", header.get("type").asString)
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

    @Test
    fun `test bypassIran routing rules without external dat files and AsIs domainStrategy`() {
        val server = ServerConfig(
            id = "test-iran-bypass",
            name = "Iran Direct Server",
            address = "ovv.pelqexa.ir",
            port = 2096,
            protocol = ProtocolType.VLESS,
            network = NetworkType.TCP,
            security = "tls",
            uuid = "03fa381a-21f2-4b75-9b6e-a4595d62106e"
        )

        val jsonString = XrayConfigGenerator.generate(server, bypassIran = true)
        val root = JsonParser.parseString(jsonString).asJsonObject

        val routing = root.getAsJsonObject("routing")
        assertEquals("domainStrategy must be AsIs to prevent UDP 53 blocking", "AsIs", routing.get("domainStrategy").asString)

        val rules = routing.getAsJsonArray("rules")
        var hasGeositeOrGeoip = false
        var hasDirectIranDomains = false
        var hasProxyExemptRule = false

        for (elem in rules) {
            val rule = elem.asJsonObject
            val outbound = rule.get("outboundTag").asString

            if (rule.has("domain")) {
                val dList = rule.getAsJsonArray("domain").map { it.asString }
                if (dList.any { it.contains("geosite") || it.contains("geoip") }) {
                    hasGeositeOrGeoip = true
                }
                if (outbound == "direct" && dList.contains("regexp:.*\\.ir$") && dList.contains("domain:ir")) {
                    hasDirectIranDomains = true
                }
                if (outbound == "proxy" && dList.any { it.contains("ovv.pelqexa.ir") }) {
                    hasProxyExemptRule = true
                }
            }
            if (rule.has("ip")) {
                val ipList = rule.getAsJsonArray("ip").map { it.asString }
                if (ipList.any { it.contains("geoip") || it.contains("geosite") }) {
                    hasGeositeOrGeoip = true
                }
            }
        }

        assertFalse("Rules must NOT depend on missing geoip/geosite .dat files", hasGeositeOrGeoip)
        assertTrue("Iran domains must route directly via in-memory rules", hasDirectIranDomains)
        assertTrue("Server endpoint must be exempt from direct routing", hasProxyExemptRule)

        val dns = root.getAsJsonObject("dns")
        val dnsServers = dns.getAsJsonArray("servers").map { it.asString }
        assertTrue("DNS must include localhost for local resolution", dnsServers.contains("localhost"))
    }
}
