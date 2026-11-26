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
) : Serializable {

    val displayName: String
        get() {
            val rawName = name.trim()
            val fallback = if (countryCode.isNotBlank() && countryCode != "UN") "Server $countryCode" else "Tunnix Server"
            if (rawName.isBlank()) {
                return fallback
            }

            // Pattern for matching IPv4 addresses and fully-qualified domain names with optional port
            val ipOrDomainRegex = "(?:\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,})(?::\\d+)?"

            // 1. Exact match of IP or domain (e.g. "ir.jorqino.ir", "1.2.3.4", "1.2.3.4:443")
            if (rawName.matches(Regex("^$ipOrDomainRegex$"))) {
                return fallback
            }

            // 2. Strip bracketed or parenthesized domain or IP in any position:
            // e.g. "(ovv.pelqexa.ir) Tunnel", "Tunnel (ovv.pelqexa.ir)", "[185.120.45.2] Server"
            // Does NOT strip valid remarks like "(clone)", "(VIP)", "(US)" since they contain no dots or domain TLD.
            var s = rawName.replace(Regex("[\\[\\(]\\s*$ipOrDomainRegex\\s*[\\]\\)]"), "")

            // 3. Strip domain or IP delimited by whitespace, hyphen, or pipe in leading, trailing, or middle positions:
            // e.g. "Tunnel - ovv.pelqexa.ir", "185.120.45.2 Germany", "Germany 185.120.45.2 Fast"
            s = s.replace(Regex("(?:\\s*[-|]\\s*)?$ipOrDomainRegex(?:\\s*[-|]\\s*)?"), " ")

            // 4. Clean up remaining excess whitespace or edge separators
            s = s.replace(Regex("\\s+"), " ").trim { it <= ' ' || it == '-' || it == '|' }

            return s.ifBlank { fallback }
        }
}
