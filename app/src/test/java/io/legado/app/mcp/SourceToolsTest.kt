package io.legado.app.mcp

import io.legado.app.model.DebugRunEvent
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceToolsTest {

    @Test
    fun serverRegistersExactlyTheSixSourceTools() = runBlocking {
        val server = createSourceServer("mcp:test-session")

        try {
            assertEquals(
                setOf("list_sources", "get_source", "save_source", "delete_sources", "check_source", "debug_source"),
                server.tools.keys
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun mcpOutputOmitsRawResponsesAndParsedBookText() {
        val snapshot = snapshot(
            event(1, 10, "<html>search response body</html>"),
            event(2, 20, "detail page response body"),
            event(3, 30, "table of contents response body"),
            event(4, 40, "chapter response body"),
            event(5, 1, "└\nchapter text that must not leave the device"),
            event(6, 1, "︾开始解析搜索页"),
            event(7, -1, "stack trace containing a response body")
        )

        val output = snapshot.forMcpOutput()

        assertEquals(listOf(6L), output.events.map { it.sequence })
        assertFalse(output.toJson().contains("response body"))
        assertFalse(output.toJson().contains("chapter text"))
        assertTrue(output.toJson().contains("开始解析搜索页"))
    }

    @Test
    fun mcpOutputSanitizesCredentialsAndHonorsSerializationBound() {
        val snapshot = snapshot(
            event(1, 1, "Authorization: Bearer test-secret")
        )

        val output = snapshot.forMcpOutput().toJson(maxChars = 512)

        assertFalse(output.contains("test-secret"))
        assertTrue(output.contains("[REDACTED]"))
        assertTrue(output.length <= 512)
    }

    @Test
    fun failedDebugDoesNotEchoExceptionBody() {
        val output = snapshot().copy(
            terminalState = "failed",
            error = "exception included a chapter response body"
        ).forMcpOutput().toJson()

        assertFalse(output.contains("chapter response body"))
        assertTrue(output.contains("details are omitted"))
    }

    private fun snapshot(vararg events: DebugRunEvent) = DebugRunSnapshot(
        runId = "test-run",
        ownerId = "mcp:test",
        kind = DebugRunKind.MCP,
        sourceUrl = "https://example.test/source",
        startedAtMillis = 1L,
        finishedAtMillis = 2L,
        terminalState = "completed",
        error = null,
        truncated = false,
        events = events.toList()
    )

    private fun event(sequence: Long, state: Int, payload: String) = DebugRunEvent(
        sequence = sequence,
        timestampMillis = sequence,
        sourceUrl = "https://example.test/source",
        state = state,
        payload = payload,
        displayMessage = payload
    )
}
