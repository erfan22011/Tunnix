package com.tunnix.parser

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class Base64HelperTest {

    @Test
    fun `test standard base64 decoding`() {
        val original = "vless://test@1.1.1.1:443\r\ntrojan://pass@2.2.2.2:443"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray(StandardCharsets.UTF_8))

        val decoded = Base64Helper.decodeToString(encoded)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `test base64 with missing padding and newlines`() {
        val original = "vless://uuid@host.com:2053?type=xhttp#Server"
        val encodedWithMissingPadding = Base64.getEncoder()
            .encodeToString(original.toByteArray(StandardCharsets.UTF_8))
            .replace("=", "")

        val decoded = Base64Helper.decodeToString("\n  $encodedWithMissingPadding \r\n")
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `test invalid base64 returns null`() {
        val invalid = "!!!not-valid-base64-at-all!!!"
        val decoded = Base64Helper.decodeToString(invalid)
        assertNull(decoded)
    }
}
