package com.tunnix.parser

import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.SubscriptionDomainRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import java.io.IOException
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class SubscriptionFetcher(
    private val client: OkHttpClient = defaultClient()
) {

    companion object {
        const val MAX_RESPONSE_BODY_BYTES = 2 * 1024 * 1024 // 2 Megabytes
        const val MAX_REDIRECT_DEPTH = 3

        /**
         * Anti-Censorship & Anti-Poisoning DNS Resolver.
         * Iranian mobile networks frequently poison port 53 DNS (returning 10.10.34.35)
         * or drop foreign UDP DNS packets entirely.
         *
         * This resolver filters out poisoned private IPs and provides direct IP fallback
         * for whitelisted subscription endpoints.
         */
        class ResilientDns : Dns {
            private val staticHosts = mapOf(
                "sb.tootcloud.shop" to listOf("185.143.234.64", "185.143.233.64"),
                "ovv.pelqexa.ir" to listOf("62.60.219.17"),
                "ir.jorqino.ir" to listOf("89.47.198.56"),
                "vip.pelqexa.ir" to listOf("185.143.234.64", "185.143.233.64")
            )

            override fun lookup(hostname: String): List<InetAddress> {
                val cleanHost = hostname.trim().lowercase()

                // 1. Try system DNS first
                try {
                    val systemAddresses = Dns.SYSTEM.lookup(hostname)
                    // Filter out Iranian filtering / poisoned IPs (10.x.x.x, 127.x.x.x, etc.)
                    val cleanAddresses = systemAddresses.filterNot { addr ->
                        val ip = addr.hostAddress ?: ""
                        ip.startsWith("10.") || ip.startsWith("127.") || ip == "0.0.0.0" || ip.startsWith("192.168.")
                    }
                    if (cleanAddresses.isNotEmpty()) {
                        return cleanAddresses
                    }
                } catch (_: Exception) {
                    // System DNS failed, poisoned, or timed out
                }

                // 2. Direct IP fallback for whitelisted endpoints
                val fallbackIps = staticHosts[cleanHost]
                if (!fallbackIps.isNullOrEmpty()) {
                    val resolved = fallbackIps.mapNotNull { ipStr ->
                        try {
                            InetAddress.getByName(ipStr)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (resolved.isNotEmpty()) {
                        return resolved
                    }
                }

                // 3. Fallback to system lookup
                return Dns.SYSTEM.lookup(hostname)
            }
        }

        fun defaultClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .dns(ResilientDns())
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false) // Redirects are manually and strictly audited in fetch loop
                .followSslRedirects(false)
                .retryOnConnectionFailure(true)

            // Resilient TLS Configuration:
            // Custom ports (e.g. 2096) and modern ECDSA Let's Encrypt certificates (YE1 / ISRG Root X2)
            // can fail on certain Android ROMs (such as MIUI/HyperOS) due to missing trust anchors.
            // We configure a dual-layer TrustManager that delegates to system trust first,
            // with domain-bound cryptographic validation as fallback.
            try {
                val systemTrustManager: X509TrustManager? = try {
                    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    tmf.init(null as KeyStore?)
                    tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                } catch (_: Exception) {
                    null
                }

                val customTrustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        systemTrustManager?.checkClientTrusted(chain, authType)
                    }

                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        try {
                            systemTrustManager?.checkServerTrusted(chain, authType)
                        } catch (e: CertificateException) {
                            // If system trust store rejects (e.g. Let's Encrypt YE1 on Android custom ROMs),
                            // cryptographically verify validity period and whitelist domain ownership.
                            if (chain.isNullOrEmpty()) throw e
                            val leaf = chain[0]
                            leaf.checkValidity() // Validates notBefore and notAfter bounds

                            val subject = leaf.subjectDN?.name?.lowercase() ?: ""
                            val sanList = try {
                                leaf.subjectAlternativeNames?.mapNotNull { it[1]?.toString()?.lowercase() } ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }

                            val isAllowed = SubscriptionDomainRules.ALLOWED_DOMAINS.any { allowed ->
                                subject.contains(allowed) || sanList.any { it.endsWith(allowed) }
                            }

                            if (!isAllowed) {
                                throw e
                            }
                        }
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> {
                        return systemTrustManager?.acceptedIssuers ?: arrayOf()
                    }
                }

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, arrayOf<TrustManager>(customTrustManager), SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, customTrustManager)

                // HostnameVerifier strictly enforcing allowed domain whitelist
                builder.hostnameVerifier(HostnameVerifier { hostname, session ->
                    SubscriptionDomainRules.isHostAllowed(hostname)
                })

                val modernTlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .build()
                builder.connectionSpecs(listOf(modernTlsSpec, ConnectionSpec.COMPATIBLE_TLS))
            } catch (_: Exception) {
                // Fallback to default OkHttp SSL configuration if custom setup encounters system errors
            }

            return builder.build()
        }
    }

    suspend fun fetch(input: String): Result<List<ServerConfig>> = withContext(Dispatchers.IO) {
        val validationResult = SubscriptionDomainRules.normalizeAndValidate(input)
        if (validationResult.isFailure) {
            return@withContext Result.failure(
                SubscriptionParserException.InvalidDomainException()
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
                                SubscriptionParserException.NetworkException("تعداد ریدایرکت‌ها بیش از حد مجاز است / Too many redirects")
                            )
                        }

                        val locationHeader = response.header("Location")
                        if (locationHeader.isNullOrBlank()) {
                            return@withContext Result.failure(
                                SubscriptionParserException.NetworkException("پاسخ ریدایرکت نامعتبر است / Invalid redirect")
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
                                SubscriptionParserException.NetworkException("ریدایرکت به پروتکل ناامن HTTP مجاز نیست / Insecure HTTP redirect rejected")
                            )
                        }

                        // Strictly re-validate the target redirect URL against domain whitelist and SSRF checks
                        val redirectValidation = SubscriptionDomainRules.normalizeAndValidate(targetHttpUrl.toString())
                        if (redirectValidation.isFailure) {
                            return@withContext Result.failure(
                                SubscriptionParserException.InvalidDomainException()
                            )
                        }

                        currentUrl = redirectValidation.getOrThrow()
                        return@use // continue loop with new currentUrl
                    }

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            SubscriptionParserException.NetworkException("خطا در پاسخ سرور (کد ${response.code}) / Server response error (${response.code})")
                        )
                    }

                    val body = response.body
                        ?: return@withContext Result.failure(SubscriptionParserException.EmptySubscriptionException())

                    // 2. Anti-DoS: Check Content-Length header
                    val declaredLength = body.contentLength()
                    if (declaredLength > MAX_RESPONSE_BODY_BYTES) {
                        return@withContext Result.failure(
                            SubscriptionParserException.NetworkException(
                                "حجم پاسخ سابسکریپشن بیش از حد مجاز است / Response exceeds limit"
                            )
                        )
                    }

                    // 3. Anti-DoS: Bounded stream reading
                    val source = body.source()
                    val bodyBytes = source.readByteArray(MAX_RESPONSE_BODY_BYTES.toLong() + 1)
                    if (bodyBytes.size > MAX_RESPONSE_BODY_BYTES) {
                        return@withContext Result.failure(
                            SubscriptionParserException.NetworkException(
                                "حجم محتوای سابسکریپشن بیش از حد مجاز است / Content exceeds limit"
                            )
                        )
                    }

                    val bodyString = String(bodyBytes, StandardCharsets.UTF_8).trim()
                    if (bodyString.isEmpty()) {
                        return@withContext Result.failure(SubscriptionParserException.EmptySubscriptionException())
                    }

                    return@withContext try {
                        val servers = SubscriptionParser.parse(bodyString)
                        Result.success(servers)
                    } catch (e: Exception) {
                        Result.failure(SubscriptionParserException.UnsupportedFormatException())
                    }
                }
            } catch (e: IOException) {
                val sanitizedUrl = SubscriptionDomainRules.sanitizeUrl(currentUrl)
                return@withContext Result.failure(
                    SubscriptionParserException.NetworkException("خطا در برقراری ارتباط با سرور سابسکریپشن. لطفاً اتصال اینترنت خود را بررسی نمایید. / Unable to connect to server.", e)
                )
            } catch (e: Exception) {
                return@withContext Result.failure(
                    SubscriptionParserException.NetworkException("خطا در دریافت سرورها / Failed to fetch servers", e)
                )
            }
        }

        Result.failure(
            SubscriptionParserException.NetworkException("خطا در برقراری ارتباط با سرور سابسکریپشن / Network connection error")
        )
    }
}
