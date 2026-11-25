package com.tunnix.xray

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.tunnix.core.models.DownloadSettingsConfig
import com.tunnix.core.models.NetworkType
import com.tunnix.core.models.ProtocolType
import com.tunnix.core.models.ServerConfig

object XrayConfigGenerator {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    const val DEFAULT_SOCKS_PORT = 10808
    const val DEFAULT_HTTP_PORT = 10809

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

        // 2. Inbounds block (Socks5 & HTTP for TUN proxying)
        val inbounds = JsonArray().apply {
            add(buildSocksInbound(socksPort))
            add(buildHttpInbound(httpPort))
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
                add("localhost")
            }
            add("servers", serversArray)
        }
        root.add("dns", dns)

        // 5. Routing rules
        val routing = JsonObject().apply {
            addProperty("domainStrategy", "AsIs")
            val rulesArray = JsonArray().apply {
                // Private IPs -> Direct
                val privateRule = JsonObject().apply {
                    addProperty("type", "field")
                    addProperty("outboundTag", "direct")
                    val ipArray = JsonArray().apply { add("geoip:private") }
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
                addProperty("allowInsecure", server.queryParams["allowInsecure"] == "1" || server.queryParams["insecure"] == "1")
                if (server.alpn.isNotBlank()) {
                    val alpnArray = JsonArray()
                    server.alpn.split(",").forEach { alpnArray.add(it.trim()) }
                    add("alpn", alpnArray)
                }
                if (server.fingerprint.isNotBlank()) {
                    addProperty("fingerprint", server.fingerprint)
                }
            }
            streamSettings.add("tlsSettings", tlsObj)
        }

        // XHTTP Transport Settings (with mode: stream-up, extra, and downloadSettings)
        if (server.network == NetworkType.XHTTP) {
            val dlConfig = customDownloadSettings ?: server.downloadSettings ?: DownloadSettingsConfig.DEFAULT

            val xhttpObj = JsonObject().apply {
                addProperty("host", server.host.ifBlank { server.sni })
                addProperty("path", server.path.ifBlank { "/xhttp" })
                addProperty("mode", dlConfig.mode.ifBlank { "stream-up" })

                // Injected xPaddingBytes inside extra
                val extraObj = JsonObject().apply {
                    addProperty("xPaddingBytes", dlConfig.xPaddingBytes.ifBlank { "100-1000" })
                }
                add("extra", extraObj)
            }
            streamSettings.add("xhttpSettings", xhttpObj)

            // Asymmetric downloadSettings block
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
            streamSettings.add("downloadSettings", dlSettingsObj)
        } else if (server.network == NetworkType.WS) {
            val wsObj = JsonObject().apply {
                addProperty("path", server.path.ifBlank { "/" })
                val headers = JsonObject().apply {
                    addProperty("Host", server.host.ifBlank { server.sni })
                }
                add("headers", headers)
            }
            streamSettings.add("wsSettings", wsObj)
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
}
