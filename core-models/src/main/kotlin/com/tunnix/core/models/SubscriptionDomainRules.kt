package com.tunnix.core.models

import java.net.URI

object SubscriptionDomainRules {

    const val MAX_INPUT_LENGTH = 2048
    const val MIN_TOKEN_LENGTH = 1
    const val MAX_TOKEN_LENGTH = 256

    private val TOKEN_REGEX = Regex("^[a-zA-Z0-9_./-]{1,256}$")

    /**
     * Allowed root domains and their subdomains.
     */
    val ALLOWED_DOMAINS = listOf(
        "tootcloud.shop",
        "jorqino.ir",
        "genus.ir",
        "pelqexa.ir"
    )

    /**
     * Default host and port used when user enters only a subscription key / redeem code.
     */
    const val DEFAULT_SUB_HOST = "sb.tootcloud.shop"
    const val DEFAULT_SUB_PORT = 2096
    const val DEFAULT_SUB_PATH_PREFIX = "/sub/"

    /**
     * Verifies if a given hostname belongs to the allowed domains or their subdomains.
     * Prevents SSRF attacks to localhost and internal networks.
     */
    fun isHostAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val cleanHost = host.trim().lowercase()

        // Block SSRF: Reject localhost, internal loopbacks and private IP addresses
        if (cleanHost == "localhost" || cleanHost == "127.0.0.1" || cleanHost == "::1" || cleanHost == "[::1]" || cleanHost == "0.0.0.0" ||
            cleanHost.startsWith("127.") || cleanHost.startsWith("10.") || cleanHost.startsWith("192.168.") ||
            cleanHost.startsWith("169.254.") || cleanHost.startsWith("100.64.") ||
            cleanHost.endsWith(".local") || cleanHost.endsWith(".internal") || cleanHost.endsWith(".lan")) {
            return false
        }

        // Block RFC 1918 172.16.0.0 - 172.31.255.255
        if (cleanHost.startsWith("172.")) {
            val parts = cleanHost.split(".")
            if (parts.size >= 2) {
                val secondOctet = parts[1].toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) {
                    return false
                }
            }
        }

        return ALLOWED_DOMAINS.any { allowed ->
            cleanHost == allowed || cleanHost.endsWith(".$allowed")
        }
    }

    /**
     * Sanitizes a subscription URL by redacting the private token segment from logs and errors.
     */
    fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return try {
            val uri = URI(url)
            val host = uri.host ?: ""
            val portStr = if (uri.port != -1 && uri.port != 443 && uri.port != 80) ":${uri.port}" else ""
            if (host.isNotEmpty()) "https://$host$portStr/[REDACTED]" else "[REDACTED]"
        } catch (_: Exception) {
            "[REDACTED]"
        }
    }

    /**
     * Takes raw user input (which can be a full URL, or just a redeem code / sub token)
     * and normalizes it into a valid, safe HTTPS subscription URL.
     *
     * Example 1: "iygsr9fn68393003" -> "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003"
     * Example 2: "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003" -> validates host and returns URL
     *
     * Returns Result.success(normalizedUrl) or Result.failure(Exception).
     */
    fun normalizeAndValidate(input: String): Result<String> {
        var trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("ورودی خالی است / Input is empty"))
        }

        if (trimmed.length > MAX_INPUT_LENGTH) {
            return Result.failure(IllegalArgumentException("طول ورودی بیش از حد مجاز است / Input exceeds length limit"))
        }

        // Strict HTTPS enforcement: Insecure HTTP is rejected
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return Result.failure(
                IllegalArgumentException("استفاده از پروتکل ناامن HTTP مجاز نیست. فقط HTTPS پشتیبانی می‌شود / Insecure HTTP rejected. Only HTTPS is allowed.")
            )
        }

        // If user typed domain without scheme, prepend https://
        val hasKnownDomain = ALLOWED_DOMAINS.any { allowed ->
            trimmed.startsWith(allowed, ignoreCase = true) || trimmed.contains(".$allowed", ignoreCase = true)
        }
        if (hasKnownDomain && (trimmed.contains("/") || trimmed.contains(":")) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "https://$trimmed"
        }

        // If the input is a full HTTPS URL
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "https") {
                    return Result.failure(IllegalArgumentException("فقط پروتکل امن HTTPS مجاز است / Only HTTPS protocol is allowed"))
                }

                val host = uri.host
                if (!isHostAllowed(host)) {
                    return Result.failure(
                        IllegalArgumentException("دامنه وارد شده مجاز نیست / Domain not allowed")
                    )
                }

                val port = uri.port
                if (port != -1 && (port < 1 || port > 65535)) {
                    return Result.failure(IllegalArgumentException("پورت وارد شده نامعتبر است / Invalid port range"))
                }

                // Verify path does not contain directory traversal sequences
                val rawPath = uri.rawPath ?: ""
                if (rawPath.contains("..") || rawPath.contains("//")) {
                    return Result.failure(IllegalArgumentException("مسیر وارد شده نامعتبر است / Invalid path traversal in URL"))
                }

                Result.success(trimmed)
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("فرمت لینک نامعتبر است / Invalid URL format: ${e.message}"))
            }
        }

        // If the input does not look like a URL (no scheme), treat it as a token/redeem code.
        val token = trimmed.removePrefix("/sub/").removePrefix("sub/").trim()
        if (token.isEmpty() || token.contains(" ") || token.contains("..")) {
            return Result.failure(IllegalArgumentException("کد نامعتبر است / Invalid redeem code"))
        }

        if (token.length < MIN_TOKEN_LENGTH || token.length > MAX_TOKEN_LENGTH) {
            return Result.failure(
                IllegalArgumentException("طول کد اشتراک باید بین $MIN_TOKEN_LENGTH تا $MAX_TOKEN_LENGTH کاراکتر باشد / Token length invalid")
            )
        }

        if (!TOKEN_REGEX.matches(token)) {
            return Result.failure(
                IllegalArgumentException("کد اشتراک نامعتبر است یا شامل کاراکترهای غیرمجاز می‌باشد / Invalid redeem code characters")
            )
        }

        // Safe URI construction via Java URI API
        return try {
            val constructedUri = URI(
                "https",
                null,
                DEFAULT_SUB_HOST,
                DEFAULT_SUB_PORT,
                "$DEFAULT_SUB_PATH_PREFIX$token",
                null,
                null
            )
            Result.success(constructedUri.toASCIIString())
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("خطا در ساخت آدرس امن اشتراک / Failed to construct safe URL: ${e.message}"))
        }
    }
}
