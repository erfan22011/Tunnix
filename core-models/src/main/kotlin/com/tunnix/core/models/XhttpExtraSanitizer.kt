package com.tunnix.core.models

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Sanitizer for XHTTP transport "extra" parameters.
 * Allows all standard and safe keys while filtering forbidden execution fields
 * and performing deep sanitization on any nested downloadSettings.
 */
object XhttpExtraSanitizer {

    private const val MAX_EXTRA_BYTES = 8192 // 8 KB
    private const val MAX_DEPTH = 4

    private val FORBIDDEN_KEYS = setOf(
        "sockopt",
        "command",
        "exec",
        "script",
        "browserforwarder",
        "reverse"
    )

    private val ALLOWED_DL_ROOT_KEYS = setOf(
        "address",
        "port",
        "network",
        "security",
        "tlsSettings",
        "realitySettings",
        "xhttpSettings"
    )

    private val ALLOWED_DL_TLS_KEYS = setOf(
        "serverName",
        "alpn",
        "fingerprint",
        "pinnedPeerCertSha256"
    )

    private val ALLOWED_DL_REALITY_KEYS = setOf(
        "publicKey",
        "shortId",
        "serverName",
        "fingerprint",
        "spiderX"
    )

    private val ALLOWED_DL_XHTTP_KEYS = setOf(
        "host",
        "path",
        "mode",
        "extra"
    )

    /**
     * Sanitizes extraJson string returning a clean, safe JsonObject.
     * Returns empty JsonObject if parsing fails, input is too large, or nesting is too deep.
     */
    fun sanitize(extraJson: String): JsonObject {
        if (extraJson.isBlank() || extraJson.length > MAX_EXTRA_BYTES) {
            return JsonObject()
        }

        val parsedElement: JsonElement = try {
            JsonParser.parseString(extraJson)
        } catch (_: Exception) {
            return JsonObject()
        }

        if (!parsedElement.isJsonObject) {
            return JsonObject()
        }

        val root = parsedElement.asJsonObject
        if (calculateDepth(root) > MAX_DEPTH) {
            return JsonObject()
        }

        val sanitized = JsonObject()
        for ((key, value) in root.entrySet()) {
            if (isForbiddenKey(key)) continue

            if (key == "downloadSettings" && value.isJsonObject) {
                val cleanDl = sanitizeDownloadSettings(value.asJsonObject)
                if (cleanDl != null) {
                    sanitized.add(key, cleanDl)
                }
            } else if (value.isJsonObject) {
                sanitized.add(key, sanitizeGenericObject(value.asJsonObject))
            } else {
                sanitized.add(key, value)
            }
        }

        return sanitized
    }

    private fun isForbiddenKey(key: String): Boolean {
        val lower = key.lowercase()
        return FORBIDDEN_KEYS.any { lower.contains(it) }
    }

    private fun sanitizeGenericObject(obj: JsonObject): JsonObject {
        val clean = JsonObject()
        for ((k, v) in obj.entrySet()) {
            if (isForbiddenKey(k)) continue
            if (v.isJsonObject) {
                clean.add(k, sanitizeGenericObject(v.asJsonObject))
            } else {
                clean.add(k, v)
            }
        }
        return clean
    }

    /**
     * Performs deep sanitization on downloadSettings.
     * Returns null (dropping downloadSettings) if address is blank or loopback/private,
     * or if port is invalid.
     */
    fun sanitizeDownloadSettings(dlObj: JsonObject): JsonObject? {
        val addressElem = dlObj.get("address")
        val address = if (addressElem != null && addressElem.isJsonPrimitive) addressElem.asString else ""

        // If address is blank or loopback/private/internal, drop entire downloadSettings safely
        if (address.isBlank() || IpAddressSecurity.isPrivateOrLoopback(address)) {
            return null
        }

        val portElem = dlObj.get("port")
        val port = if (portElem != null && portElem.isJsonPrimitive && portElem.asJsonPrimitive.isNumber) {
            portElem.asInt
        } else {
            -1
        }
        if (port !in 1..65535) {
            return null
        }

        val clean = JsonObject()
        for ((key, value) in dlObj.entrySet()) {
            if (key !in ALLOWED_DL_ROOT_KEYS || isForbiddenKey(key)) continue

            when (key) {
                "address" -> clean.addProperty("address", address)
                "port" -> clean.addProperty("port", port)
                "network" -> clean.addProperty("network", value.asString)
                "security" -> clean.addProperty("security", value.asString)
                "tlsSettings" -> {
                    if (value.isJsonObject) {
                        val tls = JsonObject()
                        for ((tk, tv) in value.asJsonObject.entrySet()) {
                            if (tk in ALLOWED_DL_TLS_KEYS && !isForbiddenKey(tk)) {
                                tls.add(tk, tv)
                            }
                        }
                        // Enforce allowInsecure = false
                        tls.addProperty("allowInsecure", false)
                        clean.add("tlsSettings", tls)
                    }
                }
                "realitySettings" -> {
                    if (value.isJsonObject) {
                        val reality = JsonObject()
                        for ((rk, rv) in value.asJsonObject.entrySet()) {
                            if (rk in ALLOWED_DL_REALITY_KEYS && !isForbiddenKey(rk)) {
                                reality.add(rk, rv)
                            }
                        }
                        clean.add("realitySettings", reality)
                    }
                }
                "xhttpSettings" -> {
                    if (value.isJsonObject) {
                        val xhttp = JsonObject()
                        for ((xk, xv) in value.asJsonObject.entrySet()) {
                            if (xk == "extra" && xv.isJsonObject) {
                                // Do not allow nested downloadSettings inside xhttpSettings.extra to prevent recursion
                                val subExtra = sanitizeGenericObject(xv.asJsonObject)
                                subExtra.remove("downloadSettings")
                                xhttp.add("extra", subExtra)
                            } else if (xk in ALLOWED_DL_XHTTP_KEYS && !isForbiddenKey(xk)) {
                                xhttp.add(xk, xv)
                            }
                        }
                        clean.add("xhttpSettings", xhttp)
                    }
                }
            }
        }

        return clean
    }

    private fun calculateDepth(element: JsonElement): Int {
        if (!element.isJsonObject) return 1
        var maxSubDepth = 0
        for ((_, v) in element.asJsonObject.entrySet()) {
            if (v.isJsonObject) {
                maxSubDepth = maxOf(maxSubDepth, calculateDepth(v))
            }
        }
        return 1 + maxSubDepth
    }
}
