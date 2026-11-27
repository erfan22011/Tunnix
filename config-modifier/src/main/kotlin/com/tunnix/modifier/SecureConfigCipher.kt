package com.tunnix.modifier

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides authenticated AES-GCM encryption with dynamic random IVs and controlled error handling.
 * Eliminates static IVs, insecure CBC padding, and silent plaintext fallbacks.
 *
 * NOTE ON SECURITY BOUNDARY:
 * This class provides obfuscation and anti-scraping protection for sensitive in-memory assets.
 * As symmetric keys derived from hardcoded seeds reside within the client binary, this does not
 * constitute a hardware-backed security boundary or zero-knowledge encryption against determined
 * local reverse engineers.
 */
object SecureConfigCipher {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    const val GCM_PREFIX = "GCM:"

    private val SEED_PARTS = listOf(
        "Tx", "2026", "Sec", "Core", "Xhttp", "Genus", "Stream"
    )

    private fun deriveKey(): SecretKeySpec {
        val seed = SEED_PARTS.joinToString("#")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES") // AES-256 key
    }

    /**
     * Encrypts plaintext using Authenticated AES-GCM with a fresh random 12-byte IV for every invocation.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), spec)
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        // Prepend IV to ciphertext
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        val b64 = Base64.getEncoder().encodeToString(combined)
        return "$GCM_PREFIX$b64"
    }

    /**
     * Decrypts ciphertext and verifies GCM authentication tag.
     * Throws controlled IllegalArgumentException / SecurityException on failure instead of silently returning raw input.
     */
    fun decrypt(encryptedText: String): String {
        val clean = encryptedText.trim()
        if (clean.isEmpty()) return ""

        if (!clean.startsWith(GCM_PREFIX)) {
            // Check if legacy unencrypted or legacy format
            throw IllegalArgumentException("Invalid ciphertext format: missing authenticated prefix")
        }

        val b64 = clean.removePrefix(GCM_PREFIX)
        val combined = try {
            Base64.getDecoder().decode(b64)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed Base64 payload in ciphertext", e)
        }

        if (combined.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Ciphertext payload too short")
        }

        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherBytes = ByteArray(combined.size - GCM_IV_LENGTH)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), spec)

        val decrypted = try {
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            throw SecurityException("Decryption authentication failed: corrupted or tampered data", e)
        }

        return String(decrypted, StandardCharsets.UTF_8)
    }
}
