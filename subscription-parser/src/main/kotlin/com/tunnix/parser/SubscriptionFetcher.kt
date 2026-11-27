package com.tunnix.parser

import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.SubscriptionDomainRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class SubscriptionFetcher(
    private val client: OkHttpClient = defaultClient()
) {

    suspend fun fetch(input: String): Result<List<ServerConfig>> = withContext(Dispatchers.IO) {
        val validationResult = SubscriptionDomainRules.normalizeAndValidate(input)
        if (validationResult.isFailure) {
            return@withContext Result.failure(
                SubscriptionParserException.InvalidDomainException(validationResult.exceptionOrNull()?.message)
            )
        }

        val initialUrlStr = validationResult.getOrThrow()
        val currentUrl: HttpUrl = initialUrlStr.toHttpUrlOrNull()
            ?: return@withContext Result.failure(
                SubscriptionParserException.InvalidDomainException("فرمت آدرس نامعتبر است: $initialUrlStr")
            )

        executeFetchLoop(currentUrl)
    }

    private fun executeFetchLoop(startUrl: HttpUrl): Result<List<ServerConfig>> {
        var currentUrl = startUrl
        var redirects = 0

        while (true) {
            val request = Request.Builder()
                .url(currentUrl)
                // Keep v2rayNG in User-Agent for compatibility with upstream subscription panels and distributors
                .header("User-Agent", "Tunnix/1.0.0 (Android; v2rayNG/1.8.12)")
                .header("Accept", "*/*")
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                return Result.failure(
                    SubscriptionParserException.NetworkException(e.message ?: "Network failure", e)
                )
            } catch (e: Exception) {
                return Result.failure(e)
            }

            if (response.isRedirect) {
                redirects++
                if (redirects > MAX_REDIRECTS) {
                    response.close()
                    return Result.failure(
                        SubscriptionParserException.NetworkException("تعداد ریدایرکت‌ها بیش از حد مجاز است / Too many redirects: $redirects")
                    )
                }

                val locationHeader = response.header("Location")
                response.close()

                if (locationHeader.isNullOrBlank()) {
                    return Result.failure(
                        SubscriptionParserException.NetworkException("ریدایرکت بدون هدر Location / Redirect missing Location header")
                    )
                }

                val resolvedUrl = currentUrl.resolve(locationHeader)
                if (resolvedUrl == null) {
                    return Result.failure(
                        SubscriptionParserException.NetworkException("آدرس ریدایرکت نامعتبر است / Invalid redirect target: $locationHeader")
                    )
                }

                if (resolvedUrl.scheme != "https") {
                    return Result.failure(
                        SubscriptionParserException.NetworkException("ریدایرکت ناامن به پروتکل غیر HTTPS رد شد / Insecure redirect rejected: ${resolvedUrl.scheme}")
                    )
                }

                if (!SubscriptionDomainRules.isHostAllowed(resolvedUrl.host)) {
                    return Result.failure(
                        SubscriptionParserException.InvalidDomainException(
                            "ریدایرکت به دامنه غیرمجاز رد شد / Redirect to unauthorized domain rejected: ${SubscriptionDomainRules.sanitizeUrl(resolvedUrl.toString())}"
                        )
                    )
                }

                currentUrl = resolvedUrl
                continue
            }

            return response.use { resp ->
                if (!resp.isSuccessful) {
                    return@use Result.failure(
                        SubscriptionParserException.NetworkException("HTTP ${resp.code}: ${resp.message}")
                    )
                }

                val responseBody = resp.body
                    ?: return@use Result.failure(SubscriptionParserException.EmptySubscriptionException())

                val source = responseBody.source()
                source.request(MAX_BODY_BYTES + 1)
                if (source.buffer.size > MAX_BODY_BYTES) {
                    return@use Result.failure(
                        SubscriptionParserException.NetworkException("حجم محتوای اشتراک بیش از ۲ مگابایت است / Subscription response exceeds 2MB limit")
                    )
                }

                val body = responseBody.string()
                if (body.isBlank()) {
                    return@use Result.failure(SubscriptionParserException.EmptySubscriptionException())
                }

                try {
                    val servers = SubscriptionParser.parse(body)
                    Result.success(servers)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    companion object {
        const val MAX_REDIRECTS = 5
        const val MAX_BODY_BYTES = 2L * 1024 * 1024 // 2MB limit

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
