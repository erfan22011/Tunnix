package com.tunnix.xray

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class XrayCoreControllerTest {

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
}
