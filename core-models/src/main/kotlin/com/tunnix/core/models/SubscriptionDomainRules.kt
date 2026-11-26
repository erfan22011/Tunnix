package com.tunnix.core.models

import java.net.URI

object SubscriptionDomainRules {

    const val MAX_INPUT_LENGTH = 2048
    const val MIN_TOKEN_LENGTH = 3
    const val MAX_TOKEN_LENGTH = 128

    private val TOKEN_REGEX = Regex("^[a-zA-Z0-9_-]{3,128}$")

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
        if (cleanHost == "localhost" || cleanHost == "127.0.0.1" || cleanHost == "::1" ||
            cleanHost.startsWith("10.") || cleanHost.startsWith("192.168.") ||
            cleanHost.endsWith(".local") || cleanHost.endsWith(".internal") || cleanHost.endsWith(".lan")) {
            return false
        }

        return ALLOWED_DOMAINS.any { allowed ->
            cleanHost == allowed || cleanHost.endsWith(".$allowed")
        }
    }

    /**
     * Sanitizes a subscription URL by redacting the private token segment from logs and errors.
     * Example: "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003" -> "https://sb.tootcloud.shop:2096/sub/[REDACTED]"
     */
    fun sanitizeUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.replace(Regex("/sub/[a-zA-Z0-9_.-]+"), "/sub/[REDACTED]")
    }

    /**
     * Takes raw user input (which can be a full URL, or just a redeem code / sub token)
     * and normalizes it into a valid, safe HTTPS subscription URL.
     *
     * Security controls:
     * 1. Max input length (<= 2048 chars)
     * 2. Strict HTTPS enforcement (Insecure HTTP is strictly rejected)
     * 3. Allowed domain whitelist with SSRF protection
     * 4. Safe URI construction using Java standard URI constructor (no string interpolation vulnerabilities)
     * 5. Strict token character whitelisting and length bounding
     *
     * Returns Result.success(normalizedUrl) or Result.failure(Exception).
     */
    fun normalizeAndValidate(input: String): Result<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("ورودی خالی است / Input is empty"))
        }

        if (trimmed.length > MAX_INPUT_LENGTH) {
            return Result.failure(IllegalArgumentException("طول ورودی بیش از حد مجاز است / Input exceeds length limit"))
        }

        // Strict HTTPS enforcement: Insecure HTTP is rejected immediately
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return Result.failure(
                IllegalArgumentException("استفاده از پروتکل ناامن HTTP مجاز نیست. فقط HTTPS پشتیبانی می‌شود / Insecure HTTP protocol rejected. Only HTTPS is allowed.")
            )
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

        // If input does not start with scheme, treat it as a redeem code / sub token
        val token = trimmed.removePrefix("/sub/").removePrefix("sub/").trim()
        if (token.length < MIN_TOKEN_LENGTH || token.length > MAX_TOKEN_LENGTH) {
            return Result.failure(
                IllegalArgumentException("طول کد اشتراک باید بین $MIN_TOKEN_LENGTH تا $MAX_TOKEN_LENGTH کاراکتر باشد / Token length must be between $MIN_TOKEN_LENGTH and $MAX_TOKEN_LENGTH")
            )
        }

        if (!TOKEN_REGEX.matches(token)) {
            return Result.failure(
                IllegalArgumentException("کد اشتراک نامعتبر است یا شامل کاراکترهای غیرمجاز می‌باشد / Invalid redeem code characters")
            )
        }

        // Safe URI construction via Java URI API (guarantees safe encoding and eliminates injection)
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
