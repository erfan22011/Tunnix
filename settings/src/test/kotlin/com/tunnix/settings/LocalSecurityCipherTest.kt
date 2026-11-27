package com.tunnix.settings

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class LocalSecurityCipherTest {

    private val testKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

    @Before
    fun setUp() {
        LocalSecurityCipher.testKeyOverride = testKey
    }

    @After
    fun tearDown() {
        LocalSecurityCipher.testKeyOverride = null
    }

    @Test
    fun `test encrypt and decrypt round trip`() {
        val original = "https://sb.tootcloud.shop:2096/sub/secret_token_123"
        val encrypted = LocalSecurityCipher.encrypt(original)

        assertTrue("Encrypted output must start with ENC:GCM:", encrypted.startsWith(LocalSecurityCipher.ENC_PREFIX))
        assertNotEquals("Encrypted text must not equal plaintext", original, encrypted)

        val decrypted = LocalSecurityCipher.decrypt(encrypted)
        assertEquals("Decrypted text must match original", original, decrypted)
    }

    @Test(expected = SecurityException::class)
    fun `test legacy unencrypted data without allowLegacy flag throws SecurityException (fail closed)`() {
        val legacyUrl = "https://sb.tootcloud.shop:2096/sub/legacy_token"
        LocalSecurityCipher.decrypt(legacyUrl, allowLegacy = false)
    }

    @Test
    fun `test legacy unencrypted data with allowLegacy flag returns plaintext for migration`() {
        val legacyUrl = "https://sb.tootcloud.shop:2096/sub/legacy_token"
        val decrypted = LocalSecurityCipher.decrypt(legacyUrl, allowLegacy = true)
        assertEquals("Legacy plaintext must pass through only when allowLegacy is true", legacyUrl, decrypted)
    }

    @Test(expected = SecurityException::class)
    fun `test tampered ciphertext throws SecurityException (fail closed)`() {
        val original = "sensitive data"
        val encrypted = LocalSecurityCipher.encrypt(original)
        // Corrupt the last character
        val tampered = encrypted.dropLast(2) + "=="
        LocalSecurityCipher.decrypt(tampered)
    }

    @Test(expected = SecurityException::class)
    fun `test empty KeyStore throws SecurityException when no key available`() {
        LocalSecurityCipher.testKeyOverride = null
        // On desktop JVM, AndroidKeyStore is not installed, must throw SecurityException
        LocalSecurityCipher.encrypt("test")
    }
}
