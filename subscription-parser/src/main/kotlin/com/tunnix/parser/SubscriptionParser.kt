package com.tunnix.parser

import com.tunnix.core.models.ServerConfig

object SubscriptionParser {

    const val MAX_SERVERS = 500
    const val MAX_LINE_LENGTH = 4096

    /**
     * Parses subscription content into a list of ServerConfig objects.
     * Handles both Base64-encoded strings and plaintext multiline URLs.
     * Enforces DoS bounds (max 500 servers, max line length 4096 chars) and deduplicates servers.
     */
    fun parse(rawContent: String): List<ServerConfig> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            throw SubscriptionParserException.EmptySubscriptionException()
        }

        // Try Base64 decoding
        var textToParse = trimmed
        val decoded = Base64Helper.decodeToString(trimmed)
        if (decoded != null && (decoded.contains("vless://", ignoreCase = true) ||
                    decoded.contains("vmess://", ignoreCase = true) ||
                    decoded.contains("trojan://", ignoreCase = true) ||
                    decoded.contains("ss://", ignoreCase = true))) {
            textToParse = decoded
        }

        // Split into lines
        val lines = textToParse.split(Regex("[\r\n]+"))
        val servers = mutableListOf<ServerConfig>()

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty() || cleanLine.startsWith("#")) continue
            if (cleanLine.length > MAX_LINE_LENGTH) continue // Reject excessively long malformed lines

            val server = when {
                cleanLine.startsWith("vless://", ignoreCase = true) -> VlessParser.parse(cleanLine)
                cleanLine.startsWith("vmess://", ignoreCase = true) -> VmessParser.parse(cleanLine)
                cleanLine.startsWith("trojan://", ignoreCase = true) -> TrojanParser.parse(cleanLine)
                cleanLine.startsWith("ss://", ignoreCase = true) -> ShadowsocksParser.parse(cleanLine)
                else -> null
            }

            if (server != null) {
                servers.add(server)
                if (servers.size >= MAX_SERVERS * 2) {
                    break
                }
            }
        }

        // Deduplicate servers by unique ID and enforce maximum server count limit
        val distinctServers = servers.distinctBy { it.id }.take(MAX_SERVERS)

        if (distinctServers.isEmpty()) {
            throw SubscriptionParserException.EmptySubscriptionException()
        }

        return distinctServers
    }
}
