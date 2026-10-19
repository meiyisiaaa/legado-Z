package io.legado.app.mcp

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.appDb
import io.legado.app.help.source.SourceHelp
import io.legado.app.service.McpStatus
import io.legado.app.service.McpService
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.legado.app.utils.GSON
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.UUID

/** End-to-end acceptance through the foreground service and the official MCP Streamable HTTP client. */
@RunWith(AndroidJUnit4::class)
class McpServiceInstrumentedTest {

    @Test
    fun sourceToolsRoundTripOverRealServiceAndRejectUntrustedHeaders() = runBlocking {
        assumeFalse(
            "Do not interrupt an MCP session that was already active before this test",
            McpService.status.value.running || McpService.status.value.starting
        )

        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val oldPort = McpConfig.port
        val port = ServerSocket(0).use { it.localPort }
        val unique = UUID.randomUUID().toString()
        val sourceUrl = "https://mcp-$unique.invalid/source"
        val draftUrl = "https://mcp-draft-$unique.invalid/source"
        val originalScript = sourceScript(sourceUrl, "MCP Instrumentation v1", "mcp-test-local")
        val replacementScript = sourceScript(
            sourceUrl,
            "MCP Instrumentation v2",
            "must-not-replace-local",
            enabled = true
        )
        val draftScript = sourceScript(draftUrl, "MCP Draft", "draft-only")
        val endpoint = "http://127.0.0.1:$port/mcp"
        val scenario = ActivityScenario.launch<ConfigActivity>(
            Intent(appContext, ConfigActivity::class.java)
                .putExtra("configTag", ConfigTag.OTHER_CONFIG)
        )
        var sdkClient: Client? = null
        var sdkHttpClient: HttpClient? = null
        var rawHttpClient: HttpClient? = null
        var testUrlsWereInitiallyAbsent = false

        try {
            assertNull("Test URL unexpectedly exists already", appDb.bookSourceDao.getBookSource(sourceUrl))
            assertNull("Draft URL unexpectedly exists already", appDb.bookSourceDao.getBookSource(draftUrl))
            testUrlsWereInitiallyAbsent = true
            McpConfig.port = port
            val token = McpConfig.token()
            val status = startServiceAndAwaitReady(scenario)
            assertNull(status.error)
            assertTrue(status.running)

            val mcpHttpClient = HttpClient(CIO) {
                install(SSE)
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                }
            }
            sdkHttpClient = mcpHttpClient
            val headerTestHttpClient = HttpClient(CIO) {
                expectSuccess = false
                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                }
            }
            rawHttpClient = headerTestHttpClient
            val transport = StreamableHttpClientTransport(
                client = mcpHttpClient,
                url = endpoint,
                requestBuilder = {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            )
            val mcpClient = Client(Implementation(name = "legado-android-test", version = "1.0"))
            sdkClient = mcpClient
            mcpClient.connect(transport)

            val expectedTools = setOf(
                "list_sources", "get_source", "save_source", "delete_sources", "check_source", "debug_source"
            )
            assertEquals(expectedTools, mcpClient.listTools().tools.map { it.name }.toSet())

            val saved = callTool(mcpClient, "save_source", mapOf("source" to originalScript))
            assertFalse(toolError(saved))
            assertEquals("inserted", resultJson(saved).get("action").asString)

            val sourceList = callTool(
                mcpClient,
                "list_sources",
                mapOf("query" to sourceUrl, "page" to 1, "limit" to 10)
            )
            assertFalse(toolError(sourceList))
            val listJson = resultJson(sourceList)
            assertEquals(1, listJson.get("total").asInt)
            assertEquals(sourceUrl, listJson.getAsJsonArray("items")[0].asJsonObject.get("url").asString)

            val conflict = callTool(mcpClient, "save_source", mapOf("source" to originalScript))
            assertTrue(toolError(conflict))
            assertEquals("CONFLICT", resultJson(conflict).getAsJsonObject("error").get("code").asString)

            val overwritten = callTool(
                mcpClient,
                "save_source",
                mapOf("source" to replacementScript, "overwrite" to true)
            )
            assertFalse(toolError(overwritten))
            assertEquals("overwritten", resultJson(overwritten).get("action").asString)

            val sourceJs = callTool(mcpClient, "get_source", mapOf("url" to sourceUrl, "format" to "js"))
            assertFalse(toolError(sourceJs))
            assertEquals(replacementScript, toolText(sourceJs))

            val sourceJson = callTool(mcpClient, "get_source", mapOf("url" to sourceUrl, "format" to "json"))
            assertFalse(toolError(sourceJson))
            val exported = GSON.fromJson(toolText(sourceJson), com.google.gson.JsonObject::class.java)
            assertEquals(replacementScript, exported.get("mainJs").asString)
            assertEquals("MCP Instrumentation v2", exported.get("bookSourceName").asString)
            assertEquals("mcp-test-local", exported.get("bookSourceGroup").asString)
            assertFalse(exported.get("enabled").asBoolean)

            val checked = callTool(mcpClient, "check_source", mapOf("url" to sourceUrl))
            assertFalse(toolError(checked))
            val checkJson = resultJson(checked)
            assertTrue(checkJson.get("valid").asBoolean)
            assertTrue(checkJson.get("validationOnly").asBoolean)

            val debuggedDraft = callTool(
                mcpClient,
                "debug_source",
                mapOf("draft" to draftScript, "key" to "offline fixture")
            )
            assertFalse(toolError(debuggedDraft))
            assertEquals("completed", resultJson(debuggedDraft).get("terminalState").asString)
            assertNull("Debugging an inline draft must not persist it", appDb.bookSourceDao.getBookSource(draftUrl))

            val hostRejected = postWithHeaders(
                headerTestHttpClient,
                endpoint,
                host = "untrusted.invalid:$port",
                authorization = "Bearer $token"
            )
            assertEquals(403, hostRejected)

            val sameOriginAccepted = postWithHeaders(
                headerTestHttpClient,
                endpoint,
                host = "127.0.0.1:$port",
                origin = "http://127.0.0.1:$port",
                authorization = "Bearer $token"
            )
            assertEquals("A valid same-origin MCP initialize request should be accepted", 200, sameOriginAccepted)

            val originRejected = postWithHeaders(
                headerTestHttpClient,
                endpoint,
                origin = "http://untrusted.invalid:$port",
                authorization = "Bearer $token"
            )
            assertEquals(403, originRejected)

            val bearerRejected = postWithHeaders(
                headerTestHttpClient,
                endpoint,
                authorization = "Bearer invalid-test-token"
            )
            assertEquals(401, bearerRejected)

            val deleted = callTool(mcpClient, "delete_sources", mapOf("urls" to listOf(sourceUrl)))
            assertFalse(toolError(deleted))
            assertEquals(1, resultJson(deleted).get("deletedCount").asInt)
            assertNull(appDb.bookSourceDao.getBookSource(sourceUrl))

            mcpClient.close()
            sdkClient = null
            mcpHttpClient.close()
            sdkHttpClient = null
            McpService.stop(appContext)
            awaitServiceStopped()
            awaitPortClosed(headerTestHttpClient, endpoint)
        } finally {
            sdkClient?.let { runCatching { it.close() } }
            runCatching { sdkHttpClient?.close() }
            runCatching { rawHttpClient?.close() }

            try {
                McpService.stop(appContext)
                runCatching { awaitServiceStopped() }
                if (testUrlsWereInitiallyAbsent) {
                    withContext(Dispatchers.IO) {
                        mapOf(
                            sourceUrl to setOf(originalScript, replacementScript),
                            draftUrl to setOf(draftScript)
                        ).forEach { (url, testScripts) ->
                            appDb.bookSourceDao.getBookSource(url)
                                ?.takeIf { it.mainJs in testScripts }
                                ?.let { SourceHelp.deleteBookSources(listOf(it)) }
                        }
                    }
                }
            } finally {
                McpConfig.port = oldPort
                scenario.close()
            }
        }
    }

    private suspend fun startServiceAndAwaitReady(scenario: ActivityScenario<ConfigActivity>): McpStatus {
        scenario.onActivity { McpService.start(it) }
        return withTimeout(START_TIMEOUT_MS) {
            McpService.status.first { it.running || it.error != null }
        }
    }

    private suspend fun awaitServiceStopped() {
        withTimeout(STOP_TIMEOUT_MS) {
            McpService.status.first { !it.running && !it.starting }
        }
    }

    private suspend fun awaitPortClosed(client: HttpClient, endpoint: String) {
        withTimeout(STOP_TIMEOUT_MS) {
            while (true) {
                val stillListening = runCatching { client.get(endpoint).status }.isSuccess
                if (!stillListening) return@withTimeout
                delay(PORT_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun postWithHeaders(
        client: HttpClient,
        endpoint: String,
        host: String? = null,
        origin: String? = null,
        authorization: String
    ): Int {
        val response = client.post(endpoint) {
            host?.let { header(HttpHeaders.Host, it) }
            header(HttpHeaders.Authorization, authorization)
            origin?.let { header(HttpHeaders.Origin, it) }
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(INITIALIZE_REQUEST)
        }
        return response.status.value
    }

    private suspend fun callTool(
        client: Client,
        name: String,
        arguments: Map<String, Any?>
    ): CallToolResult = withTimeout(TOOL_TIMEOUT_MS) {
        client.callTool(name = name, arguments = arguments)
    }

    private fun toolError(result: CallToolResult): Boolean = result.isError == true

    private fun toolText(result: CallToolResult): String =
        result.content.filterIsInstance<TextContent>().firstOrNull()?.text
            ?: error("MCP tool returned no text content")

    private fun resultJson(result: CallToolResult) =
        GSON.fromJson(toolText(result), com.google.gson.JsonObject::class.java)

    private fun sourceScript(
        url: String,
        name: String,
        group: String,
        enabled: Boolean = false
    ): String = """
        var config = {
          "bookSourceUrl": "$url",
          "bookSourceName": "$name",
          "bookSourceGroup": "$group",
          "bookSourceType": 0,
          "enabled": $enabled
        };

        function search(key, page) {
          return [{ "name": "Synthetic Book", "author": "Fixture", "bookUrl": "https://mcp-test.invalid/book" }];
        }
        function getBookInfo(book) {
          return { "name": "Synthetic Book", "author": "Fixture", "tocUrl": "https://mcp-test.invalid/toc" };
        }
        function getChapters(book) {
          return [{ "title": "Synthetic Chapter", "url": "https://mcp-test.invalid/chapter/1" }];
        }
        function getContent(chapter, book, nextChapterUrl) {
          return "synthetic content that never leaves this process";
        }
    """.trimIndent()

    private companion object {
        const val START_TIMEOUT_MS = 20_000L
        const val STOP_TIMEOUT_MS = 10_000L
        const val TOOL_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 1_000L
        const val PORT_POLL_INTERVAL_MS = 100L
        const val INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
              "protocolVersion":"2025-11-25","capabilities":{},
              "clientInfo":{"name":"mcp-security-test","version":"1.0"}
            }}
        """
    }
}
