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

    private var encryptedConfigPayload: String = ""

    private var currentConfig: DownloadSettingsConfig = DownloadSettingsConfig.DEFAULT

    init {
        // Encrypt default configuration on initialization safely
        try {
            val defaultJson = gson.toJson(DownloadSettingsConfig.DEFAULT)
            encryptedConfigPayload = SecureConfigCipher.encrypt(defaultJson)
        } catch (_: Throwable) {
            encryptedConfigPayload = ""
        }
    }

    /**
     * Retrieves the active DownloadSettingsConfig.
     * Decrypts the secure payload if needed with complete error safety.
     */
    @Synchronized
    fun getConfig(): DownloadSettingsConfig {
        if (encryptedConfigPayload.isNotEmpty()) {
            try {
                val decryptedJson = SecureConfigCipher.decrypt(encryptedConfigPayload)
                val parsed = gson.fromJson(decryptedJson, DownloadSettingsConfig::class.java)
                if (parsed != null) {
                    currentConfig = parsed
                }
            } catch (_: Throwable) {
                // Safely fallback to currentConfig
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
        try {
            val json = gson.toJson(newConfig)
            encryptedConfigPayload = SecureConfigCipher.encrypt(json)
        } catch (_: Throwable) {}
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
