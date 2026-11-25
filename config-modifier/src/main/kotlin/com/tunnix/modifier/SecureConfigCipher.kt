package com.tunnix.modifier

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides AES-CBC encryption and decryption with obfuscated key derivation
 * to protect sensitive backend configuration addresses from APK inspection.
 */
object SecureConfigCipher {

    // Obfuscated seed components combined at runtime to prevent static strings matching
    private val SEED_PARTS = listOf(
        "Tx", "2026", "Sec", "Core", "Xhttp", "Genus", "Stream"
    )

    private fun deriveKey(): SecretKeySpec {
        val seed = SEED_PARTS.joinToString("#")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(seed.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes.copyOf(16), "AES") // AES-128 key
    }

    private fun deriveIv(): IvParameterSpec {
        val ivSeed = "Tunnix#Iv#2026"
        val md5 = MessageDigest.getInstance("MD5")
        val ivBytes = md5.digest(ivSeed.toByteArray(StandardCharsets.UTF_8))
        return IvParameterSpec(ivBytes)
    }

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), deriveIv())
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            java.util.Base64.getEncoder().encodeToString(encrypted)
        } catch (_: Exception) {
            plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val clean = encryptedText.trim()
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(), deriveIv())
            val decodedBytes = java.util.Base64.getDecoder().decode(clean)
            val decrypted = cipher.doFinal(decodedBytes)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            encryptedText
        }
    }
}
