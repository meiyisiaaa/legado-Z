package io.legado.app.ai.engine

import io.legado.app.model.DebugRunEvent
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugEvidenceBuilderTest {

    @Test
    fun `only selected event categories are included and credentials are redacted`() {
        val snapshot = snapshot(
            DebugRunEvent(1, 1, "https://example.test", 1, "Authorization: Bearer token-123", "log"),
            DebugRunEvent(2, 2, "https://example.test", 10, "<div>api_key=html-secret</div>", "html")
        )

        val logsOnly = DebugEvidenceBuilder.build(snapshot, includeLogs = true, includeHtml = false)
        assertTrue(logsOnly.logs.contains("Authorization: [REDACTED]"))
        assertFalse(logsOnly.sampleHtml.contains("html-secret"))

        val htmlOnly = DebugEvidenceBuilder.build(snapshot, includeLogs = false, includeHtml = true)
        assertFalse(htmlOnly.logs.contains("token-123"))
        assertTrue(htmlOnly.sampleHtml.contains("[REDACTED]"))
        assertFalse(htmlOnly.sampleHtml.contains("html-secret"))
    }

    @Test
    fun `evidence respects character budgets`() {
        val snapshot = snapshot(
            DebugRunEvent(1, 1, null, 1, "x".repeat(DebugEvidenceBuilder.MAX_LOG_CHARS * 2), "log")
        )

        val evidence = DebugEvidenceBuilder.build(snapshot, includeLogs = true, includeHtml = false)

        assertTrue(evidence.truncated)
        assertTrue(evidence.logs.length <= DebugEvidenceBuilder.MAX_LOG_CHARS)
    }

    private fun snapshot(vararg events: DebugRunEvent) = DebugRunSnapshot(
        runId = "run",
        ownerId = "owner",
        kind = DebugRunKind.DRAFT_BOOK,
        sourceUrl = "https://example.test",
        startedAtMillis = 1,
        finishedAtMillis = 2,
        terminalState = "completed",
        error = null,
        truncated = false,
        events = events.toList()
    )
}
