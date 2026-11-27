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
    private const val MAX_DEPTH = 8

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
        return try {
            if (extraJson.isBlank()) {
                return JsonObject()
            }
            if (extraJson.length > MAX_EXTRA_BYTES) {
                android.util.Log.d("XhttpExtraSanitizer", "Payload size ${extraJson.length} exceeds limit $MAX_EXTRA_BYTES")
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

            val sanitized = JsonObject()
            for ((key, value) in root.entrySet()) {
                if (isForbiddenKey(key)) continue

                if (key == "downloadSettings" && value.isJsonObject) {
                    val cleanDl = sanitizeDownloadSettings(value.asJsonObject, depth = 2)
                    if (cleanDl != null) {
                        sanitized.add(key, cleanDl)
                    }
                } else if (value.isJsonObject) {
                    val child = sanitizeGenericObject(value.asJsonObject, depth = 2)
                    sanitized.add(key, child)
                } else {
                    sanitized.add(key, value)
                }
            }

            sanitized
        } catch (_: Throwable) {
            JsonObject()
        }
    }

    private fun isForbiddenKey(key: String): Boolean {
        val lower = key.lowercase()
        return lower in FORBIDDEN_KEYS
    }

    private fun sanitizeGenericObject(obj: JsonObject, depth: Int = 1): JsonObject {
        if (depth >= MAX_DEPTH) {
            return JsonObject()
        }
        val clean = JsonObject()
        for ((k, v) in obj.entrySet()) {
            if (isForbiddenKey(k)) continue
            if (v.isJsonObject) {
                val child = sanitizeGenericObject(v.asJsonObject, depth + 1)
                clean.add(k, child)
            } else {
                clean.add(k, v)
            }
        }
        return clean
    }

    /**
     * Performs deep sanitization on downloadSettings.
     * Returns null (dropping downloadSettings) if address is blank or loopback/private,
     * if port is invalid, or if required security settings (tlsSettings/realitySettings) are missing.
     */
    fun sanitizeDownloadSettings(dlObj: JsonObject, depth: Int = 2): JsonObject? {
        return try {
            val addressElem = dlObj.get("address")
            val address = if (addressElem != null && addressElem.isJsonPrimitive && addressElem.asJsonPrimitive.isString) {
                addressElem.asString
            } else {
                return null
            }

            // If address is blank or loopback/private/internal, drop entire downloadSettings safely
            if (address.isBlank() || IpAddressSecurity.isPrivateOrLoopback(address)) {
                return null
            }

            val portElem = dlObj.get("port")
            val port = if (portElem != null && portElem.isJsonPrimitive && portElem.asJsonPrimitive.isNumber) {
                try { portElem.asInt } catch (_: Exception) { return null }
            } else {
                return null
            }
            if (port !in 1..65535) {
                return null
            }

            val clean = JsonObject()
            clean.addProperty("address", address)
            clean.addProperty("port", port)

            val networkElem = dlObj.get("network")
            val network = if (networkElem != null && networkElem.isJsonPrimitive && networkElem.asJsonPrimitive.isString) {
                networkElem.asString.lowercase()
            } else {
                "xhttp"
            }
            if (network != "xhttp") {
                return null
            }
            clean.addProperty("network", network)

            val secElem = dlObj.get("security")
            val security = if (secElem != null && secElem.isJsonPrimitive && secElem.asJsonPrimitive.isString) {
                secElem.asString.lowercase()
            } else {
                "tls"
            }
            if (security !in setOf("tls", "reality", "none")) {
                return null
            }
            clean.addProperty("security", security)

            // tlsSettings
            if (dlObj.has("tlsSettings") && dlObj.get("tlsSettings").isJsonObject) {
                val tls = JsonObject()
                for ((tk, tv) in dlObj.getAsJsonObject("tlsSettings").entrySet()) {
                    if (tk in ALLOWED_DL_TLS_KEYS && !isForbiddenKey(tk)) {
                        tls.add(tk, tv)
                    }
                }
                tls.addProperty("allowInsecure", false)
                clean.add("tlsSettings", tls)
            }

            // realitySettings
            if (dlObj.has("realitySettings") && dlObj.get("realitySettings").isJsonObject) {
                val reality = JsonObject()
                for ((rk, rv) in dlObj.getAsJsonObject("realitySettings").entrySet()) {
                    if (rk in ALLOWED_DL_REALITY_KEYS && !isForbiddenKey(rk)) {
                        reality.add(rk, rv)
                    }
                }
                clean.add("realitySettings", reality)
            }

            // xhttpSettings
            if (dlObj.has("xhttpSettings") && dlObj.get("xhttpSettings").isJsonObject) {
                val xhttp = JsonObject()
                for ((xk, xv) in dlObj.getAsJsonObject("xhttpSettings").entrySet()) {
                    if (xk == "extra" && xv.isJsonObject) {
                        val subExtra = sanitizeGenericObject(xv.asJsonObject, depth = depth + 2)
                        subExtra.remove("downloadSettings")
                        xhttp.add("extra", subExtra)
                    } else if (xk in ALLOWED_DL_XHTTP_KEYS && !isForbiddenKey(xk)) {
                        xhttp.add(xk, xv)
                    }
                }
                clean.add("xhttpSettings", xhttp)
            }

            // P2 FIX: If security is tls, tlsSettings MUST be present. Otherwise drop downloadSettings!
            if (security == "tls" && (!clean.has("tlsSettings") || !clean.get("tlsSettings").isJsonObject)) {
                return null
            }
            // If security is reality, realitySettings MUST be present. Otherwise drop downloadSettings!
            if (security == "reality" && (!clean.has("realitySettings") || !clean.get("realitySettings").isJsonObject)) {
                return null
            }

            clean
        } catch (_: Throwable) {
            null
        }
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
