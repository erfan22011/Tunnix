package com.tunnix.xray

import com.google.gson.JsonObject
import com.google.gson.JsonParser

object XrayConfigValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String> = emptyList()
    )

    fun validate(configJson: String): ValidationResult {
        val issues = mutableListOf<String>()

        if (configJson.isBlank()) {
            return ValidationResult(false, listOf("Config JSON is empty"))
        }

        val root: JsonObject = try {
            JsonParser.parseString(configJson).asJsonObject
        } catch (e: Exception) {
            return ValidationResult(false, listOf("Invalid JSON syntax: ${e.message}"))
        }

        // Check essential root sections
        if (!root.has("inbounds") || !root.getAsJsonArray("inbounds").isJsonArray) {
            issues.add("Missing or invalid 'inbounds' array")
        }
        if (!root.has("outbounds") || !root.getAsJsonArray("outbounds").isJsonArray) {
            issues.add("Missing or invalid 'outbounds' array")
        }
        if (!root.has("routing")) {
            issues.add("Missing 'routing' section")
        }

        val outbounds = root.getAsJsonArray("outbounds")
        val proxyOutbound = outbounds?.firstOrNull { elem ->
            elem.isJsonObject && elem.asJsonObject.get("tag")?.asString == "proxy"
        }?.asJsonObject

        if (proxyOutbound == null) {
            issues.add("Missing primary 'proxy' outbound")
        } else {
            val protocol = proxyOutbound.get("protocol")?.asString
            if (protocol.isNullOrBlank()) {
                issues.add("Proxy outbound missing 'protocol'")
            }

            val streamSettings = proxyOutbound.getAsJsonObject("streamSettings")
            val network = streamSettings?.get("network")?.asString

            if (network == "xhttp") {
                val xhttpSettings = streamSettings.getAsJsonObject("xhttpSettings")
                if (xhttpSettings == null) {
                    issues.add("XHTTP streamSettings missing 'xhttpSettings'")
                }

                // If asymmetric downloadSettings is present, validate its fields
                val downloadSettings = streamSettings?.getAsJsonObject("downloadSettings")
                if (downloadSettings != null) {
                    if (!downloadSettings.has("address") || downloadSettings.get("address").asString.isBlank()) {
                        issues.add("downloadSettings missing 'address'")
                    }
                    if (!downloadSettings.has("port") || downloadSettings.get("port").asInt <= 0) {
                        issues.add("downloadSettings missing or invalid 'port'")
                    }
                    if (!downloadSettings.has("tlsSettings")) {
                        issues.add("downloadSettings missing 'tlsSettings'")
                    }
                    if (!downloadSettings.has("xhttpSettings")) {
                        issues.add("downloadSettings missing 'xhttpSettings'")
                    }
                }
            }
        }

        return ValidationResult(issues.isEmpty(), issues)
    }
}
