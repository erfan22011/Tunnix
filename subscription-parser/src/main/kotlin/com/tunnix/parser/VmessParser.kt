package com.tunnix.parser

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import java.nio.charset.StandardCharsets
import java.util.UUID

object VmessParser {

    private val gson = Gson()

    /**
     * Parses a vmess:// link (base64 encoded JSON) into a ServerConfig object.
     * Standard VMess JSON format:
     * {"v": "2", "ps": "Remark", "add": "IP/Domain", "port": 443, "id": "UUID", ...}
     */
    fun parse(rawUri: String): ServerConfig? {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("vmess://", ignoreCase = true)) return null

        return try {
            val base64Payload = trimmed.substring(8)
            val jsonString = Base64Helper.decodeToString(base64Payload) ?: return null
            val obj = gson.fromJson(jsonString, JsonObject::class.java) ?: return null

            val name = obj.get("ps")?.asString ?: "VMess Server"
            val address = obj.get("add")?.asString?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
            val port = obj.get("port")?.asInt ?: return null
            val uuid = obj.get("id")?.asString?.trim() ?: ""

            if (port !in 1..65535 || address.isBlank() || uuid.isBlank()) return null

            val security = if (obj.get("tls")?.asString?.equals("tls", ignoreCase = true) == true) "tls" else "none"
            val net = obj.get("net")?.asString?.lowercase() ?: "tcp"
            val host = obj.get("host")?.asString ?: ""
            val path = obj.get("path")?.asString ?: ""
            val sni = obj.get("sni")?.asString ?: host
            val alpn = obj.get("alpn")?.asString ?: ""
            val fp = obj.get("fp")?.asString ?: ""

            val network = when (net) {
                "xhttp", "splithttp" -> NetworkType.XHTTP
                "ws", "websocket" -> NetworkType.WS
                "grpc" -> NetworkType.GRPC
                "tcp" -> NetworkType.TCP
                "http" -> NetworkType.HTTP
                else -> NetworkType.UNKNOWN
            }

            val serverName = name.ifBlank { "$address:$port" }.take(ServerConfig.MAX_NAME_LENGTH)

            ServerConfig(
                id = UUID.nameUUIDFromBytes(trimmed.toByteArray(StandardCharsets.UTF_8)).toString(),
                name = serverName,
                address = address,
                port = port,
                protocol = ProtocolType.VMESS,
                network = network,
                security = security,
                uuid = uuid,
                sni = sni,
                host = host,
                path = path,
                alpn = alpn,
                fingerprint = fp,
                rawUri = trimmed,
                originalUri = trimmed
            )
        } catch (_: Exception) {
            null
        }
    }
}
