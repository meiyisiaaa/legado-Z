package io.legado.app.lib.cronet

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CronetRedirectRequestTest {

    @Test
    fun sameOriginRedirectKeepsAuthorization() {
        val redirected = authenticatedRequest().withRedirectLocation("https://reader.example/book/2")

        assertEquals("Basic reader-secret", redirected.header("Authorization"))
        assertEquals("https://reader.example/book/2", redirected.url.toString())
    }

    @Test
    fun redirectAcrossHostPortOrSchemeRemovesAuthorization() {
        val targets = listOf(
            "https://other.example/book/2",
            "https://reader.example:8443/book/2",
            "http://reader.example/book/2"
        )

        targets.forEach { target ->
            val redirected = authenticatedRequest().withRedirectLocation(target)
            assertNull("Authorization leaked to $target", redirected.header("Authorization"))
        }
    }

    @Test
    fun laterRedirectsUseTheCredentialStateOfThePreviousHop() {
        val firstHop = authenticatedRequest().withRedirectLocation("https://other.example/redirect")
        val secondHop = firstHop.withRedirectLocation("https://third.example/book")

        assertNull(firstHop.header("Authorization"))
        assertNull(secondHop.header("Authorization"))
    }

    private fun authenticatedRequest(): Request = Request.Builder()
        .url("https://reader.example/book/1")
        .header("Authorization", "Basic reader-secret")
        .build()
}
