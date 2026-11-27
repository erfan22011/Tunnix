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
        assertTrue(encrypted.startsWith("GCM:"))

        val decrypted = SecureConfigCipher.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `test dynamic random IV produces distinct ciphertexts for identical plaintext`() {
        val original = """{"address":"ipapp.genus.ir","port":2053}"""
        val enc1 = SecureConfigCipher.encrypt(original)
        val enc2 = SecureConfigCipher.encrypt(original)
        assertNotEquals(enc1, enc2) // Static IV vulnerability eliminated!
        assertEquals(original, SecureConfigCipher.decrypt(enc1))
        assertEquals(original, SecureConfigCipher.decrypt(enc2))
    }

    @Test(expected = SecurityException::class)
    fun `test tampered ciphertext triggers controlled authentication failure`() {
        val original = "secret_config_payload"
        val encrypted = SecureConfigCipher.encrypt(original)
        // Corrupt a byte in Base64 payload
        val b64Part = encrypted.removePrefix("GCM:")
        val tamperedBytes = java.util.Base64.getDecoder().decode(b64Part)
        tamperedBytes[tamperedBytes.size - 1] = (tamperedBytes[tamperedBytes.size - 1].toInt() xor 0xFF).toByte()
        val tamperedStr = "GCM:" + java.util.Base64.getEncoder().encodeToString(tamperedBytes)

        // Must throw SecurityException rather than silently returning corrupted text
        SecureConfigCipher.decrypt(tamperedStr)
    }

    @Test
    fun `test central config repository retrieval and update`() {
        val config = CentralConfigRepository.getConfig()
        assertEquals("2053ipapp.genus.ir", config.address)
        assertEquals("2053ovapp.genus.ir", config.tlsSettings.serverName)
        assertEquals("2053ovapp.genus.ir", config.xhttpSettings.host)
        assertEquals("stream-up", config.mode)
        assertEquals("100-1000", config.xPaddingBytes)

        // Test update
        val updated = config.copy(address = "new.genus.ir")
        CentralConfigRepository.updateConfig(updated)
        assertEquals("new.genus.ir", CentralConfigRepository.getConfig().address)

        // Reset
        CentralConfigRepository.resetToDefault()
        assertEquals("2053ipapp.genus.ir", CentralConfigRepository.getConfig().address)
    }
}
