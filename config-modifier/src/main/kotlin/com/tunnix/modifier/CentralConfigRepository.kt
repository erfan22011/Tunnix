package com.tunnix.modifier

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.tunnix.core.models.DownloadSettingsConfig

/**
 * Central repository holding the XHTTP DownloadSettings.
 * Stores the values encrypted to prevent unauthorized extraction from APK decompilation.
 * Allows easy dynamic runtime updating.
 */
object CentralConfigRepository {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Encrypted snapshot of default DownloadSettings
    // Plaintext JSON:
    // {
    //   "xPaddingBytes": "100-1000",
    //   "downloadSettings": {
    //     "address": "ipapp.genus.ir",
    //     "port": 2053,
    //     "network": "xhttp",
    //     "security": "tls",
    //     "tlsSettings": {
    //       "serverName": "ovapp.genus.ir",
    //       "alpn": ["h2"],
    //       "fingerprint": "chrome"
    //     },
    //     "xhttpSettings": {
    //       "host": "ovapp.genus.ir",
    //       "path": "/xhttp"
    //     }
    //   }
    // }
    private var encryptedConfigPayload: String = ""

    private var currentConfig: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT

    init {
        // Encrypt default configuration on initialization
        val defaultJson = gson.toJson(DownloadSettingsConfig.DEFAULT)
        encryptedConfigPayload = SecureConfigCipher.encrypt(defaultJson)
    }

    /**
     * Retrieves the active DownloadSettingsConfig.
     * Decrypts the secure payload if needed.
     */
    @Synchronized
    fun getConfig(): DownloadSettingsConfig {
        if (encryptedConfigPayload.isNotEmpty()) {
            val decryptedJson = SecureConfigCipher.decrypt(encryptedConfigPayload)
            try {
                val parsed = gson.fromJson(decryptedJson, DownloadSettingsConfig::class.java)
                if (parsed != null) {
                    currentConfig = parsed
                }
            } catch (_: Exception) {
                // fallback to currentConfig
            }
        }
        return currentConfig
    }

    /**
     * Updates the active DownloadSettingsConfig and re-encrypts the storage.
     */
    @Synchronized
    fun updateConfig(newConfig: DownloadSettingsConfig) {
        currentConfig = newConfig
        val json = gson.toJson(newConfig)
        encryptedConfigPayload = SecureConfigCipher.encrypt(json)
    }

    /**
     * Resets to initial factory defaults.
     */
    @Synchronized
    fun resetToDefault() {
        updateConfig(DownloadSettingsConfig.DEFAULT)
    }

    /**
     * Returns the encrypted string representation.
     */
    fun getEncryptedPayload(): String = encryptedConfigPayload
}
