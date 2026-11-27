package com.tunnix.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides hardware-backed authenticated AES-256-GCM encryption for sensitive local storage
 * (subscription tokens and server credentials) using AndroidKeyStore.
 *
 * Guarantees:
 * 1. Authenticated Encryption with Associated Data (AEAD) via AES/GCM/NoPadding.
 * 2. Dynamic, cryptographically secure 12-byte IV for every encryption operation.
 * 3. Fail-closed security: Throws SecurityException on encryption or authentication failures.
 * 4. Zero hardcoded secrets in repository.
 * 5. Backwards compatibility: migrates unencrypted legacy data gracefully.
 */
object LocalSecurityCipher {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "tunnix_local_master_key"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    const val ENC_PREFIX = "ENC:GCM:"

    // For unit testing environments where AndroidKeyStore service is not available
    var testKeyOverride: SecretKey? = null

    private fun getOrCreateKey(): SecretKey {
        testKeyOverride?.let { return it }

        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }

            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
                ?: (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw SecurityException("Key retrieval from AndroidKeyStore returned null")
        } catch (e: Exception) {
            throw SecurityException("AndroidKeyStore hardware security module is unavailable or failed", e)
        }
    }

    /**
     * Encrypts plaintext using Authenticated AES-256-GCM.
     * Generates a unique 12-byte IV for every call.
     * Output format: "ENC:GCM:<Base64(IV + Ciphertext + Tag)>"
     * Fail-closed: Throws SecurityException on failure.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: throw SecurityException("Cipher IV was not generated")
            require(iv.size == GCM_IV_LENGTH) { "Invalid IV length: ${iv.size}" }
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

            val b64 = Base64.getEncoder().encodeToString(combined)
            return "$ENC_PREFIX$b64"
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt sensitive data", e)
        }
    }

    /**
     * Decrypts ciphertext and authenticates GCM tag.
     * - If input starts with "ENC:GCM:", decrypts with AES-GCM; throws SecurityException on tamper.
     * - If input does NOT start with "ENC:GCM:", throws SecurityException (fail-closed)
     *   unless allowLegacy is explicitly true for migration.
     */
    fun decrypt(data: String, allowLegacy: Boolean = false): String {
        if (data.isBlank()) return ""

        // Backwards compatibility check: If unencrypted legacy string from previous versions, only return if explicitly allowing legacy
        if (!data.startsWith(ENC_PREFIX)) {
            if (allowLegacy) {
                return data
            }
            throw SecurityException("Data is unencrypted or missing secure envelope (fail-closed)")
        }

        val b64 = data.removePrefix(ENC_PREFIX).trim()
        val combined = try {
            Base64.getDecoder().decode(b64)
        } catch (e: Exception) {
            throw SecurityException("Corrupted encrypted data format", e)
        }

        if (combined.size < GCM_IV_LENGTH) {
            throw SecurityException("Encrypted payload too short")
        }

        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = try {
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            throw SecurityException("Decryption authentication failed: data corrupted or tampered", e)
        }

        return String(decryptedBytes, StandardCharsets.UTF_8)
    }
}
