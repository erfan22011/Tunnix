package com.tunnix.xray

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.SecureVault
import com.tunnix.core.models.ServerConfig

object XrayConfigGenerator {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Inbounds default to 0 (disabled) to prevent unauthenticated SOCKS5/HTTP port exposure on 127.0.0.1
    const val DEFAULT_SOCKS_PORT = 0
    const val DEFAULT_HTTP_PORT = 0

    /**
     * Generates a complete, production-ready Xray-core JSON configuration
     * with full support for VLESS, XHTTP, Asymmetric downloadSettings, and stream-up.
     */
    fun generate(
        server: ServerConfig,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        httpPort: Int = DEFAULT_HTTP_PORT,
        customDownloadSettings: DownloadSettingsConfig? = null
    ): String {
        val root = JsonObject()

        // 1. Log block
        val log = JsonObject().apply {
            addProperty("loglevel", "warning")
        }
        root.add("log", log)

        // 2. Inbounds block (TUN interface + Socks5 & HTTP for proxying)
        val safeSocksPort = if (socksPort > 0) getAvailablePort(socksPort) else 0
        val safeHttpPort = if (httpPort > 0) getAvailablePort(httpPort) else 0

        val inbounds = JsonArray().apply {
            add(buildTunInbound())
            if (safeSocksPort > 0) add(buildSocksInbound(safeSocksPort))
            if (safeHttpPort > 0) add(buildHttpInbound(safeHttpPort))
        }
        root.add("inbounds", inbounds)

        // 3. Outbounds block (Primary Proxy + Direct + Block)
        val outbounds = JsonArray().apply {
            add(buildProxyOutbound(server, customDownloadSettings))
            add(buildFreedomOutbound())
            add(buildBlackholeOutbound())
        }
        root.add("outbounds", outbounds)

        // 4. DNS configuration
        val dns = JsonObject().apply {
            val serversArray = JsonArray().apply {
                add("1.1.1.1")
                add("8.8.8.8")
            }
            add("servers", serversArray)
        }
        root.add("dns", dns)

        // 5. Routing rules
        val routing = JsonObject().apply {
            // "AsIs" prevents blocking on blocked port 53 upstream DNS queries in restrictive networks
            addProperty("domainStrategy", "AsIs")
            val rulesArray = JsonArray().apply {
                // Private IPs -> Direct
                val privateRule = JsonObject().apply {
                    addProperty("type", "field")
                    addProperty("outboundTag", "direct")
                    val ipArray = JsonArray().apply {
                        add("10.0.0.0/8")
                        add("172.16.0.0/12")
                        add("192.168.0.0/16")
                        add("127.0.0.0/8")
                    }
                    add("ip", ipArray)
                }
                add(privateRule)

                // All other TCP/UDP traffic -> Proxy
                val proxyRule = JsonObject().apply {
                    addProperty("type", "field")
                    addProperty("outboundTag", "proxy")
                    addProperty("network", "tcp,udp")
                }
                add(proxyRule)
            }
            add("rules", rulesArray)
        }
        root.add("routing", routing)

        // 6. Policy & Stats (for real-time Traffic Monitoring)
        val policy = JsonObject().apply {
            val systemObj = JsonObject().apply {
                addProperty("statsInboundUplink", true)
                addProperty("statsInboundDownlink", true)
                addProperty("statsOutboundUplink", true)
                addProperty("statsOutboundDownlink", true)
            }
            add("system", systemObj)
        }
        root.add("policy", policy)
        root.add("stats", JsonObject())

        return gson.toJson(root)
    }

