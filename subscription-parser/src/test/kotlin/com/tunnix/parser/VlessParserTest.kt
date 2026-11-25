package com.tunnix.parser

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import org.junit.Assert.*
import org.junit.Test

class VlessParserTest {

    @Test
    fun `test user sample VLESS XHTTP URI parsing with full fidelity`() {
        val sampleUri = "vless://03fa381a-21f2-4b75-9b6e-a4595d62106e@ovv.pelqexa.ir:2053?mode=auto&path=%2Fxhttp&security=tls&alpn=h2%2Chttp%2F1.1&encryption=none&extra=%7B%0A%20%20%22xPaddingBytes%22%3A%20%22100-1000%22%2C%0A%20%20%22downloadSettings%22%3A%20%7B%0A%20%20%20%20%22address%22%3A%20%22gama.uploadtg.ir%22%2C%0A%20%20%20%20%22port%22%3A%202053%2C%0A%20%20%20%20%22network%22%3A%20%22xhttp%22%2C%0A%20%20%20%20%22security%22%3A%20%22tls%22%2C%0A%20%20%20%20%22tlsSettings%22%3A%20%7B%0A%20%20%20%20%20%20%22serverName%22%3A%20%22ovv.genus.ir%22%2C%0A%20%20%20%20%20%20%22alpn%22%3A%20%5B%22h2%22%5D%2C%0A%20%20%20%20%20%20%22fingerprint%22%3A%20%22chrome%22%0A%20%20%20%20%7D%2C%0A%20%20%20%20%22xhttpSettings%22%3A%20%7B%0A%20%20%20%20%20%20%22host%22%3A%20%22ovv.genus.ir%22%2C%0A%20%20%20%20%20%20%22path%22%3A%20%22%2Fxhttp%22%0A%20%20%20%20%7D%0A%20%20%7D%0A%7D&insecure=0&host=ovv.genus.ir&fp=chrome&type=xhttp&allowInsecure=0&sni=ovv.genus.ir#DIRECT-CDN-Cdn"

        val server = VlessParser.parse(sampleUri)
        assertNotNull("Server must parse successfully", server)

        assertEquals("03fa381a-21f2-4b75-9b6e-a4595d62106e", server!!.uuid)
        assertEquals("ovv.pelqexa.ir", server.address)
        assertEquals(2053, server.port)
        assertEquals(ProtocolType.VLESS, server.protocol)
        assertEquals(NetworkType.XHTTP, server.network)
        assertEquals("tls", server.security)
        assertEquals("ovv.genus.ir", server.sni)
        assertEquals("ovv.genus.ir", server.host)
        assertEquals("/xhttp", server.path)
        assertEquals("chrome", server.fingerprint)
        assertEquals("DIRECT-CDN-Cdn", server.name)
        assertEquals("none", server.encryption)

        // Verify extra parameter is preserved and non-empty
        assertTrue("extraJson must be preserved", server.extraJson.contains("xPaddingBytes"))
        assertTrue("downloadSettings must be inside extraJson", server.extraJson.contains("downloadSettings"))

        // Verify all query parameters are preserved
        assertEquals("0", server.queryParams["insecure"])
        assertEquals("0", server.queryParams["allowInsecure"])
        assertEquals("auto", server.queryParams["mode"])
    }

    @Test
    fun `test standard VLESS ws URI parsing`() {
        val uri = "vless://uuid-1234@server.com:443?type=ws&security=tls&sni=server.com&path=%2Fws#MyServer"
        val server = VlessParser.parse(uri)
        assertNotNull(server)
        assertEquals("uuid-1234", server!!.uuid)
        assertEquals("server.com", server.address)
        assertEquals(443, server.port)
        assertEquals(NetworkType.WS, server.network)
        assertEquals("/ws", server.path)
        assertEquals("MyServer", server.name)
    }
}
