package com.tunnix.parser

import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.SubscriptionDomainRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        val url = validationResult.getOrThrow()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Tunnix/1.0.0 (Android; v2rayNG/1.8.12)")
            .header("Accept", "*/*")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        SubscriptionParserException.NetworkException("HTTP ${response.code}: ${response.message}")
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext Result.failure(SubscriptionParserException.EmptySubscriptionException())
                }

                try {
                    val servers = SubscriptionParser.parse(body)
                    Result.success(servers)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } catch (e: IOException) {
            Result.failure(SubscriptionParserException.NetworkException(e.message ?: "Network failure", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private val redirectCount = ThreadLocal<Int>()

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    redirectCount.set(0)
                    try {
                        chain.proceed(chain.request())
                    } finally {
                        redirectCount.remove()
                    }
                }
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (response.isRedirect) {
                        val count = (redirectCount.get() ?: 0) + 1
                        redirectCount.set(count)
                        if (count > MAX_REDIRECTS) {
                            throw IOException("تعداد ریدایرکت‌ها بیش از حد مجاز است / Too many redirects: $count")
                        }
                        val location = response.header("Location")
                        if (!location.isNullOrBlank()) {
                            val validation = SubscriptionDomainRules.normalizeAndValidate(location)
                            if (validation.isFailure) {
                                throw IOException("ریدایرکت به دامنه غیرمجاز رد شد / Redirect to unauthorized domain rejected: $location")
                            }
                        }
                    }
                    response
                }
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
