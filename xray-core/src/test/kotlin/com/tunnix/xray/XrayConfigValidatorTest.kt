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
    }

    @Test
    fun `test json missing downloadSettings fails XHTTP validation`() {
        val brokenJson = """
            {
              "inbounds": [],
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

        val result = XrayConfigValidator.validate(brokenJson)
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.contains("downloadSettings") })
    }
}
