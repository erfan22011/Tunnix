package com.tunnix.core.models

import org.junit.Assert.*
import org.junit.Test

class SubscriptionDomainRulesTest {

    @Test
    fun `test valid full URLs with allowed domains`() {
        val validUrl1 = "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003"
        val res1 = SubscriptionDomainRules.normalizeAndValidate(validUrl1)
        assertTrue(res1.isSuccess)
        assertEquals(validUrl1, res1.getOrNull())

        val validUrl2 = "https://sub.jorqino.ir/sub/token123"
        val res2 = SubscriptionDomainRules.normalizeAndValidate(validUrl2)
        assertTrue(res2.isSuccess)

        val validUrl3 = "https://ovv.genus.ir:2053/sub/abc"
        val res3 = SubscriptionDomainRules.normalizeAndValidate(validUrl3)
        assertTrue(res3.isSuccess)

        val validUrl4 = "https://cdn.pelqexa.ir/sub/xyz"
        val res4 = SubscriptionDomainRules.normalizeAndValidate(validUrl4)
        assertTrue(res4.isSuccess)
    }

    @Test
    fun `test rejected URLs with non-allowed domains`() {
        val disallowedUrl = "https://example.com/sub/token123"
        val res = SubscriptionDomainRules.normalizeAndValidate(disallowedUrl)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("مجاز نیست") == true)
    }

    @Test
    fun `test reject plain HTTP protocol`() {
        val httpUrl = "http://sb.tootcloud.shop:2096/sub/iygsr9fn68393003"
        val res = SubscriptionDomainRules.normalizeAndValidate(httpUrl)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("پروتکل ناامن HTTP") == true)
    }

    @Test
    fun `test reject SSRF loopback and private IPs`() {
        val localhostUrl = "https://localhost:8080/sub/test"
        assertTrue(SubscriptionDomainRules.normalizeAndValidate(localhostUrl).isFailure)

        val ip127Url = "https://127.0.0.1:8080/sub/test"
        assertTrue(SubscriptionDomainRules.normalizeAndValidate(ip127Url).isFailure)

        val ip10Url = "https://10.0.0.1:8080/sub/test"
        assertTrue(SubscriptionDomainRules.normalizeAndValidate(ip10Url).isFailure)
    }

    @Test
    fun `test reject invalid token traversal and characters`() {
        val traversalToken = "../../../etc/passwd"
        assertTrue(SubscriptionDomainRules.normalizeAndValidate(traversalToken).isFailure)

        val queryToken = "token?inject=true"
        assertTrue(SubscriptionDomainRules.normalizeAndValidate(queryToken).isFailure)

        val spaceToken = "token with spaces"
        assertTrue(SubscriptionDomainRules.normalizeAndValidate(spaceToken).isFailure)
    }

    @Test
    fun `test reject excessively large input`() {
        val hugeInput = "a".repeat(2050)
        val res = SubscriptionDomainRules.normalizeAndValidate(hugeInput)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("بیش از حد مجاز") == true)
    }

    @Test
    fun `test URL token sanitization in logs`() {
        val url = "https://sb.tootcloud.shop:2096/sub/mysecrettoken12345"
        val sanitized = SubscriptionDomainRules.sanitizeUrl(url)
        assertEquals("https://sb.tootcloud.shop:2096/sub/[REDACTED]", sanitized)
        assertFalse(sanitized.contains("mysecrettoken12345"))
    }

    @Test
    fun `test redeem code completion`() {
        val token = "iygsr9fn68393003"
        val res = SubscriptionDomainRules.normalizeAndValidate(token)
        assertTrue(res.isSuccess)
        val expected = "https://sb.tootcloud.shop:2096/sub/iygsr9fn68393003"
        assertEquals(expected, res.getOrNull())
    }

    @Test
    fun `test empty input validation`() {
        val res = SubscriptionDomainRules.normalizeAndValidate("   ")
        assertTrue(res.isFailure)
    }

    @Test
    fun `test traffic stats formatting`() {
        val stats = TrafficStats(
            uploadSpeedBytesPerSec = 26757, // ~26.13 KiB/s
            downloadSpeedBytesPerSec = 289484, // ~282.70 KiB/s
            totalUploadBytes = 1363148, // 1.30 MiB
            totalDownloadBytes = 7801405 // 7.44 MiB
        )

        assertEquals("26.13 KiB/s", stats.formattedUploadSpeed)
        assertEquals("282.70 KiB/s", stats.formattedDownloadSpeed)
        assertEquals("1.30 MiB", stats.formattedTotalUpload)
        assertEquals("7.44 MiB", stats.formattedTotalDownload)
    }
}
