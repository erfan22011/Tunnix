package com.tunnix.modifier

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.DownloadSettingsResolver
import com.tunnix.core.models.IpAddressSecurity
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object XhttpConfigModifier {

    private val gson = Gson()

    /**
     * Checks if a server qualifies for XHTTP modification:
     * Must be VLESS protocol, server address must not be loopback or private IP,
     * and must resolve via DownloadSettingsResolver (default ports 2053, 6793, or custom targetPort).
     */
    fun isEligible(server: ServerConfig, customDownloadSettings: DownloadSettingsConfig? = null): Boolean {
        if (server.protocol != ProtocolType.VLESS || server.network != NetworkType.XHTTP) return false
        if (IpAddressSecurity.isPrivateOrLoopback(server.address)) return false

        return DownloadSettingsResolver.resolve(server, customDownloadSettings) != null
    }

    fun resolveConfig(server: ServerConfig, customDownloadSettings: DownloadSettingsConfig? = null): DownloadSettingsConfig? {
        if (!isEligible(server, customDownloadSettings)) return null
        return DownloadSettingsResolver.resolve(server, customDownloadSettings)
    }

    /**
     * Modifies an eligible VLESS XHTTP server config:
     * 1. Injects / updates "xPaddingBytes"
     * 2. Injects / updates "downloadSettings" with exact asymmetric structure
     * 3. Sets "mode" to "stream-up"
     * 4. Merges with any existing "extra" fields without losing unknown keys
     * 5. Preserves 100% of all other server parameters (UUID, address, port, SNI, host, path, encryption, flow, etc.)
     * 6. Rebuilds the VLESS URI using standard URL Encoding.
     */
    fun modify(
        server: ServerConfig,
        customDownloadSettings: DownloadSettingsConfig? = null
    ): ServerConfig {
        if (!isEligible(server, customDownloadSettings)) return server
        val config = resolveConfig(server, customDownloadSettings) ?: return server

        return try {
            // 1. Parse or initialize extra JSON object (preserving existing extra keys)
            val extraObj: JsonObject = if (server.extraJson.isNotBlank()) {
                try {
                    JsonParser.parseString(server.extraJson).asJsonObject
                } catch (_: Exception) {
                    JsonObject()
                }
            } else {
                JsonObject()
            }

            // 2. Inject or update xPaddingBytes
            extraObj.addProperty("xPaddingBytes", config.xPaddingBytes)

            // 3. Build downloadSettings JSON object
            val downloadSettingsObj = JsonObject().apply {
                addProperty("address", config.address)
                addProperty("port", config.port)
                addProperty("network", config.network)
                addProperty("security", config.security)

                val tlsObj = JsonObject().apply {
                    addProperty("serverName", config.tlsSettings.serverName)
                    val alpnArray = JsonArray()
                    config.tlsSettings.alpn.forEach { alpnArray.add(it) }
                    add("alpn", alpnArray)
                    addProperty("fingerprint", config.tlsSettings.fingerprint)
                }
                add("tlsSettings", tlsObj)

                val xhttpObj = JsonObject().apply {
                    addProperty("host", config.xhttpSettings.host)
                    addProperty("path", config.xhttpSettings.path)
                }
                add("xhttpSettings", xhttpObj)
            }

            extraObj.add("downloadSettings", downloadSettingsObj)
            val updatedExtraJson = gson.toJson(extraObj)

            // 4. Update query parameters with mode and extra while preserving all other keys
            val updatedQueryParams = LinkedHashMap(server.queryParams)
            updatedQueryParams["mode"] = config.mode
            updatedQueryParams["extra"] = updatedExtraJson

            // Ensure critical original parameters are explicitly present in map
            if (!updatedQueryParams.containsKey("type")) updatedQueryParams["type"] = "xhttp"
            if (server.path.isNotEmpty() && !updatedQueryParams.containsKey("path")) updatedQueryParams["path"] = server.path
            if (server.security.isNotEmpty() && !updatedQueryParams.containsKey("security")) updatedQueryParams["security"] = server.security
            if (server.sni.isNotEmpty() && !updatedQueryParams.containsKey("sni")) updatedQueryParams["sni"] = server.sni
            if (server.host.isNotEmpty() && !updatedQueryParams.containsKey("host")) updatedQueryParams["host"] = server.host
            if (server.alpn.isNotEmpty() && !updatedQueryParams.containsKey("alpn")) updatedQueryParams["alpn"] = server.alpn
            if (server.fingerprint.isNotEmpty() && !updatedQueryParams.containsKey("fp")) updatedQueryParams["fp"] = server.fingerprint
            if (server.flow.isNotEmpty() && !updatedQueryParams.containsKey("flow")) updatedQueryParams["flow"] = server.flow
            if (server.encryption.isNotEmpty() && !updatedQueryParams.containsKey("encryption")) updatedQueryParams["encryption"] = server.encryption

            // 5. Reconstruct full VLESS URI with standard URL Encoding
            val queryString = updatedQueryParams.entries.joinToString("&") { (key, value) ->
                val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name())
                val encodedVal = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                "$encodedKey=$encodedVal"
            }

            val encodedRemark = URLEncoder.encode(server.name, StandardCharsets.UTF_8.name())
                .replace("+", "%20")

            val modifiedUri = "vless://${server.uuid}@${server.address}:${server.port}?$queryString#$encodedRemark"

            server.copy(
                network = NetworkType.XHTTP,
                mode = config.mode,
                xPaddingBytes = config.xPaddingBytes,
                downloadSettings = config,
                extraJson = updatedExtraJson,
                rawUri = modifiedUri,
                isXhttpModified = true,
                queryParams = updatedQueryParams
            )
        } catch (_: Exception) {
            // If modification fails, preserve original server intact
            server
        }
    }

    /**
     * Modifies all eligible servers in a list, leaving non-eligible servers untouched.
     */
    fun modifyAll(
        servers: List<ServerConfig>,
        customDownloadSettings: DownloadSettingsConfig? = null
    ): List<ServerConfig> {
        return servers.map { server ->
            if (isEligible(server, customDownloadSettings)) {
                modify(server, customDownloadSettings)
            } else if (server.isXhttpModified && !isEligible(server, customDownloadSettings)) {
                // If it was previously modified, clear asymmetric downloadSettings & mark isXhttpModified = false
                server.copy(
                    isXhttpModified = false,
                    downloadSettings = null,
                    mode = "auto"
                )
            } else {
                server // Untouched
            }
        }
    }
}
