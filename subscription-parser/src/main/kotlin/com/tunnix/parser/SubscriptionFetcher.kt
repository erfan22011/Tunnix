package com.tunnix.parser

import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.SubscriptionDomainRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class SubscriptionFetcher(
    private val client: OkHttpClient = defaultClient()
) {

    companion object {
        const val MAX_RESPONSE_BODY_BYTES = 2 * 1024 * 1024 // 2 Megabytes
        const val MAX_REDIRECT_DEPTH = 3

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false) // Redirects are manually and strictly audited in fetch loop
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    suspend fun fetch(input: String): Result<List<ServerConfig>> = withContext(Dispatchers.IO) {
        val validationResult = SubscriptionDomainRules.normalizeAndValidate(input)
        if (validationResult.isFailure) {
            val rawMsg = validationResult.exceptionOrNull()?.message
            return@withContext Result.failure(
                SubscriptionParserException.InvalidDomainException(rawMsg)
            )
        }

        var currentUrl = validationResult.getOrThrow()
        var redirectCount = 0

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("User-Agent", "Tunnix/1.0.7 (Android; v2rayNG/1.8.12)")
                .header("Accept", "*/*")
                .build()

            try {
                val call = client.newCall(request)
                call.execute().use { response ->
                    // 1. Audit and follow redirects securely
                    if (response.isRedirect) {
                        redirectCount++
                        if (redirectCount > MAX_REDIRECT_DEPTH) {
                            return@withContext Result.failure(
                                SubscriptionParserException.NetworkException("تعداد ریدایرکت‌ها بیش از حد مجاز است (احتمال Redirect Loop) / Too many redirects")
                            )
                        }

                        val locationHeader = response.header("Location")
                        if (locationHeader.isNullOrBlank()) {
                            return@withContext Result.failure(
                                SubscriptionParserException.NetworkException("پاسخ ریدایرکت فاقد هدر Location است / Redirect missing Location header")
                            )
                        }

                        // Resolve both relative and absolute redirect targets safely
                        val targetHttpUrl = response.request.url.resolve(locationHeader)
                            ?: return@withContext Result.failure(
                                SubscriptionParserException.NetworkException("آدرس ریدایرکت نامعتبر است / Invalid redirect target")
                            )

                        // Disallow HTTPS -> HTTP Downgrade
                        if (!targetHttpUrl.isHttps) {
                            return@withContext Result.failure(
                                SubscriptionParserException.NetworkException("ریدایرکت به پروتکل ناامن HTTP رد شد / Insecure HTTP redirect rejected")
                            )
                        }

                        // Strictly re-validate the target redirect URL against domain whitelist and SSRF checks
                        val redirectValidation = SubscriptionDomainRules.normalizeAndValidate(targetHttpUrl.toString())
                        if (redirectValidation.isFailure) {
                            val sanitizedTarget = SubscriptionDomainRules.sanitizeUrl(targetHttpUrl.toString())
                            return@withContext Result.failure(
                                SubscriptionParserException.InvalidDomainException(
                                    "ریدایرکت به مقصد غیرمجاز رد شد: $sanitizedTarget"
                                )
                            )
                        }

                        currentUrl = redirectValidation.getOrThrow()
                        return@use // continue loop with new currentUrl
                    }

                    if (!response.isSuccessful) {
                        val sanitized = SubscriptionDomainRules.sanitizeUrl(currentUrl)
                        return@withContext Result.failure(
                            SubscriptionParserException.NetworkException("HTTP ${response.code}: ${response.message} ($sanitized)")
                        )
                    }

                    val body = response.body
                        ?: return@withContext Result.failure(SubscriptionParserException.EmptySubscriptionException())

                    // 2. Anti-DoS: Check Content-Length header
                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_RESPONSE_BODY_BYTES) {
                        return@withContext Result.failure(
                            SubscriptionParserException.NetworkException(
                                "حجم پاسخ سابسکریپشن بیش از حد مجاز است (حداکثر ۲ مگابایت) / Response exceeds 2MB limit"
                            )
                        )
                    }

                    // 3. Anti-DoS: Bounded stream reading
                    val source = body.source()
                    val bodyBytes = source.readByteArray(MAX_RESPONSE_BODY_BYTES.toLong() + 1)
                    if (bodyBytes.size > MAX_RESPONSE_BODY_BYTES) {
                        return@withContext Result.failure(
                            SubscriptionParserException.NetworkException(
                                "حجم محتوای سابسکریپشن از سقف ۲ مگابایت فراتر رفت / Content exceeds 2MB limit"
                            )
                        )
                    }

                    val bodyString = String(bodyBytes, StandardCharsets.UTF_8).trim()
                    if (bodyString.isEmpty()) {
                        return@withContext Result.failure(SubscriptionParserException.EmptySubscriptionException())
                    }

                    return try {
                        val servers = SubscriptionParser.parse(bodyString)
                        Result.success(servers)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            } catch (e: IOException) {
                val sanitizedUrl = SubscriptionDomainRules.sanitizeUrl(currentUrl)
                return@withContext Result.failure(
                    SubscriptionParserException.NetworkException("خطای ارتباط شبکه ($sanitizedUrl): ${e.message}", e)
                )
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }
    }
}
