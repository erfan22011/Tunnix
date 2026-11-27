package com.tunnix.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying LocalSecurityCipher on real AndroidKeyStore hardware/emulator.
 * Verifies that AndroidKeyStore automatically generates IV without throwing
 * 'InvalidAlgorithmParameterException: Caller-provided IV not permitted'.
 */
@RunWith(AndroidJUnit4::class)
class LocalSecurityCipherAndroidTest {

    @Before
    fun setUp() {
        LocalSecurityCipher.testKeyOverride = null // Ensure real AndroidKeyStore is used
    }

    @After
    fun tearDown() {
        LocalSecurityCipher.testKeyOverride = null
    }

    @Test
    fun testRealAndroidKeyStoreEncryptAndDecryptRoundTrip() {
        val original = "https://sb.tootcloud.shop:2096/sub/real_device_token"
        val encrypted = LocalSecurityCipher.encrypt(original)

        assertNotNull(encrypted)
        assertTrue(encrypted.startsWith(LocalSecurityCipher.ENC_PREFIX))
        assertNotEquals(original, encrypted)

        val decrypted = LocalSecurityCipher.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun testRealAndroidKeyStoreMultipleEncryptionsProduceDifferentIVs() {
        val original = "same_plaintext"
        val enc1 = LocalSecurityCipher.encrypt(original)
        val enc2 = LocalSecurityCipher.encrypt(original)

        assertNotEquals("Encryptions must have distinct random IVs", enc1, enc2)
        assertEquals(original, LocalSecurityCipher.decrypt(enc1))
        assertEquals(original, LocalSecurityCipher.decrypt(enc2))
    }

    @Test(expected = SecurityException::class)
    fun testRealAndroidKeyStoreTamperedDataThrowsSecurityException() {
        val original = "sensitive_data"
        val encrypted = LocalSecurityCipher.encrypt(original)
        val tampered = encrypted.dropLast(2) + "=="
        LocalSecurityCipher.decrypt(tampered)
    }
}
