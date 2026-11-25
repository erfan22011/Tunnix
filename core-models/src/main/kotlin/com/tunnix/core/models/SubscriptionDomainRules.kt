package com.tunnix.core.models

import java.net.URI

object SubscriptionDomainRules {

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
     */
    fun isHostAllowed(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val cleanHost = host.trim().lowercase()
        return ALLOWED_DOMAINS.any { allowed ->
            cleanHost == allowed || cleanHost.endsWith(".$allowed")
        }
    }

    /**
     * Takes raw user input (which can be a full URL, or just a redeem code / sub token)
     * and normalizes it into a valid subscription URL if valid.
     *
     * Example 1: "iygsr9fn68393003" -> "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003"
     * Example 2: "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003" -> validates host and returns URL
     *
     * Returns Result.success(normalizedUrl) or Result.failure(Exception).
     */
    fun normalizeAndValidate(input: String): Result<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("ورودی خالی است / Input is empty"))
        }

        // If the input does not look like a URL (no scheme), treat it as a token/redeem code.
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            // Check if token contains invalid path chars
            val token = trimmed.removePrefix("/sub/").removePrefix("sub/").trim()
            if (token.isEmpty() || token.contains(" ")) {
                return Result.failure(IllegalArgumentException("کد نامعتبر است / Invalid redeem code"))
            }
            val constructedUrl = "https://$DEFAULT_SUB_HOST:$DEFAULT_SUB_PORT$DEFAULT_SUB_PATH_PREFIX$token"
            return Result.success(constructedUrl)
        }

        // Parse as URI
        return try {
            val uri = URI(trimmed)
            val host = uri.host
            if (isHostAllowed(host)) {
                Result.success(trimmed)
            } else {
                Result.failure(
                    IllegalArgumentException(
                        "دامنه '$host' مجاز نیست. فقط دامنه‌های تحت tootcloud.shop، jorqino.ir، genus.ir و pelqexa.ir مجاز هستند."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("فرمت لینک نامعتبر است / Invalid URL format: ${e.message}"))
        }
    }
}
