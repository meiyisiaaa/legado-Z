package io.legado.app.ai.client

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Protocol
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class AiHttpClientTest {
    // Public, generated test identity; the self-signed certificate covers localhost only.
    private val identity = KeyStore.getInstance("PKCS12").apply {
        AiHttpClientTest::class.java.getResourceAsStream("/ai/localhost-test.p12")!!.use {
            load(it, "test-fixture-only".toCharArray())
        }
    }
    private val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(identity) }.trustManagers.filterIsInstance<X509TrustManager>().single()
    private val trustedContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), null)
    }
    private val localClient = aiHttpClient.newBuilder()
        .dns { listOf(InetAddress.getByName("127.0.0.1")) }
        .callTimeout(3, TimeUnit.SECONDS)
        .build()
    private val trustedClient = localClient.newBuilder()
        .sslSocketFactory(trustedContext.socketFactory, trustManager)
        .build()

    @Test fun aiClientHasNoSourceInterceptorsOrAutomaticRedirects() {
        assertTrue(aiHttpClient.interceptors.isEmpty())
        assertTrue(aiHttpClient.networkInterceptors.isEmpty())
        assertFalse(aiHttpClient.followRedirects)
        assertFalse(aiHttpClient.followSslRedirects)
    }

    @Test fun selfSignedServerIsRejectedBeforeSendingCredentials() {
        val request = serveTls { port ->
            try {
                localClient.newCall(request("localhost", port)).execute().use { fail("Untrusted TLS accepted") }
            } catch (_: SSLHandshakeException) {
            }
        }
        assertNull(request)
    }

    @Test fun trustedCertificateForAnotherHostnameIsRejectedBeforeSendingCredentials() {
        val request = serveTls { port ->
            try {
                trustedClient.newCall(request("127.0.0.1", port)).execute().use { fail("Wrong hostname accepted") }
            } catch (_: SSLPeerUnverifiedException) {
            }
        }
        assertNull(request)
    }

    @Test fun trustedLocalhostSucceeds() {
        val request = serveTls { port ->
            trustedClient.newCall(request("localhost", port)).execute().use {
                assertEquals(200, it.code)
                assertEquals("ok", it.body.string())
            }
        }
        assertEquals("GET /v1/models HTTP/1.1", request)
    }

    @Test fun trustedHttpsRedirectIsReturnedWithoutForwardingCredentials() {
        val request = serveTls(status = 302) { port ->
            trustedClient.newCall(request("localhost", port)).execute().use {
                assertEquals(302, it.code)
                assertEquals("https://localhost:$port/redirected", it.header("Location"))
            }
        }
        assertEquals("GET /v1/models HTTP/1.1", request)
    }

    private fun request(host: String, port: Int) = Request.Builder()
        .url("https://$host:$port/v1/models")
        .header("Authorization", "Bearer synthetic-test-key")
        .build()

    private fun serveTls(status: Int = 200, call: (Int) -> Unit): String? {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(identity, "test-fixture-only".toCharArray())
        }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        return MockWebServer().use { server ->
            server.protocols = listOf(Protocol.HTTP_1_1)
            server.useHttps(context.socketFactory)
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse.Builder().code(status).body("ok").apply {
                if (status == 302) addHeader("Location", "https://localhost:${server.port}/redirected")
            }.build())
            call(server.port)
            server.takeRequest(100, TimeUnit.MILLISECONDS)?.requestLine
        }
    }
}
