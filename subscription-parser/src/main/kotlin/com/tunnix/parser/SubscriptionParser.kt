package com.tunnix.parser

import com.tunnix.core.models.ServerConfig

object SubscriptionParser {

    /**
     * Parses subscription content into a list of ServerConfig objects.
     * Handles both Base64-encoded strings and plaintext multiline URLs.
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
                    decoded.contains("trojan://", ignoreCase = true))) {
            textToParse = decoded
        }

        // Split into lines
        val lines = textToParse.split(Regex("[\r\n]+"))
        val servers = mutableListOf<ServerConfig>()

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty() || cleanLine.startsWith("#")) continue

            val server = when {
                cleanLine.startsWith("vless://", ignoreCase = true) -> VlessParser.parse(cleanLine)
                cleanLine.startsWith("vmess://", ignoreCase = true) -> VmessParser.parse(cleanLine)
                cleanLine.startsWith("trojan://", ignoreCase = true) -> TrojanParser.parse(cleanLine)
                else -> null
            }

            if (server != null) {
                servers.add(server)
            }
        }

        if (servers.isEmpty()) {
            throw SubscriptionParserException.EmptySubscriptionException()
        }

        return servers
    }
}