    private fun buildTunInbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "tun-in")
            addProperty("port", 0)
            addProperty("protocol", "tun")

            val settings = JsonObject().apply {
                addProperty("name", "tun0")
                addProperty("mtu", 1400)
                addProperty("userLevel", 8)
            }
            add("settings", settings)

            val sniffing = JsonObject().apply {
                addProperty("enabled", true)
                val destOverride = JsonArray().apply {
                    add("http")
                    add("tls")
                    add("quic")
                }
                add("destOverride", destOverride)
            }
            add("sniffing", sniffing)
        }
    }

    private fun buildSocksInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "socks-in")
            addProperty("port", port)
            addProperty("listen", "127.0.0.1")
            addProperty("protocol", "socks")

            val settings = JsonObject().apply {
                addProperty("auth", "noauth")
                addProperty("udp", true)
            }
            add("settings", settings)

            val sniffing = JsonObject().apply {
                addProperty("enabled", true)
                val destOverride = JsonArray().apply {
                    add("http")
                    add("tls")
                    add("quic")
                }
                add("destOverride", destOverride)
            }
            add("sniffing", sniffing)
        }
    }

    private fun buildHttpInbound(port: Int): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "http-in")
            addProperty("port", port)
            addProperty("listen", "127.0.0.1")
            addProperty("protocol", "http")
        }
    }

    private fun buildProxyOutbound(
        server: ServerConfig,
        customDownloadSettings: DownloadSettingsConfig?
    ): JsonObject {
        val outbound = JsonObject().apply {
            addProperty("tag", "proxy")
            addProperty("protocol", server.protocol.name.lowercase())
        }

        // Protocol Specific Settings
        when (server.protocol) {
            ProtocolType.VLESS -> {
                val settings = JsonObject()
                val vnextArray = JsonArray()
                val serverNode = JsonObject().apply {
                    addProperty("address", server.address)
                    addProperty("port", server.port)

                    val usersArray = JsonArray()
                    val user = JsonObject().apply {
                        addProperty("id", server.uuid)
                        addProperty("encryption", server.encryption.ifBlank { "none" })
                        if (server.flow.isNotBlank()) {
                            addProperty("flow", server.flow)
                        }
                    }
                    usersArray.add(user)
                    add("users", usersArray)
                }
                vnextArray.add(serverNode)
                settings.add("vnext", vnextArray)
                outbound.add("settings", settings)
            }
            ProtocolType.VMESS -> {
                val settings = JsonObject()
                val vnextArray = JsonArray()
                val serverNode = JsonObject().apply {
                    addProperty("address", server.address)
                    addProperty("port", server.port)

                    val usersArray = JsonArray()
                    val user = JsonObject().apply {
                        addProperty("id", server.uuid)
                        addProperty("alterId", 0)
                        addProperty("security", "auto")
                    }
                    usersArray.add(user)
                    add("users", usersArray)
                }
                vnextArray.add(serverNode)
                settings.add("vnext", vnextArray)
                outbound.add("settings", settings)
            }
            ProtocolType.TROJAN -> {
                val settings = JsonObject()
                val serversArray = JsonArray()
                val serverNode = JsonObject().apply {
                    addProperty("address", server.address)
                    addProperty("port", server.port)
                    addProperty("password", server.uuid)
                }
                serversArray.add(serverNode)
                settings.add("servers", serversArray)
                outbound.add("settings", settings)
            }
            ProtocolType.SHADOWSOCKS -> {
                val settings = JsonObject()
                val serversArray = JsonArray()
                val serverNode = JsonObject().apply {
                    addProperty("address", server.address)
                    addProperty("port", server.port)
                    addProperty("method", server.encryption.ifBlank { "chacha20-ietf-poly1305" })
                    addProperty("password", server.uuid)
                    addProperty("uot", true)
                }
                serversArray.add(serverNode)
                settings.add("servers", serversArray)
                outbound.add("settings", settings)
            }
            else -> {
                outbound.add("settings", JsonObject())
            }
        }

        // StreamSettings Block
        val streamSettings = JsonObject()
        val networkName = when (server.network) {
            NetworkType.XHTTP -> "xhttp"
            NetworkType.WS -> "ws"
            NetworkType.GRPC -> "grpc"
            NetworkType.HTTP -> "http"
            else -> "tcp"
        }
        streamSettings.addProperty("network", networkName)

        val sec = server.security.lowercase().ifBlank { "none" }
        streamSettings.addProperty("security", sec)

        // TLS Settings
        if (sec == "tls") {
            val tlsObj = JsonObject().apply {
                addProperty("serverName", server.sni.ifBlank { server.host })
                // Security Hardening: Never allow insecure TLS certificates under any circumstance
                addProperty("allowInsecure", false)

                // Handle pcs (Pinned Certificate SHA-256)
                val pcs = server.queryParams["pcs"]?.trim() ?: ""
                if (pcs.length == 64 && pcs.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    addProperty("pinnedPeerCertSha256", pcs.lowercase())
                }

                val isTcpHttp = server.network == NetworkType.TCP &&
                        server.queryParams["headerType"].equals("http", ignoreCase = true)

                if (isTcpHttp) {
                    // TCP with HTTP header emulation sends plaintext HTTP/1.1 requests.
                    // Negotiating HTTP/2 causes server RST_STREAM binary protocol error.
                    val alpnArray = JsonArray().apply { add("http/1.1") }
                    add("alpn", alpnArray)
                } else if (server.alpn.isNotBlank()) {
                    val alpnArray = JsonArray()
                    server.alpn.split(",").forEach { alpnArray.add(it.trim()) }
                    add("alpn", alpnArray)
                }

                if (server.fingerprint.isNotBlank()) {
                    addProperty("fingerprint", server.fingerprint)
                }
            }
            streamSettings.add("tlsSettings", tlsObj)
        } else if (sec == "reality") {
            val realityObj = JsonObject().apply {
                addProperty("serverName", server.sni.ifBlank { server.host })
                val pbk = server.queryParams["pbk"] ?: server.queryParams["publicKey"] ?: ""
                if (pbk.isNotBlank()) addProperty("publicKey", pbk)
                val sid = server.queryParams["sid"] ?: server.queryParams["shortId"] ?: ""
                if (sid.isNotBlank()) addProperty("shortId", sid)
                val spx = server.queryParams["spx"] ?: server.queryParams["spiderX"] ?: ""
                if (spx.isNotBlank()) addProperty("spiderX", spx)
                if (server.fingerprint.isNotBlank()) {
                    addProperty("fingerprint", server.fingerprint)
                }
            }
            streamSettings.add("realitySettings", realityObj)
        }

        // XHTTP Transport Settings
        val vaultConfig = SecureVault.getConfigForPort(server.port)
        val dlConfig = customDownloadSettings ?: vaultConfig ?: server.downloadSettings
        val matchesTargetPort = dlConfig != null && ((server.port == dlConfig.effectiveTargetPort) || (server.port == dlConfig.port))
        val shouldApplyAsymmetric = dlConfig != null && dlConfig.isEnabled && matchesTargetPort && server.protocol == ProtocolType.VLESS

        if (server.network == NetworkType.XHTTP || shouldApplyAsymmetric) {
            streamSettings.addProperty("network", "xhttp")

            if (shouldApplyAsymmetric && dlConfig != null) {
                // Asymmetric downloadSettings block (Exact structure expected by Xray-core)
                val dlSettingsObj = JsonObject().apply {
                    addProperty("address", dlConfig.address)
                    addProperty("port", dlConfig.port)
                    addProperty("network", dlConfig.network)
                    addProperty("security", dlConfig.security)

                    val dlTls = JsonObject().apply {
                        addProperty("serverName", dlConfig.tlsSettings.serverName)
                        val alpnArray = JsonArray()
                        dlConfig.tlsSettings.alpn.forEach { alpnArray.add(it) }
                        add("alpn", alpnArray)
                        addProperty("fingerprint", dlConfig.tlsSettings.fingerprint)
                    }
                    add("tlsSettings", dlTls)

                    val dlXhttp = JsonObject().apply {
                        addProperty("host", dlConfig.xhttpSettings.host)
                        addProperty("path", dlConfig.xhttpSettings.path)
                    }
                    add("xhttpSettings", dlXhttp)
                }

                val xhttpObj = JsonObject().apply {
                    addProperty("host", server.host.ifBlank { dlConfig.xhttpSettings.host })
                    addProperty("path", server.path.ifBlank { dlConfig.xhttpSettings.path })
                    addProperty("mode", dlConfig.mode.ifBlank { "stream-up" })

                    // Injected xPaddingBytes & downloadSettings inside extra (Xray-core specification)
                    val extraObj = JsonObject().apply {
                        addProperty("xPaddingBytes", dlConfig.xPaddingBytes.ifBlank { "100-1000" })
                        add("downloadSettings", dlSettingsObj)
                    }
                    add("extra", extraObj)
                    // Also attach downloadSettings directly for variants expecting it under xhttpSettings
                    add("downloadSettings", dlSettingsObj)
                }
                streamSettings.add("xhttpSettings", xhttpObj)
                // Also attach at streamSettings level for root compatibility
                streamSettings.add("downloadSettings", dlSettingsObj)
            } else {
                // Standard symmetric XHTTP settings (when port differs or feature disabled)
                val xhttpObj = JsonObject().apply {
                    val resolvedHost = server.host.ifBlank { server.sni }
                    if (resolvedHost.isNotBlank()) {
                        addProperty("host", resolvedHost)
                    }
                    addProperty("path", server.path.ifBlank { "/xhttp" })
                    addProperty("mode", server.mode.ifBlank { "auto" })

                    val extraObj = JsonObject()
                    if (server.extraJson.isNotBlank()) {
                        try {
                            val parsed = com.google.gson.JsonParser.parseString(server.extraJson).asJsonObject
                            val allowedKeys = setOf("xPaddingBytes", "downloadSettings", "scMaxEachPostBytes", "noSSEHeader")
                            for ((k, v) in parsed.entrySet()) {
                                if (k in allowedKeys) {
                                    extraObj.add(k, v)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (server.xPaddingBytes.isNotBlank()) {
                        extraObj.addProperty("xPaddingBytes", server.xPaddingBytes)
                    }
                    if (extraObj.size() > 0) {
                        add("extra", extraObj)
                    }
                }
                streamSettings.add("xhttpSettings", xhttpObj)
            }
        } else if (server.network == NetworkType.WS) {
            val wsObj = JsonObject().apply {
                addProperty("path", server.path.ifBlank { "/" })
                val headers = JsonObject().apply {
                    addProperty("Host", server.host.ifBlank { server.sni })
                }
                add("headers", headers)
            }
            streamSettings.add("wsSettings", wsObj)
        } else if (server.network == NetworkType.TCP) {
            val headerType = server.queryParams["headerType"] ?: ""
            if (headerType.equals("http", ignoreCase = true)) {
                val tcpObj = JsonObject().apply {
                    val headerObj = JsonObject().apply {
                        addProperty("type", "http")
                        val reqObj = JsonObject().apply {
                            addProperty("version", "1.1")
                            addProperty("method", "GET")
                            val pathArray = JsonArray().apply {
                                add(server.path.ifBlank { "/" })
                            }
                            add("path", pathArray)
                            val headersObj = JsonObject().apply {
                                val hostArray = JsonArray().apply {
                                    add(server.host.ifBlank { server.sni }.ifBlank { server.address })
                                }
                                add("Host", hostArray)
                                val uaArray = JsonArray().apply {
                                    add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                }
                                add("User-Agent", uaArray)
                            }
                            add("headers", headersObj)
                        }
                        add("request", reqObj)
                    }
                    add("header", headerObj)
                }
                streamSettings.add("tcpSettings", tcpObj)
            }
        } else if (server.network == NetworkType.GRPC) {
            val serviceName = server.queryParams["serviceName"] ?: server.path.trimStart('/')
            val grpcObj = JsonObject().apply {
                if (serviceName.isNotBlank()) {
                    addProperty("serviceName", serviceName)
                }
                val multiMode = server.queryParams["mode"].equals("multi", ignoreCase = true)
                addProperty("multiMode", multiMode)
            }
            streamSettings.add("grpcSettings", grpcObj)
        } else if (server.network == NetworkType.HTTP) {
            val httpObj = JsonObject().apply {
                val hostArray = JsonArray()
                val h = server.host.ifBlank { server.sni }
                if (h.isNotBlank()) hostArray.add(h)
                add("host", hostArray)
                addProperty("path", server.path.ifBlank { "/" })
            }
            streamSettings.add("httpSettings", httpObj)
        }

        outbound.add("streamSettings", streamSettings)
        return outbound
    }

    private fun buildFreedomOutbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "direct")
            addProperty("protocol", "freedom")
            add("settings", JsonObject())
        }
    }

    private fun buildBlackholeOutbound(): JsonObject {
        return JsonObject().apply {
            addProperty("tag", "block")
            addProperty("protocol", "blackhole")
            add("settings", JsonObject())
        }
    }

    /**
     * Finds an available local port, falling back to an ephemeral free port if the preferred port is occupied.
     */
    fun getAvailablePort(preferredPort: Int): Int {
        if (preferredPort <= 0) return 0
        return try {
            java.net.ServerSocket(preferredPort, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        } catch (_: Exception) {
            try {
                java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
            } catch (_: Exception) {
                0
            }
        }
    }

    private fun isIpAddress(host: String): Boolean {
        val clean = host.removeSurrounding("[", "]")
        val ipv4Regex = Regex("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$")
        if (ipv4Regex.matches(clean)) return true
        if (clean.contains(":") && clean.split(":").size >= 3) return true
        return false
    }
}
