package com.tunnix.core.models

import java.io.Serializable

enum class ProtocolType {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    UNKNOWN
}

enum class NetworkType {
    XHTTP,
    WS,
    TCP,
    GRPC,
    HTTP,
    UNKNOWN
}

data class ServerConfig(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val protocol: ProtocolType,
    val network: NetworkType = NetworkType.UNKNOWN,
    val security: String = "none",
    val uuid: String = "",
    val sni: String = "",
    val host: String = "",
    val path: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val flow: String = "",
    val encryption: String = "none",
    val mode: String = "auto",
    val xPaddingBytes: String = "",
    val downloadSettings: DownloadSettingsConfig? = null,
    val extraJson: String = "",
    val rawUri: String = "",
    val originalUri: String = "",
    val isXhttpModified: Boolean = false,
    val queryParams: Map<String, String> = emptyMap(),
    val pingMs: Long = -1L,
    val countryCode: String = "UN",
    val isSelected: Boolean = false
) : Serializable
