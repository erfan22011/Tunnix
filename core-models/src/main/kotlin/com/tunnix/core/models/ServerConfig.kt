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

    val isTransportUnencrypted: Boolean
        get() = (protocol == ProtocolType.VLESS || protocol == ProtocolType.VMESS || protocol == ProtocolType.TROJAN) &&
                (security.isBlank() || security.equals("none", ignoreCase = true))

    val displayName: String
        get() {
            // Anti-ReDoS & UI protection: bound rawName to at most 100 chars
            val rawName = name.trim().take(MAX_NAME_LENGTH)
            val fallback = if (countryCode.isNotBlank() && countryCode != "UN") "Server $countryCode" else "Tunnix Server"
            if (rawName.isBlank()) {
                return fallback
            }

            // 1. Exact match of IP or domain
            if (EXACT_IP_OR_DOMAIN.matches(rawName)) {
                return fallback
            }

            // 2. Strip bracketed or parenthesized domain or IP
            var s = BRACKETED_IP_OR_DOMAIN.replace(rawName, "")

            // 3. Strip domain or IP delimited by whitespace, hyphen, or pipe
            s = DELIMITED_IP_OR_DOMAIN.replace(s, " ")

            // 4. Clean up remaining excess whitespace or edge separators
            s = EXCESS_WHITESPACE.replace(s, " ").trim { it <= ' ' || it == '-' || it == '|' }

            return s.ifBlank { fallback }
        }

    companion object {
        const val MAX_NAME_LENGTH = 100
        private const val IP_OR_DOMAIN_PATTERN = "(?:\\d{1,3}(?:\\.\\d{1,3}){3}|(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,})(?::\\d+)?"
        private val EXACT_IP_OR_DOMAIN = Regex("^$IP_OR_DOMAIN_PATTERN$")
        private val BRACKETED_IP_OR_DOMAIN = Regex("[\\[\\(]\\s*$IP_OR_DOMAIN_PATTERN\\s*[\\]\\)]")
        private val DELIMITED_IP_OR_DOMAIN = Regex("(?:\\s*[-|]\\s*)?$IP_OR_DOMAIN_PATTERN(?:\\s*[-|]\\s*)?")
        private val EXCESS_WHITESPACE = Regex("\\s+")
    }
}
