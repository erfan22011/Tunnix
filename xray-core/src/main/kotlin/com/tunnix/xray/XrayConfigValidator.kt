package com.tunnix.xray

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Validates Xray-core JSON configurations before passing them to the native engine.
 *
 * Enforces:
 * 1. Strict input length bounds (Anti-DoS / Memory Bomb)
 * 2. Complete syntax and JSON object structure validation
 * 3. Type-safe field checking without unchecked cast exceptions
 * 4. Comprehensive port range validation (1..65535, or 0 for tun)
 * 5. Inbounds, Outbounds, Protocol settings, StreamSettings, and Routing integrity
 */
object XrayConfigValidator {

    const val MAX_CONFIG_JSON_BYTES = 512 * 1024 // 512 Kilobytes
    private val FORBIDDEN_FIELDS = setOf("command", "exec", "script", "browserforwarder", "reverse")
    private val ALLOWED_OUTBOUND_PROTOCOLS = setOf("vless", "vmess", "trojan", "shadowsocks", "freedom", "blackhole", "direct")

    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String> = emptyList()
    )

    fun validate(configJson: String): ValidationResult {
        val issues = mutableListOf<String>()

        if (configJson.isBlank()) {
            return ValidationResult(false, listOf("Config JSON is empty"))
        }

        if (configJson.length > MAX_CONFIG_JSON_BYTES) {
            return ValidationResult(false, listOf("Config JSON exceeds size limit (512KB)"))
        }

        val rootElement: JsonElement = try {
            JsonParser.parseString(configJson)
        } catch (e: Exception) {
            return ValidationResult(false, listOf("Invalid JSON syntax: ${e.message}"))
        }

        if (!rootElement.isJsonObject) {
            return ValidationResult(false, listOf("Root element must be a JSON Object"))
        }

        // 0. Check for forbidden/dangerous execution fields
        checkForbiddenFields(rootElement, issues)

        val root = rootElement.asJsonObject

        // 1. Validate Inbounds section
        if (!root.has("inbounds") || !root.get("inbounds").isJsonArray) {
            issues.add("Missing or invalid 'inbounds' array")
        } else {
            val inbounds = root.getAsJsonArray("inbounds")
            if (inbounds.size() == 0) {
                issues.add("'inbounds' array cannot be empty")
            } else {
                for (i in 0 until inbounds.size()) {
                    val elem = inbounds.get(i)
                    if (!elem.isJsonObject) {
                        issues.add("Inbound at index $i must be a JSON Object")
                        continue
                    }
                    val inObj = elem.asJsonObject
                    val tag = getStringSafe(inObj, "tag")
                    if (tag.isNullOrBlank()) {
                        issues.add("Inbound at index $i missing 'tag'")
                    }
                    val protocol = getStringSafe(inObj, "protocol")
                    if (protocol.isNullOrBlank()) {
                        issues.add("Inbound at index $i missing 'protocol'")
                    }
                    if (inObj.has("port") && !isValidPort(inObj.get("port"), allowZero = true)) {
                        issues.add("Inbound at index $i has invalid port")
                    }
                }
            }
        }

        // 2. Validate Outbounds section
        if (!root.has("outbounds") || !root.get("outbounds").isJsonArray) {
            issues.add("Missing or invalid 'outbounds' array")
        } else {
            val outbounds = root.getAsJsonArray("outbounds")
            var foundProxyOutbound = false

            for (i in 0 until outbounds.size()) {
                val elem = outbounds.get(i)
                if (!elem.isJsonObject) {
                    issues.add("Outbound at index $i must be a JSON Object")
                    continue
                }
                val outObj = elem.asJsonObject
                val tag = getStringSafe(outObj, "tag")
                val protocol = getStringSafe(outObj, "protocol")

                if (tag == "proxy") {
                    foundProxyOutbound = true
                    if (protocol.isNullOrBlank()) {
                        issues.add("Proxy outbound missing or blank 'protocol'")
                    } else if (protocol.lowercase() !in ALLOWED_OUTBOUND_PROTOCOLS) {
                        issues.add("Proxy outbound has unsupported protocol '$protocol'")
                    }

                    // Validate protocol-specific settings if present
                    if (outObj.has("settings") && outObj.get("settings").isJsonObject) {
                        val settings = outObj.getAsJsonObject("settings")
                        validateProxySettings(settings, protocol ?: "", issues)
                    }

                    // Validate streamSettings
                    if (outObj.has("streamSettings")) {
                        val streamElem = outObj.get("streamSettings")
                        if (!streamElem.isJsonObject) {
                            issues.add("streamSettings must be a JSON Object")
                        } else {
                            validateStreamSettings(streamElem.asJsonObject, issues)
                        }
                    }
                }
            }

            if (!foundProxyOutbound) {
                issues.add("Missing primary 'proxy' outbound")
            }
        }

        // 3. Validate Routing section
        if (!root.has("routing")) {
            issues.add("Missing 'routing' section")
        } else {
            val routingElem = root.get("routing")
            if (!routingElem.isJsonObject) {
                issues.add("'routing' section must be a JSON Object")
            } else {
                val routing = routingElem.asJsonObject
                if (routing.has("rules")) {
                    val rulesElem = routing.get("rules")
                    if (!rulesElem.isJsonArray) {
                        issues.add("routing 'rules' must be a JSON Array")
                    } else {
                        val rules = rulesElem.asJsonArray
                        for (r in 0 until rules.size()) {
                            val rElem = rules.get(r)
                            if (!rElem.isJsonObject) {
                                issues.add("routing rule at index $r must be a JSON Object")
                            } else {
                                val ruleObj = rElem.asJsonObject
                                val outboundTag = getStringSafe(ruleObj, "outboundTag")
                                if (outboundTag.isNullOrBlank()) {
                                    issues.add("routing rule at index $r missing 'outboundTag'")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Validate DNS section if present
        if (root.has("dns")) {
            val dnsElem = root.get("dns")
            if (!dnsElem.isJsonObject) {
                issues.add("'dns' section must be a JSON Object")
            } else {
                val dns = dnsElem.asJsonObject
                if (dns.has("servers") && !dns.get("servers").isJsonArray) {
                    issues.add("dns 'servers' must be a JSON Array")
                }
            }
        }

        return ValidationResult(issues.isEmpty(), issues)
    }

    private fun validateProxySettings(settings: JsonObject, protocol: String, issues: MutableList<String>) {
        when (protocol.lowercase()) {
            "vless", "vmess" -> {
                if (settings.has("vnext")) {
                    val vnextElem = settings.get("vnext")
                    if (!vnextElem.isJsonArray) {
                        issues.add("vnext must be a JSON Array")
                    } else {
                        val vnext = vnextElem.asJsonArray
                        for (i in 0 until vnext.size()) {
                            val nElem = vnext.get(i)
                            if (!nElem.isJsonObject) {
                                issues.add("vnext node at index $i must be a JSON Object")
                                continue
                            }
                            val node = nElem.asJsonObject
                            val address = getStringSafe(node, "address")
                            if (address.isNullOrBlank()) {
                                issues.add("vnext node at index $i missing 'address'")
                            }
                            if (!isValidPort(node.get("port"), allowZero = false)) {
                                issues.add("vnext node at index $i missing or invalid 'port'")
                            }
                        }
                    }
                }
            }
            "trojan", "shadowsocks" -> {
                if (settings.has("servers")) {
                    val serversElem = settings.get("servers")
                    if (!serversElem.isJsonArray) {
                        issues.add("servers must be a JSON Array")
                    } else {
                        val servers = serversElem.asJsonArray
                        for (i in 0 until servers.size()) {
                            val sElem = servers.get(i)
                            if (!sElem.isJsonObject) {
                                issues.add("servers node at index $i must be a JSON Object")
                                continue
                            }
                            val sNode = sElem.asJsonObject
                            val address = getStringSafe(sNode, "address")
                            if (address.isNullOrBlank()) {
                                issues.add("servers node at index $i missing 'address'")
                            }
                            if (!isValidPort(sNode.get("port"), allowZero = false)) {
                                issues.add("servers node at index $i missing or invalid 'port'")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun validateStreamSettings(streamSettings: JsonObject, issues: MutableList<String>) {
        val network = getStringSafe(streamSettings, "network")

        if (network == "xhttp") {
            if (!streamSettings.has("xhttpSettings") || !streamSettings.get("xhttpSettings").isJsonObject) {
                issues.add("XHTTP streamSettings missing or invalid 'xhttpSettings'")
            }

            // Asymmetric downloadSettings validation
            if (streamSettings.has("downloadSettings")) {
                val dlElem = streamSettings.get("downloadSettings")
                if (!dlElem.isJsonObject) {
                    issues.add("downloadSettings must be a JSON Object")
                } else {
                    val dl = dlElem.asJsonObject
                    val address = getStringSafe(dl, "address")
                    if (address.isNullOrBlank()) {
                        issues.add("downloadSettings missing 'address'")
                    } else if (isPrivateOrLoopback(address)) {
                        issues.add("downloadSettings address cannot be loopback or private IP (SSRF protection)")
                    }
                    if (!isValidPort(dl.get("port"), allowZero = false)) {
                        issues.add("downloadSettings missing or invalid 'port' (must be 1..65535)")
                    }
                    if (!dl.has("tlsSettings") || !dl.get("tlsSettings").isJsonObject) {
                        issues.add("downloadSettings missing or invalid 'tlsSettings'")
                    }
                    if (!dl.has("xhttpSettings") || !dl.get("xhttpSettings").isJsonObject) {
                        issues.add("downloadSettings missing or invalid 'xhttpSettings'")
                    }
                }
            }
        }
    }

    fun isPrivateOrLoopback(address: String): Boolean {
        val clean = address.trim().lowercase()
            .removePrefix("[").removeSuffix("]")
        if (clean == "localhost" || clean == "127.0.0.1" || clean == "::1" || clean == "0.0.0.0" ||
            clean.startsWith("127.") || clean.startsWith("10.") || clean.startsWith("192.168.") ||
            clean.startsWith("169.254.") || clean.startsWith("100.64.") ||
            clean.endsWith(".local") || clean.endsWith(".internal") || clean.endsWith(".lan")) {
            return true
        }
        if (clean.startsWith("172.")) {
            val parts = clean.split(".")
            if (parts.size >= 2) {
                val secondOctet = parts[1].toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) {
                    return true
                }
            }
        }
        return false
    }

    fun isValidPort(element: JsonElement?, allowZero: Boolean = false): Boolean {
        if (element == null || !element.isJsonPrimitive) return false
        val prim = element.asJsonPrimitive
        val portNum = if (prim.isNumber) {
            try { prim.asInt } catch (_: Exception) { return false }
        } else if (prim.isString) {
            prim.asString.toIntOrNull() ?: return false
        } else {
            return false
        }
        return if (allowZero) portNum in 0..65535 else portNum in 1..65535
    }

    private fun getStringSafe(obj: JsonObject, memberName: String): String? {
        if (!obj.has(memberName)) return null
        val elem = obj.get(memberName)
        if (!elem.isJsonPrimitive) return null
        val prim = elem.asJsonPrimitive
        return if (prim.isString) prim.asString else null
    }

    private fun checkForbiddenFields(element: JsonElement, issues: MutableList<String>) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for (key in obj.keySet()) {
                if (key.lowercase() in FORBIDDEN_FIELDS) {
                    issues.add("Forbidden dangerous field '$key' rejected")
                }
                checkForbiddenFields(obj.get(key), issues)
            }
        } else if (element.isJsonArray) {
            val arr = element.asJsonArray
            for (i in 0 until arr.size()) {
                checkForbiddenFields(arr.get(i), issues)
            }
        }
    }
}
