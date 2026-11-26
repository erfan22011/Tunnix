package com.tunnix.parser

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object ShadowsocksParser {

    /**
     * Parses a Shadowsocks (ss://) URI into a ServerConfig object.
     * Supports SIP002 (Base64 userinfo@host:port) as well as legacy base64-all formats.
     */
    fun parse(uriString: String): ServerConfig? {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("ss://", ignoreCase = true)) return null

        return try {
            val withoutScheme = trimmed.substring(5)

            // Split fragment (#remark)
            val hashIndex = withoutScheme.indexOf('#')
            val beforeHash = if (hashIndex != -1) withoutScheme.substring(0, hashIndex) else withoutScheme
            val rawRemark = if (hashIndex != -1) withoutScheme.substring(hashIndex + 1) else ""
            val remark = decodeSafely(rawRemark)

            // Split query (?params)
            val queryIndex = beforeHash.indexOf('?')
            val mainPart = if (queryIndex != -1) beforeHash.substring(0, queryIndex) else beforeHash
            val queryString = if (queryIndex != -1) beforeHash.substring(queryIndex + 1) else ""

            val queryParams = mutableMapOf<String, String>()
            if (queryString.isNotEmpty()) {
                val pairs = queryString.split('&')
                for (pair in pairs) {
                    val eqIndex = pair.indexOf('=')
                    if (eqIndex != -1) {
                        val key = decodeSafely(pair.substring(0, eqIndex))
                        val value = decodeSafely(pair.substring(eqIndex + 1))
                        queryParams[key] = value
                    } else if (pair.isNotEmpty()) {
                        queryParams[decodeSafely(pair)] = ""
                    }
                }
            }

            var method = ""
            var password = ""
            var address = ""
            var port = 0

            if (mainPart.contains('@')) {
                // Format 1: ss://[base64(method:password)]@host:port
                val atIndex = mainPart.indexOf('@')
                val rawUserInfo = mainPart.substring(0, atIndex).trim()
                val hostPort = mainPart.substring(atIndex + 1).trim()

                // Decode userinfo (may be base64 or plaintext)
                val decodedUserInfo = Base64Helper.decodeToString(rawUserInfo) ?: rawUserInfo
                val colonIdx = decodedUserInfo.indexOf(':')
                if (colonIdx != -1) {
                    method = decodedUserInfo.substring(0, colonIdx)
                    password = decodedUserInfo.substring(colonIdx + 1)
                } else {
                    password = decodedUserInfo
                }

                val lastColon = hostPort.lastIndexOf(':')
                if (lastColon != -1) {
                    address = hostPort.substring(0, lastColon).trim().removePrefix("[").removeSuffix("]")
                    port = hostPort.substring(lastColon + 1).trim().toIntOrNull() ?: 0
                }
            } else {
                // Format 2: ss://base64(method:password@host:port)
                val decodedMain = Base64Helper.decodeToString(mainPart) ?: mainPart
                if (decodedMain.contains('@')) {
                    val atIndex = decodedMain.indexOf('@')
                    val userInfo = decodedMain.substring(0, atIndex).trim()
                    val hostPort = decodedMain.substring(atIndex + 1).trim()

                    val colonIdx = userInfo.indexOf(':')
                    if (colonIdx != -1) {
                        method = userInfo.substring(0, colonIdx)
                        password = userInfo.substring(colonIdx + 1)
                    }

                    val lastColon = hostPort.lastIndexOf(':')
                    if (lastColon != -1) {
                        address = hostPort.substring(0, lastColon).trim().removePrefix("[").removeSuffix("]")
                        port = hostPort.substring(lastColon + 1).trim().toIntOrNull() ?: 0
                    }
                }
            }

            if (address.isBlank() || port <= 0) return null

            val serverName = remark.ifBlank { "$address:$port" }

            ServerConfig(
                id = UUID.nameUUIDFromBytes(trimmed.toByteArray(StandardCharsets.UTF_8)).toString(),
                name = serverName,
                address = address,
                port = port,
                protocol = ProtocolType.SHADOWSOCKS,
                network = NetworkType.TCP,
                security = "none",
                uuid = password,
                encryption = method,
                rawUri = trimmed,
                originalUri = trimmed,
                queryParams = queryParams,
                countryCode = extractCountryHint(serverName)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSafely(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    private fun extractCountryHint(name: String): String {
        val lower = name.lowercase()
        val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return when {
            tokens.contains("nl") || lower.contains("netherland") -> "NL"
            tokens.contains("ch") || lower.contains("swiss") || lower.contains("switzerland") -> "CH"
            tokens.contains("de") || lower.contains("germany") -> "DE"
            tokens.contains("fr") || lower.contains("france") -> "FR"
            tokens.contains("uk") || tokens.contains("gb") || lower.contains("london") || lower.contains("united kingdom") -> "GB"
            tokens.contains("us") || tokens.contains("usa") || lower.contains("united states") -> "US"
            tokens.contains("ca") || lower.contains("canada") -> "CA"
            tokens.contains("fi") || lower.contains("finland") -> "FI"
            tokens.contains("tr") || lower.contains("turkey") -> "TR"
            tokens.contains("se") || lower.contains("sweden") -> "SE"
            tokens.contains("ru") || lower.contains("russia") -> "RU"
            else -> "UN"
        }
    }
}
