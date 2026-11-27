package com.tunnix.parser

import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object TrojanParser {

    fun parse(uriString: String): ServerConfig? {
        val trimmed = uriString.trim()
        if (!trimmed.startsWith("trojan://", ignoreCase = true)) return null

        return try {
            val withoutScheme = trimmed.substring(9)

            val hashIndex = withoutScheme.indexOf('#')
            val beforeHash = if (hashIndex != -1) withoutScheme.substring(0, hashIndex) else withoutScheme
            val rawRemark = if (hashIndex != -1) withoutScheme.substring(hashIndex + 1) else ""
            val remark = decodeSafely(rawRemark)

            val queryIndex = beforeHash.indexOf('?')
            val mainPart = if (queryIndex != -1) beforeHash.substring(0, queryIndex) else beforeHash
            val queryString = if (queryIndex != -1) beforeHash.substring(queryIndex + 1) else ""

            val atIndex = mainPart.indexOf('@')
            if (atIndex == -1) return null

            val password = mainPart.substring(0, atIndex).trim()
            val hostPort = mainPart.substring(atIndex + 1).trim()

            val lastColon = hostPort.lastIndexOf(':')
            if (lastColon == -1) return null

            val address = hostPort.substring(0, lastColon).trim().removePrefix("[").removeSuffix("]")
            val port = hostPort.substring(lastColon + 1).trim().toIntOrNull() ?: return null
            if (port !in 1..65535 || address.isBlank() || password.isBlank()) return null

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

            val typeValue = queryParams["type"] ?: queryParams["net"] ?: "tcp"
            val network = when (typeValue.lowercase()) {
                "xhttp", "splithttp" -> NetworkType.XHTTP
                "ws", "websocket" -> NetworkType.WS
                "grpc" -> NetworkType.GRPC
                "tcp" -> NetworkType.TCP
                else -> NetworkType.UNKNOWN
            }

            val security = queryParams["security"] ?: "tls"
            val sni = queryParams["sni"] ?: queryParams["peer"] ?: ""
            val host = queryParams["host"] ?: ""
            val path = queryParams["path"] ?: ""
            val alpn = queryParams["alpn"] ?: ""
            val fp = queryParams["fp"] ?: ""

            val serverName = remark.ifBlank { "$address:$port" }.take(ServerConfig.MAX_NAME_LENGTH)

            ServerConfig(
                id = UUID.nameUUIDFromBytes(trimmed.toByteArray(StandardCharsets.UTF_8)).toString(),
                name = serverName,
                address = address,
                port = port,
                protocol = ProtocolType.TROJAN,
                network = network,
                security = security,
                uuid = password,
                sni = sni,
                host = host,
                path = path,
                alpn = alpn,
                fingerprint = fp,
                rawUri = trimmed,
                originalUri = trimmed,
                queryParams = queryParams
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
}
