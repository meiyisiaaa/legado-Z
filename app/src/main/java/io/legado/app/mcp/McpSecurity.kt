package io.legado.app.mcp

import java.net.URI
import java.security.MessageDigest

internal fun mcpAuthority(host: String, port: Int): String =
    "${if (':' in host) "[$host]" else host}:$port"

/** 不接受转发头、通配域名或仅后缀相同的 Host。 */
class McpSecurity(private val hosts: Set<String>, private val port: Int, private val token: () -> String) {
    fun acceptsHost(header: String?): Boolean = parseAuthority(header)?.let { (host, requestPort) ->
        host in hosts && requestPort == port
    } == true

    fun acceptsOrigin(origin: String?): Boolean {
        if (origin == null) return true // 非浏览器客户端不携带 Origin。
        return runCatching {
            val uri = URI(origin)
            uri.scheme == "http" && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawFragment == null && uri.rawPath.isNullOrEmpty() &&
                uri.host?.lowercase()?.removeSurrounding("[", "]") in hosts &&
                (if (uri.port < 0) 80 else uri.port) == port
        }.getOrDefault(false)
    }

    fun acceptsAuthorization(header: String?): Boolean {
        if (header == null || !header.startsWith("Bearer ")) return false
        return MessageDigest.isEqual(header.removePrefix("Bearer ").toByteArray(Charsets.UTF_8),
            token().toByteArray(Charsets.UTF_8))
    }

    private fun parseAuthority(value: String?): Pair<String, Int>? = runCatching {
        if (value.isNullOrBlank() || value.any { it.isWhitespace() || it in "/\\@?#" }) return null
        val uri = URI("http://$value")
        val host = uri.host?.lowercase()?.removeSurrounding("[", "]") ?: return null
        host to if (uri.port < 0) 80 else uri.port
    }.getOrNull()
}
