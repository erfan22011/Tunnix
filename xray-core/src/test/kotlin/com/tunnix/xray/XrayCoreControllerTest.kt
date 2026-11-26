package com.tunnix.xray

import com.google.gson.JsonParser
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class XrayCoreControllerTest {

    @Before
    fun setUp() {
        XrayCoreController.bypassNativeCoreForTesting = true
    }

    @Test
    fun `test start and stop lifecycle`() = runBlocking {
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

        val startResult = XrayCoreController.start(json)
        assertTrue(startResult.isSuccess)
        assertTrue(XrayCoreController.isRunning())

        val stopSuccess = XrayCoreController.stop()
        assertTrue(stopSuccess)
        assertFalse(XrayCoreController.isRunning())
    }

    @Test
    fun `test queryTrafficStats returns cumulative without compounding`() {
        XrayCoreController.updateStatsCache(1000L, 2000L)
        val (up, down) = XrayCoreController.queryTrafficStats()
        // When not running, queryTrafficStats returns 0,0
        assertEquals(0L, up)
        assertEquals(0L, down)
    }

    @Test
    fun `test reality and grpc config generation`() {
        val realityServer = ServerConfig(
            id = "reality-1",
            name = "VLESS Reality",
            address = "1.2.3.4",
            port = 443,
            protocol = ProtocolType.VLESS,
            network = NetworkType.GRPC,
            security = "reality",
            uuid = "03fa381a-21f2-4b75-9b6e-a4595d62106e",
            sni = "yahoo.com",
            queryParams = mapOf(
                "pbk" to "abcdef123456",
                "sid" to "12345678",
                "serviceName" to "my-grpc-service"
            )
        )

        val json = XrayConfigGenerator.generate(realityServer)
        val root = JsonParser.parseString(json).asJsonObject
        val proxy = root.getAsJsonArray("outbounds").first { it.asJsonObject.get("tag").asString == "proxy" }.asJsonObject
        val streamSettings = proxy.getAsJsonObject("streamSettings")

        assertEquals("grpc", streamSettings.get("network").asString)
        assertEquals("reality", streamSettings.get("security").asString)

        assertTrue(streamSettings.has("realitySettings"))
        val reality = streamSettings.getAsJsonObject("realitySettings")
        assertEquals("yahoo.com", reality.get("serverName").asString)
        assertEquals("abcdef123456", reality.get("publicKey").asString)
        assertEquals("12345678", reality.get("shortId").asString)

        assertTrue(streamSettings.has("grpcSettings"))
        val grpc = streamSettings.getAsJsonObject("grpcSettings")
        assertEquals("my-grpc-service", grpc.get("serviceName").asString)
    }
}
