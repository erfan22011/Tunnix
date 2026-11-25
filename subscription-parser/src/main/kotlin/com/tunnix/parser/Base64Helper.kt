package com.tunnix.parser

import java.nio.charset.StandardCharsets
import java.util.Base64

object Base64Helper {

    /**
     * Decodes Base64 or URL-Safe Base64 with padding auto-fix.
     * Returns decoded String or null if input is not valid Base64.
     */
    fun decodeToString(input: String): String? {
        val clean = input.trim()
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")

        if (clean.isEmpty()) return null

        // Try standard decoder first
        try {
            val bytes = Base64.getDecoder().decode(clean)
            val result = String(bytes, StandardCharsets.UTF_8)
            if (isValidDecodedText(result)) return result
        } catch (_: Exception) {
            // ignore and try URL-safe or padding fix
        }

        // Try URL-safe decoder
        try {
            val bytes = Base64.getUrlDecoder().decode(clean)
            val result = String(bytes, StandardCharsets.UTF_8)
            if (isValidDecodedText(result)) return result
        } catch (_: Exception) {
            // ignore
        }

        // Try fixing missing padding
        val padded = fixPadding(clean.replace('-', '+').replace('_', '/'))
        return try {
            val bytes = Base64.getDecoder().decode(padded)
            val result = String(bytes, StandardCharsets.UTF_8)
            if (isValidDecodedText(result)) result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fixPadding(s: String): String {
        val remainder = s.length % 4
        return if (remainder > 0) {
            s + "=".repeat(4 - remainder)
        } else {
            s
        }
    }

    private fun isValidDecodedText(s: String): Boolean {
        // Must contain printable ASCII/UTF-8 characters and no heavy binary null bytes
        if (s.isEmpty()) return false
        val nullCount = s.count { it == '\u0000' }
        return nullCount < s.length * 0.05
    }
}
