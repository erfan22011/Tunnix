package com.tunnix.core.models

/**
 * In-memory obfuscation provider for proprietary asymmetric download routing parameters.
 * Provides binary string obfuscation (anti-scraping) for built-in endpoint presets.
 * Note: This provides in-memory obfuscation and does not constitute a hardware cryptographic boundary.
 */
object SecureVault {

    private const val KEY = "Tunnix2026SecureVault"

    fun decrypt(bytes: ByteArray): String {
        val keyBytes = KEY.toByteArray(Charsets.UTF_8)
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }

    // Encrypted representations
    private val ENC_2053_IP = byteArrayOf(0x66.toByte(), 0x45.toByte(), 0x5B.toByte(), 0x5D.toByte(), 0x00.toByte(), 0x08.toByte(), 0x53.toByte(), 0x40.toByte(), 0x42.toByte(), 0x18.toByte(), 0x34.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4B.toByte(), 0x3F.toByte(), 0x13.toByte())
    private val ENC_2053_OV = byteArrayOf(0x66.toByte(), 0x45.toByte(), 0x5B.toByte(), 0x5D.toByte(), 0x06.toByte(), 0x0E.toByte(), 0x53.toByte(), 0x40.toByte(), 0x42.toByte(), 0x18.toByte(), 0x34.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4B.toByte(), 0x3F.toByte(), 0x13.toByte())
    private val ENC_6793_IP = byteArrayOf(0x62.toByte(), 0x42.toByte(), 0x57.toByte(), 0x5D.toByte(), 0x00.toByte(), 0x08.toByte(), 0x53.toByte(), 0x40.toByte(), 0x42.toByte(), 0x18.toByte(), 0x34.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4B.toByte(), 0x3F.toByte(), 0x13.toByte())
    private val ENC_6793_OV = byteArrayOf(0x62.toByte(), 0x42.toByte(), 0x57.toByte(), 0x5D.toByte(), 0x06.toByte(), 0x0E.toByte(), 0x53.toByte(), 0x40.toByte(), 0x42.toByte(), 0x18.toByte(), 0x34.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x01.toByte(), 0x4B.toByte(), 0x3F.toByte(), 0x13.toByte())
    private val ENC_PATH = byteArrayOf(0x7B.toByte(), 0x0D.toByte(), 0x06.toByte(), 0x1A.toByte(), 0x1D.toByte(), 0x08.toByte())
    private val ENC_CHROME = byteArrayOf(0x37.toByte(), 0x1D.toByte(), 0x1C.toByte(), 0x01.toByte(), 0x04.toByte(), 0x1D.toByte())
    private val ENC_H2 = byteArrayOf(0x3C.toByte(), 0x47.toByte())
    private val ENC_PADDING = byteArrayOf(0x65.toByte(), 0x45.toByte(), 0x5E.toByte(), 0x43.toByte(), 0x58.toByte(), 0x48.toByte(), 0x02.toByte(), 0x00.toByte())
    private val ENC_MODE = byteArrayOf(0x27.toByte(), 0x01.toByte(), 0x1C.toByte(), 0x0B.toByte(), 0x08.toByte(), 0x15.toByte(), 0x1F.toByte(), 0x45.toByte(), 0x42.toByte())

    val PORT_2053: DownloadSettingsConfig by lazy {
        DownloadSettingsConfig(
            isEnabled = true,
            targetPort = 2053,
            address = decrypt(ENC_2053_IP),
            port = 2053,
            network = "xhttp",
            security = "tls",
            tlsSettings = DownloadTlsSettings(
                serverName = decrypt(ENC_2053_OV),
                alpn = listOf(decrypt(ENC_H2)),
                fingerprint = decrypt(ENC_CHROME)
            ),
            xhttpSettings = DownloadXhttpSettings(
                host = decrypt(ENC_2053_OV),
                path = decrypt(ENC_PATH)
            ),
            xPaddingBytes = decrypt(ENC_PADDING),
            mode = decrypt(ENC_MODE)
        )
    }

    val PORT_6793: DownloadSettingsConfig by lazy {
        DownloadSettingsConfig(
            isEnabled = true,
            targetPort = 6793,
            address = decrypt(ENC_6793_IP),
            port = 6793,
            network = "xhttp",
            security = "tls",
            tlsSettings = DownloadTlsSettings(
                serverName = decrypt(ENC_6793_OV),
                alpn = listOf(decrypt(ENC_H2)),
                fingerprint = decrypt(ENC_CHROME)
            ),
            xhttpSettings = DownloadXhttpSettings(
                host = decrypt(ENC_6793_OV),
                path = decrypt(ENC_PATH)
            ),
            xPaddingBytes = decrypt(ENC_PADDING),
            mode = decrypt(ENC_MODE)
        )
    }

    /**
     * Resolves the asymmetric download configuration for a given server port.
     * Only port 2053 and port 6793 qualify. All other ports return null.
     */
    fun getConfigForPort(targetPort: Int): DownloadSettingsConfig? {
        return when (targetPort) {
            2053 -> PORT_2053
            6793 -> PORT_6793
            else -> null
        }
    }
}
