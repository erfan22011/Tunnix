package com.tunnix.core.models

import org.junit.Assert.*
import org.junit.Test

class DownloadSettingsResolverTest {

    private fun vlessServer(port: Int): ServerConfig {
        return ServerConfig(
            id = "vless-$port",
            name = "VLESS-$port",
            address = "185.10.20.30",
            port = port,
            protocol = ProtocolType.VLESS,
            network = NetworkType.XHTTP,
            uuid = "03fa381a-21f2-4b75-9b6e-a4595d62106e"
        )
    }

    @Test
    fun `test port 2053 resolves correctly with null custom and default custom`() {
        val server = vlessServer(2053)

        val resNull = DownloadSettingsResolver.resolve(server, null)
        assertNotNull(resNull)
        assertEquals("2053ipapp.genus.ir", resNull!!.address)
        assertEquals(2053, resNull.port)

        val resDefault = DownloadSettingsResolver.resolve(server, DownloadSettingsConfig.DEFAULT)
        assertNotNull(resDefault)
        assertEquals("2053ipapp.genus.ir", resDefault!!.address)
        assertEquals(2053, resDefault.port)
    }

    @Test
    fun `test port 6793 resolves to 6793 preset even when default 2053 custom is passed`() {
        val server = vlessServer(6793)

        // When custom is DEFAULT (which defaults to port 2053), port 6793 must still resolve to 6793 preset!
        val resDefault = DownloadSettingsResolver.resolve(server, DownloadSettingsConfig.DEFAULT)
        assertNotNull("Port 6793 must NOT be null when custom is DEFAULT", resDefault)
        assertEquals("6793ipapp.genus.ir", resDefault!!.address)
        assertEquals(6793, resDefault.port)

        val resNull = DownloadSettingsResolver.resolve(server, null)
        assertNotNull(resNull)
        assertEquals("6793ipapp.genus.ir", resNull!!.address)
        assertEquals(6793, resNull.port)
    }

    @Test
    fun `test disabled custom config disables preset resolution`() {
        val server2053 = vlessServer(2053)
        val server6793 = vlessServer(6793)
        val disabledCustom = DownloadSettingsConfig(isEnabled = false)

        assertNull(DownloadSettingsResolver.resolve(server2053, disabledCustom))
        assertNull(DownloadSettingsResolver.resolve(server6793, disabledCustom))
    }

    @Test
    fun `test custom port 8443 resolves only when custom targetPort matches 8443`() {
        val server8443 = vlessServer(8443)

        // With default 2053 custom -> null
        assertNull(DownloadSettingsResolver.resolve(server8443, DownloadSettingsConfig.DEFAULT))

        // With custom targeting 8443 -> matches custom
        val custom8443 = DownloadSettingsConfig(isEnabled = true, targetPort = 8443, address = "custom.genus.ir")
        val res = DownloadSettingsResolver.resolve(server8443, custom8443)
        assertNotNull(res)
        assertEquals("custom.genus.ir", res!!.address)
    }

    @Test
    fun `test standard ports 443 and 80 resolve to null`() {
        val server443 = vlessServer(443)
        val server80 = vlessServer(80)

        assertNull(DownloadSettingsResolver.resolve(server443, null))
        assertNull(DownloadSettingsResolver.resolve(server443, DownloadSettingsConfig.DEFAULT))
        assertNull(DownloadSettingsResolver.resolve(server80, DownloadSettingsConfig.DEFAULT))
    }

    @Test
    fun `test non-VLESS protocols resolve to null`() {
        val trojan2053 = vlessServer(2053).copy(protocol = ProtocolType.TROJAN)
        val vmess6793 = vlessServer(6793).copy(protocol = ProtocolType.VMESS)
        val ss2053 = vlessServer(2053).copy(protocol = ProtocolType.SHADOWSOCKS)

        assertNull(DownloadSettingsResolver.resolve(trojan2053, DownloadSettingsConfig.DEFAULT))
        assertNull(DownloadSettingsResolver.resolve(vmess6793, DownloadSettingsConfig.DEFAULT))
        assertNull(DownloadSettingsResolver.resolve(ss2053, DownloadSettingsConfig.DEFAULT))
    }
}
