package io.legado.app.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRunnerTest {

    @Test
    fun `another owner receives busy and cannot cancel active run`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val active = async {
            DebugRunner.run("test-owner-a", DebugRunKind.DRAFT_BOOK, "https://same.test") {
                started.complete(Unit)
                release.await()
            }
        }
        started.await()

        val result = DebugRunner.run("test-owner-b", DebugRunKind.MCP, "https://same.test") {
            error("a busy owner must not start")
        }

        assertEquals("test-owner-a", (result as DebugRunResult.Busy).activeOwnerId)
        assertTrue(active.isActive)
        release.complete(Unit)
        assertTrue(active.await() is DebugRunResult.Completed)
    }

    @Test
    fun `same owner retry cancels and joins old work before starting`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var oldStopped = false
        val old = async {
            DebugRunner.run("test-owner-retry", DebugRunKind.BOOK_UI) {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    oldStopped = true
                }
            }
        }
        started.await()

        val retried = DebugRunner.run("test-owner-retry", DebugRunKind.BOOK_UI) {
            assertTrue("old worker must be joined before replacement", oldStopped)
        }

        assertTrue(retried is DebugRunResult.Completed)
        assertTrue(oldStopped)
        assertTrue(old.await() is DebugRunResult.Completed)
    }

    @Test
    fun `captured stale callback for same source cannot enter the next run`() = runBlocking {
        val sourceUrl = "https://same.test"
        val oldEvents = mutableListOf<String>()
        lateinit var staleCallback: () -> Unit
        DebugRunner.run(
            ownerId = "test-owner-stale",
            kind = DebugRunKind.DRAFT_BOOK,
            sourceUrl = sourceUrl,
            onEvent = { oldEvents += it.payload }
        ) { context ->
            staleCallback = context.wrapCallback {
                context.emit(sourceUrl, "late old event", "late old event", 1)
            }
        }

        val newEvents = mutableListOf<String>()
        DebugRunner.run(
            ownerId = "test-owner-stale",
            kind = DebugRunKind.DRAFT_BOOK,
            sourceUrl = sourceUrl,
            onEvent = { newEvents += it.payload }
        ) { context ->
            staleCallback()
            context.emit(sourceUrl, "current event", "current event", 1)
        }

        assertTrue(oldEvents.isEmpty())
        assertEquals(listOf("current event"), newEvents)
    }

    @Test
    fun `stale batch callback cannot update same source validation status`() = runBlocking {
        val sourceUrl = "https://same.test"
        lateinit var staleBatchCallback: () -> Unit
        DebugRunner.run(
            ownerId = "test-batch-stale",
            kind = DebugRunKind.CHECK_SOURCE_BATCH,
            capture = false
        ) { context ->
            staleBatchCallback = context.wrapCallback {
                Debug.log(sourceUrl, "late old status")
            }
        }

        Debug.debugMessageMap[sourceUrl] = "new run status"
        staleBatchCallback()
        assertEquals("new run status", Debug.debugMessageMap[sourceUrl])
        Debug.debugMessageMap.remove(sourceUrl)
        Unit
    }

    @Test
    fun `sanitizing credentials inside source JSON preserves valid JSON`() {
        val sanitized = DebugLogSanitizer.sanitize(
            """{"header":"Authorization: Bearer source-secret","apiKey":"api-secret","mainJs":"const apiKey = \"sk-main-secret\";","comment":"the ordinary key name is visible"}"""
        )

        assertTrue(JsonParser.parseString(sanitized).isJsonObject)
        assertTrue(sanitized.contains("[REDACTED]"))
        assertFalse(sanitized.contains("source-secret"))
        assertFalse(sanitized.contains("api-secret"))
        assertFalse(sanitized.contains("sk-main-secret"))
        assertTrue(sanitized.contains("the ordinary key name is visible"))
        assertTrue(JsonParser.parseString(sanitized).asJsonObject.get("mainJs").asString.contains("apiKey = \"[REDACTED]\""))
    }

    @Test
    fun `sanitizer redacts bare assignments and sensitive html fields only`() {
        val sanitizedText = DebugLogSanitizer.sanitize(
            "api_key=html-secret; ordinary key=chapter-key"
        )
        assertFalse(sanitizedText.contains("html-secret"))
        assertTrue(sanitizedText.contains("ordinary key=chapter-key"))

        val sanitizedHtml = DebugLogSanitizer.sanitize(
            """<input name="password" value="pw-secret"><input name="csrf_token" value="csrf-secret"><input name="search" value="reader text">"""
        )
        assertTrue(sanitizedHtml.contains("name=\"password\" value=\"[REDACTED]\""))
        assertTrue(sanitizedHtml.contains("name=\"csrf_token\" value=\"[REDACTED]\""))
        assertTrue(sanitizedHtml.contains("name=\"search\" value=\"reader text\""))
        assertFalse(sanitizedHtml.contains("pw-secret"))
        assertFalse(sanitizedHtml.contains("csrf-secret"))
    }

    @Test
    fun `snapshot serialization is bounded valid Gson JSON`() {
        val snapshot = DebugRunSnapshot(
            runId = "run",
            ownerId = "owner",
            kind = DebugRunKind.MCP,
            sourceUrl = "https://example.test",
            startedAtMillis = 1,
            finishedAtMillis = 2,
            terminalState = "completed",
            error = null,
            truncated = false,
            events = listOf(
                DebugRunEvent(1, 1, null, 1, "x".repeat(10_000), "x".repeat(10_000))
            )
        )

        val json = snapshot.toJson(maxChars = 512)

        assertTrue(json.length <= 512)
        assertTrue(JsonParser.parseString(json).isJsonObject)
        assertTrue(JsonParser.parseString(json).asJsonObject.get("truncated").asBoolean)
    }

    @Test
    fun `lease remains owned until structured child cancellation cleanup finishes`() = runBlocking {
        val childStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanupToFinish = CompletableDeferred<Unit>()
        val childStopped = CompletableDeferred<Unit>()
        var childFinishedAtMillis = 0L
        val first = async {
            DebugRunner.run("test-owner-children", DebugRunKind.BOOK_UI) {
                launch {
                    try {
                        childStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            assertEquals("test-owner-children", DebugRunner.activeOwnerId.value)
                            cleanupStarted.complete(Unit)
                            allowCleanupToFinish.await()
                            childFinishedAtMillis = System.currentTimeMillis()
                            childStopped.complete(Unit)
                        }
                    }
                }
            }
        }
        childStarted.await()

        val cancel = async { DebugRunner.cancelOwner("test-owner-children") }
        cleanupStarted.await()
        val competing = DebugRunner.run("test-owner-during-cleanup", DebugRunKind.MCP) {
            error("competing owner must not start")
        }
        assertTrue(competing is DebugRunResult.Busy)
        assertEquals("test-owner-children", DebugRunner.activeOwnerId.value)

        allowCleanupToFinish.complete(Unit)
        cancel.await()

        assertTrue(childStopped.isCompleted)
        val result = first.await() as DebugRunResult.Completed
        assertTrue(result.snapshot.finishedAtMillis >= childFinishedAtMillis)
        assertEquals(null, DebugRunner.activeOwnerId.value)
    }
}
