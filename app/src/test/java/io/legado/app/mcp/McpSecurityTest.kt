package io.legado.app.mcp

import org.junit.Assert.*
import org.junit.Test

class McpSecurityTest {
    private val security = McpSecurity(setOf("localhost", "127.0.0.1", "192.168.1.2", "::1"), 1236) { "secret" }
    @Test fun `SDK authorities and origins include port and bracket IPv6`() {
        mapOf("localhost" to "localhost:1236", "127.0.0.1" to "127.0.0.1:1236",
            "::1" to "[::1]:1236").forEach { (host, expected) ->
            val authority = mcpAuthority(host, 1236)
            assertEquals(expected, authority)
            assertTrue(security.acceptsHost(authority))
            assertTrue(security.acceptsOrigin("http://$authority"))
        }
    }
    @Test fun `only exact listening authorities are accepted`() {
        assertTrue(security.acceptsHost("192.168.1.2:1236"))
        assertTrue(security.acceptsHost("[::1]:1236"))
        listOf(null, "localhost", "localhost:1237", "evil.test:1236", "localhost.evil:1236",
            "evil@localhost:1236", "localhost:1236/path").forEach { assertFalse(it, security.acceptsHost(it)) }
    }
    @Test fun `browser origins require exact origin while native clients may omit it`() {
        assertTrue(security.acceptsOrigin(null))
        assertTrue(security.acceptsOrigin("http://localhost:1236"))
        listOf("null", "http://evil.test:1236", "https://localhost:1236", "http://localhost:9999",
            "http://localhost:1236/path", "http://user@localhost:1236").forEach {
            assertFalse(it, security.acceptsOrigin(it))
        }
    }
    @Test fun `token rotation takes effect for every request`() {
        var token = "one"
        val gate = McpSecurity(setOf("localhost"), 1236) { token }
        assertTrue(gate.acceptsAuthorization("Bearer one"))
        token = "two"
        assertFalse(gate.acceptsAuthorization("Bearer one"))
        assertTrue(gate.acceptsAuthorization("Bearer two"))
        assertFalse(gate.acceptsAuthorization(null))
    }
}
