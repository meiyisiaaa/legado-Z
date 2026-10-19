package io.legado.app.mcp

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.legado.app.model.DebugRunner
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class McpServer(private val port: Int, hosts: Set<String>) {
    private val prefix = "mcp:${UUID.randomUUID()}:"
    private val servers = ConcurrentHashMap<String, Server>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var closing = false
    private val security = McpSecurity(hosts, port, McpConfig::token)
    private val authorities = hosts.map { mcpAuthority(it, port) }
    private val engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        intercept(ApplicationCallPipeline.Plugins) {
            val status = when {
                !security.acceptsHost(call.request.header("Host")) ||
                    !security.acceptsOrigin(call.request.header("Origin")) -> HttpStatusCode.Forbidden
                !security.acceptsAuthorization(call.request.header("Authorization")) -> HttpStatusCode.Unauthorized
                else -> null
            }
            if (status != null) {
                call.respondText(status.description, status = status)
                finish()
            }
        }
        mcpStreamableHttp(allowedHosts = authorities, allowedOrigins = authorities.map { "http://$it" }) {
            val owner = prefix + UUID.randomUUID()
            createSourceServer(owner).also { server ->
                servers[owner] = server
                server.onClose {
                    servers.remove(owner)
                    if (!closing) cleanupScope.launch { DebugRunner.cancelOwner(owner) }
                }
            }
        }
    }

    suspend fun start() = withContext(Dispatchers.IO) { engine.start(wait = false); Unit }

    suspend fun stop() = withContext(Dispatchers.IO) {
        closing = true
        // 先阻断监听，再等待本服务持有的调试及协议会话退出。
        engine.stop(0, 1000)
        DebugRunner.cancelOwners(prefix)
        servers.values.toList().forEach { it.close() }
        servers.clear()
        cleanupScope.coroutineContext[Job]?.cancelAndJoin()
    }
}
