package com.tunnix.modifier

import com.tunnix.core.models.DownloadSettingsConfig
import org.junit.Assert.*
import org.junit.Test

class SecureConfigCipherTest {

    @Test
    fun `test encryption and decryption roundtrip`() {
        val original = """{"address":"ipapp.genus.ir","port":2053}"""
        val encrypted = SecureConfigCipher.encrypt(original)
        assertNotEquals(original, encrypted)
        assertFalse(encrypted.contains("ipapp.genus.ir"))

        val decrypted = SecureConfigCipher.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `test central config repository retrieval and update`() {
        val config = CentralConfigRepository.getConfig()
        assertEquals("ipapp.genus.ir", config.address)
        assertEquals("ovapp.genus.ir", config.tlsSettings.serverName)
        assertEquals("ovapp.genus.ir", config.xhttpSettings.host)
        assertEquals("stream-up", config.mode)
        assertEquals("100-1000", config.xPaddingBytes)

        // Test update
        val updated = config.copy(address = "new.genus.ir")
        CentralConfigRepository.updateConfig(updated)
        assertEquals("new.genus.ir", CentralConfigRepository.getConfig().address)

        // Reset
        CentralConfigRepository.resetToDefault()
        assertEquals("ipapp.genus.ir", CentralConfigRepository.getConfig().address)
    }
}
