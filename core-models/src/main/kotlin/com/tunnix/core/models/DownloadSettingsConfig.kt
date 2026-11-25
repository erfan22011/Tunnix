package com.tunnix.core.models

import java.io.Serializable

data class DownloadTlsSettings(
    val serverName: String = "ovapp.genus.ir",
    val alpn: List<String> = listOf("h2"),
    val fingerprint: String = "chrome"
) : Serializable

data class DownloadXhttpSettings(
    val host: String = "ovapp.genus.ir",
    val path: String = "/xhttp"
) : Serializable

data class DownloadSettingsConfig(
    val isEnabled: Boolean = true,
    val targetPort: Int = 2053,
    val address: String = "2053ipapp.genus.ir",
    val port: Int = 2053,
    val network: String = "xhttp",
    val security: String = "tls",
    val tlsSettings: DownloadTlsSettings = DownloadTlsSettings(serverName = "2053ovapp.genus.ir"),
    val xhttpSettings: DownloadXhttpSettings = DownloadXhttpSettings(host = "2053ovapp.genus.ir"),
    val xPaddingBytes: String = "100-1000",
    val mode: String = "stream-up"
) : Serializable {
    val effectiveTargetPort: Int
        get() = if (targetPort > 0) targetPort else (if (port > 0) port else 2053)

    companion object {
        val DEFAULT: DownloadSettingsConfig
            get() = SecureVault.PORT_2053
    }
}
