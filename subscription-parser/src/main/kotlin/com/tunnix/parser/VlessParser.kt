package com.tunnix.parser

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object VlessParser {

    /**
     * Parses a VLESS URI string into a ServerConfig object.
     * Preserves all query parameters, UUID, address, port, and original link.
     */
    fun parse(uriString: String): ServerConfig? {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true)) return null

        return try {
            val withoutScheme = trimmed.substring(8)

            // Split fragment (#remark)
            val hashIndex = withoutScheme.indexOf('#')
            val beforeHash = if (hashIndex != -1) withoutScheme.substring(0, hashIndex) else withoutScheme
            val rawRemark = if (hashIndex != -1) withoutScheme.substring(hashIndex + 1) else ""
            val remark = decodeSafely(rawRemark)

            // Split query (?params)
            val queryIndex = beforeHash.indexOf('?')
            val mainPart = if (queryIndex != -1) beforeHash.substring(0, queryIndex) else beforeHash
            val queryString = if (queryIndex != -1) beforeHash.substring(queryIndex + 1) else ""

            // Parse uuid@host:port
            val atIndex = mainPart.indexOf('@')
            if (atIndex == -1) return null

            val uuid = mainPart.substring(0, atIndex).trim()
            val hostPort = mainPart.substring(atIndex + 1).trim()

            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon == -1) return null

            val address = hostPort.substring(0, lastColon).trim().removePrefix("[").removeSuffix("]")
            val port = hostPort.substring(lastColon + 1).trim().toIntOrNull() ?: return null

            // Parse query parameters
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

            // Extract known network and security fields
            val typeValue = queryParams["type"] ?: queryParams["net"] ?: "tcp"
            val network = when (typeValue.lowercase()) {
                "xhttp", "splithttp" -> NetworkType.XHTTP
                "ws", "websocket" -> NetworkType.WS
                "grpc" -> NetworkType.GRPC
                "tcp" -> NetworkType.TCP
                "http" -> NetworkType.HTTP
                else -> NetworkType.UNKNOWN
            }

            val security = queryParams["security"] ?: "none"
            val sni = queryParams["sni"] ?: ""
            val host = queryParams["host"] ?: ""
            val path = queryParams["path"] ?: ""
            val alpn = queryParams["alpn"] ?: ""
            val fp = queryParams["fp"] ?: ""
            val flow = queryParams["flow"] ?: ""
            val encryption = queryParams["encryption"] ?: "none"
            val mode = queryParams["mode"] ?: "auto"
            val extra = queryParams["extra"] ?: ""

            val serverName = remark.ifBlank { "$address:$port" }

            ServerConfig(
                id = UUID.nameUUIDFromBytes(trimmed.toByteArray(StandardCharsets.UTF_8)).toString(),
                name = serverName,
                address = address,
                port = port,
                protocol = ProtocolType.VLESS,
                network = network,
                security = security,
                uuid = uuid,
                sni = sni,
                host = host,
                path = path,
                alpn = alpn,
                fingerprint = fp,
                flow = flow,
                encryption = encryption,
                mode = mode,
                extraJson = extra,
                rawUri = trimmed,
                originalUri = trimmed,
                isXhttpModified = false,
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
        return when {
            lower.contains("nl") || lower.contains("netherland") -> "NL"
            lower.contains("ch") || lower.contains("swiss") -> "CH"
            lower.contains("de") || lower.contains("germany") -> "DE"
            lower.contains("fr") || lower.contains("france") -> "FR"
            lower.contains("uk") || lower.contains("london") -> "GB"
            lower.contains("us") || lower.contains("usa") -> "US"
            else -> "UN"
        }
    }
}
