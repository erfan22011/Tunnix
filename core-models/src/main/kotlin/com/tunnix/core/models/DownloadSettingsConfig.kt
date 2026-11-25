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
    val address: String = "ipapp.genus.ir",
    val port: Int = 2053,
    val network: String = "xhttp",
    val security: String = "tls",
    val tlsSettings: DownloadTlsSettings = DownloadTlsSettings(),
    val xhttpSettings: DownloadXhttpSettings = DownloadXhttpSettings(),
    val xPaddingBytes: String = "100-1000",
    val mode: String = "stream-up"
) : Serializable {
    companion object {
        val DEFAULT = DownloadSettingsConfig()
    }
}
