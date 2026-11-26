package com.tunnix.parser

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFetcherTest {

    private val validSampleConfigBase64 = "dmxlc3M6Ly8wM2ZhMzgxYS0yMWYyLTRiNzUtOWI2ZS1hNDU5NWQ2MjEwNmVAb3Z2LnBlbHFleGEuaXI6MjA5Nj9zZWN1cml0eT10bHMmdHlwZT10Y3AjVGVzdFNlcnZlcg=="

    @Test
    fun `test fetch rejects redirect to disallowed domain`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.host == "sb.tootcloud.shop") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://evil.com/sub/malicious")
                        .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validSampleConfigBase64.toResponseBody("text/plain".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val fetcher = SubscriptionFetcher(client)
        val result = fetcher.fetch("https://sb.tootcloud.shop:2096/sub/initial")

        assertTrue("Redirect to evil.com must fail", result.isFailure)
        assertTrue(
            "Exception must mention disallowed domain or redirect rejection",
            result.exceptionOrNull() is SubscriptionParserException.InvalidDomainException
        )
    }

    @Test
    fun `test fetch rejects redirect to insecure HTTP`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "http://sb.tootcloud.shop:2096/sub/insecure")
                    .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val fetcher = SubscriptionFetcher(client)
        val result = fetcher.fetch("https://sb.tootcloud.shop:2096/sub/initial")

        assertTrue("Insecure HTTP redirect must fail", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("HTTPS") == true
        )
    }

    @Test
    fun `test fetch follows safe relative redirect within allowed domain`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.url.encodedPath == "/sub/initial") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "/sub/final_target")
                        .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                        .build()
                } else if (req.url.encodedPath == "/sub/final_target") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(validSampleConfigBase64.toResponseBody("text/plain".toMediaTypeOrNull()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                        .build()
                }
            }
            .build()

        val fetcher = SubscriptionFetcher(client)
        val result = fetcher.fetch("https://sb.tootcloud.shop:2096/sub/initial")

        assertTrue("Safe relative redirect must succeed", result.isSuccess)
        val servers = result.getOrNull()
        assertTrue("Must parse servers successfully", !servers.isNullOrEmpty())
        assertEquals("TestServer", servers?.first()?.name)
    }

    @Test
    fun `test fetch rejects excessive redirects`() = runBlocking {
        var hop = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                hop++
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://sb.tootcloud.shop:2096/sub/hop_$hop")
                    .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                    .build()
            }
            .build()

        val fetcher = SubscriptionFetcher(client)
        val result = fetcher.fetch("https://sb.tootcloud.shop:2096/sub/initial")

        assertTrue("Excessive redirects must fail", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("تعداد ریدایرکت‌ها") == true
        )
    }
}
