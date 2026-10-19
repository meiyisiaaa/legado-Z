package io.legado.app.ui.ai

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiReaderOptions
import io.legado.app.constant.PreferKey
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue

/**
 * End-to-end regression harness for AiChatViewModel. The local NanoHTTPD server
 * emits synthetic OpenAI-compatible responses; it does not validate any real
 * provider, model, account, or production endpoint.
 */
@RunWith(AndroidJUnit4::class)
class AiChatSessionInstrumentedTest {
    private lateinit var application: Application
    private lateinit var preferences: SharedPreferences
    private lateinit var originalAiPreferences: Map<String, Any?>
    private lateinit var server: SyntheticOpenAiServer
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: AiChatViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        preferences = application.defaultSharedPreferences
        originalAiPreferences = AI_PREFERENCE_KEYS.associateWith { key ->
            preferences.all[key].let { value -> if (value is Set<*>) value.toSet() else value }
        }

        server = SyntheticOpenAiServer().apply {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        }
        saveOptions()
        AiConfig.saveReader(AiReaderOptions(readerAssistantEnabled = true))

        store = ViewModelStore()
        onMainSync {
            viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            ).get(AiChatViewModel::class.java)
        }
    }

    @After
    fun tearDown() {
        server.releaseAllGates()
        runCatching { onMainSync { store.clear() } }
        runCatching { server.stop() }
        restoreAiPreferences()
    }

    @Test
    fun retryKeepsQuestionIdAndFailedTurnsAreNotAddedToHistory() = runBlocking {
        server.enqueueFailure()
        assertTrue(ask(RETRY_QUESTION))
        val failed = awaitQuestion(RETRY_QUESTION, AiChatStatus.FAILED)

        server.enqueueSuccess(RETRY_ANSWER)
        onMainSync { viewModel.retry() }
        awaitQuestion(RETRY_QUESTION, AiChatStatus.COMPLETE)
        val afterRetry = items()
        assertEquals(1, afterRetry.count { it.role == AiChatRole.USER && it.text == RETRY_QUESTION })
        assertEquals(failed.id, afterRetry.single { it.role == AiChatRole.USER }.id)
        assertEquals(RETRY_ANSWER, afterRetry.last().text)

        onMainSync { viewModel.clear() }
        server.enqueueFailure()
        assertTrue(ask(FAILED_QUESTION))
        awaitQuestion(FAILED_QUESTION, AiChatStatus.FAILED)

        server.enqueueSuccess("answer-after-failure")
        assertTrue(ask(AFTER_FAILURE_QUESTION))
        awaitQuestion(AFTER_FAILURE_QUESTION, AiChatStatus.COMPLETE)
        assertFalse(server.requests().last().body.contains(FAILED_QUESTION))
    }

    @Test
    fun successfulHistoryIsBoundedByFourPairsAndEightThousandCharacters() = runBlocking {
        for (index in 1..6) {
            val question = "pair-question-$index"
            server.enqueueSuccess("pair-answer-$index")
            assertTrue(ask(question))
            awaitQuestion(question, AiChatStatus.COMPLETE)
        }

        val fourPairRequest = server.requests()[5].body
        assertFalse(fourPairRequest.contains("pair-question-1"))
        assertFalse(fourPairRequest.contains("pair-answer-1"))
        for (index in 2..6) assertTrue(fourPairRequest.contains("pair-question-$index"))

        onMainSync { viewModel.clear() }
        for (index in 1..3) {
            val question = "char-question-$index"
            server.enqueueSuccess("char-answer-$index:" + "x".repeat(3_000))
            assertTrue(ask(question))
            awaitQuestion(question, AiChatStatus.COMPLETE)
        }

        server.enqueueSuccess("char-answer-4")
        assertTrue(ask("char-question-4"))
        awaitQuestion("char-question-4", AiChatStatus.COMPLETE)
        val charBoundRequest = server.requests().last().body
        assertFalse(charBoundRequest.contains("char-question-1"))
        assertFalse(charBoundRequest.contains("char-answer-1:"))
        assertTrue(charBoundRequest.contains("char-question-2"))
        assertTrue(charBoundRequest.contains("char-question-3"))
    }

    @Test
    fun largeResponseBodyIsReadAndDeliveredIntact() = runBlocking {
        val largeAnswer = "large-answer-" + "x".repeat(20_000)
        server.enqueueSuccess(largeAnswer)

        assertTrue(ask("large-response-question"))
        val question = awaitQuestion("large-response-question", AiChatStatus.COMPLETE)
        val answer = items().single { it.role == AiChatRole.ASSISTANT && it.replyTo == question.id }

        assertEquals(largeAnswer, answer.text)
    }

    @Test
    fun stopClearSelectionAndConfigChangesRejectLateResponses() = runBlocking {
        val stopGate = startDelayedQuestion(STOPPED_QUESTION)
        onMainSync { viewModel.stop() }
        assertEquals(AiChatStatus.CANCELLED, userItem(STOPPED_QUESTION).status)
        stopGate.release.countDown()
        awaitResponsePath(stopGate)
        delay(IDLE_SETTLE_MS)
        assertEquals(AiChatStatus.CANCELLED, userItem(STOPPED_QUESTION).status)

        server.enqueueSuccess("answer-after-stop")
        assertTrue(ask(AFTER_STOP_QUESTION))
        awaitQuestion(AFTER_STOP_QUESTION, AiChatStatus.COMPLETE)
        assertFalse(server.requests().last().body.contains(STOPPED_QUESTION))

        val clearGate = startDelayedQuestion(CLEARED_QUESTION)
        onMainSync { viewModel.clear() }
        assertTrue(items().isEmpty())
        clearGate.release.countDown()
        awaitResponsePath(clearGate)
        delay(IDLE_SETTLE_MS)
        assertTrue(items().isEmpty())

        val selectionGate = startDelayedQuestion(OLD_SELECTION_QUESTION)
        onMainSync { viewModel.bindSelection(NEW_ANCHOR, NEW_SELECTION) }
        assertTrue(items().isEmpty())
        selectionGate.release.countDown()
        awaitResponsePath(selectionGate)
        delay(IDLE_SETTLE_MS)
        assertTrue(items().isEmpty())

        assertPreferenceChangeRejectsLateResult("model") {
            preferences.edit().putString(PreferKey.aiModel, "instrumentation-model-updated").commit()
        }
        assertPreferenceChangeRejectsLateResult("endpoint") {
            preferences.edit().putString(PreferKey.aiEndpoint, "http://127.0.0.1:${server.listeningPort}/v2").commit()
        }
        assertPreferenceChangeRejectsLateResult("api key") {
            val changed = AiConfig.snapshot().copy(apiKey = TEST_API_KEY_ROTATED)
            assertFalse(AiConfig.save(changed).hasError())
        }
    }

    @Test
    fun questionOverTwoThousandCharactersDoesNotSendARequest() = runBlocking {
        val before = server.requests().size
        assertFalse(ask("q".repeat(2_001)))
        assertEquals(before, server.requests().size)
        assertTrue(items().isEmpty())
        assertFalse(onMainSync { viewModel.error.value.isNullOrBlank() })
    }

    private suspend fun assertPreferenceChangeRejectsLateResult(
        label: String,
        change: () -> Unit
    ) {
        onMainSync {
            viewModel.clear()
            viewModel.bindSelection(ANCHOR, SELECTION)
        }
        val previousQuestion = "prior-$label"
        server.enqueueSuccess("prior-answer-$label")
        assertTrue(ask(previousQuestion))
        awaitQuestion(previousQuestion, AiChatStatus.COMPLETE)

        val pendingQuestion = "pending-$label"
        val gate = startDelayedQuestion(pendingQuestion)
        onMainSync { change() }
        waitUntil { items().isEmpty() && !isLoading() }
        gate.release.countDown()
        awaitResponsePath(gate)
        delay(IDLE_SETTLE_MS)
        assertTrue("$label change must clear prior and pending turns", items().isEmpty())

        // Restore a reachable endpoint after the endpoint-specific invalidation.
        if (label == "endpoint") {
            onMainSync {
                preferences.edit()
                    .putString(PreferKey.aiEndpoint, "http://127.0.0.1:${server.listeningPort}/v1")
                    .commit()
            }
            waitUntil { items().isEmpty() && !isLoading() }
        }

        val followUp = "follow-up-$label"
        server.enqueueSuccess("follow-up-answer-$label")
        assertTrue(ask(followUp))
        awaitQuestion(followUp, AiChatStatus.COMPLETE)
        val followUpBody = server.requests().last().body
        assertFalse(followUpBody.contains(previousQuestion))
        assertFalse(followUpBody.contains(pendingQuestion))
    }

    private suspend fun startDelayedQuestion(question: String): ResponseGate {
        val gate = server.enqueueDelayed("late-$question")
        assertTrue(ask(question))
        assertTrue("server did not receive $question", gate.awaitStarted())
        return gate
    }

    private suspend fun awaitQuestion(question: String, status: AiChatStatus): AiChatItem {
        try {
            waitUntil {
                items().lastOrNull { it.role == AiChatRole.USER && it.text == question }?.status == status
            }
        } catch (e: TimeoutCancellationException) {
            // Preserve cancellation from the test runner; only enrich this helper's own timeout.
            currentCoroutineContext().ensureActive()
            val currentItems = viewModel.items.value.orEmpty()
            val currentError = viewModel.error.value.orEmpty()
                .replace(TEST_API_KEY, "<redacted>")
                .replace(TEST_API_KEY_ROTATED, "<redacted>")
                .take(160)
            throw AssertionError(
                "Timed out waiting for '$question'=$status; " +
                    "items=${currentItems.joinToString { "${it.id}:${it.role}:${it.status}" }}; " +
                    "loading=${viewModel.loading.value}; error='$currentError'; " +
                    "serverRequests=${server.requests().size}",
                e
            )
        }
        return userItem(question)
    }

    private suspend fun awaitResponsePath(gate: ResponseGate) {
        assertTrue("server did not finish the delayed response", gate.awaitResponseReady())
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(WAIT_TIMEOUT_MS) {
            while (!predicate()) delay(POLL_INTERVAL_MS)
        }
    }

    private fun ask(question: String): Boolean = onMainSync {
        viewModel.ask(SELECTION, question)
    }

    private fun items(): List<AiChatItem> = onMainSync { viewModel.items.value.orEmpty() }

    private fun isLoading(): Boolean = onMainSync { viewModel.loading.value == true }

    private fun userItem(question: String): AiChatItem =
        items().single { it.role == AiChatRole.USER && it.text == question }

    private fun saveOptions(
        endpoint: String = "http://127.0.0.1:${server.listeningPort}/v1",
        apiKey: String = TEST_API_KEY,
        model: String = TEST_MODEL
    ) {
        val result = AiConfig.save(
            AiChatOptions(
                endpoint = endpoint,
                apiKey = apiKey,
                model = model,
                timeoutSec = 10
            )
        )
        assertFalse("test AI config must pass validation: ${result.errors()}", result.hasError())
    }

    private fun restoreAiPreferences() {
        val editor = preferences.edit()
        originalAiPreferences.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> editor.remove(key)
            }
        }
        check(editor.commit()) { "Failed to restore AI preferences after test" }
    }

    private fun <T : Any> onMainSync(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        return checkNotNull(result)
    }

    private class SyntheticOpenAiServer : NanoHTTPD("127.0.0.1", 0) {
        private val plans = LinkedBlockingQueue<ResponsePlan>()
        private val captured = CopyOnWriteArrayList<CapturedRequest>()
        private val gates = CopyOnWriteArrayList<ResponseGate>()

        fun enqueueSuccess(answer: String) {
            plans.add(ResponsePlan(NanoHTTPD.Response.Status.OK, answer = answer))
        }

        fun enqueueFailure() {
            plans.add(ResponsePlan(NanoHTTPD.Response.Status.INTERNAL_ERROR, errorMessage = "synthetic failure"))
        }

        fun enqueueDelayed(answer: String): ResponseGate {
            val gate = ResponseGate()
            gates.add(gate)
            plans.add(ResponsePlan(NanoHTTPD.Response.Status.OK, answer = answer, gate = gate))
            return gate
        }

        fun requests(): List<CapturedRequest> = captured.toList()

        fun releaseAllGates() {
            gates.forEach { it.release.countDown() }
        }

        override fun serve(session: IHTTPSession): Response {
            val upload = HashMap<String, String>()
            runCatching { session.parseBody(upload) }
            val request = CapturedRequest(session.uri, upload["postData"].orEmpty())
            captured.add(request)
            val plan = plans.poll() ?: ResponsePlan(
                NanoHTTPD.Response.Status.OK,
                answer = "synthetic-answer-${captured.size}"
            )
            plan.gate?.let { gate ->
                gate.started.countDown()
                runCatching { gate.release.await(GATE_WAIT_MS, TimeUnit.MILLISECONDS) }
            }
            val body = if (plan.errorMessage != null) {
                """{"error":{"message":${JSONObject.quote(plan.errorMessage)}}}"""
            } else {
                """{"id":"synthetic","model":"synthetic-model","choices":[{"index":0,"message":{"role":"assistant","content":${JSONObject.quote(plan.answer.orEmpty())}},"finish_reason":"stop"}]}"""
            }
            plan.gate?.responseReady?.countDown()
            return NanoHTTPD.newFixedLengthResponse(
                plan.status,
                "application/json; charset=utf-8",
                body
            )
        }
    }

    private data class CapturedRequest(val path: String, val body: String)

    private data class ResponsePlan(
        val status: NanoHTTPD.Response.Status,
        val answer: String? = null,
        val errorMessage: String? = null,
        val gate: ResponseGate? = null
    )

    private class ResponseGate {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val responseReady = CountDownLatch(1)

        suspend fun awaitStarted(): Boolean = withContext(Dispatchers.IO) {
            started.await(GATE_WAIT_MS, TimeUnit.MILLISECONDS)
        }

        suspend fun awaitResponseReady(): Boolean = withContext(Dispatchers.IO) {
            responseReady.await(GATE_WAIT_MS, TimeUnit.MILLISECONDS)
        }
    }

    companion object {
        private const val TEST_API_KEY = "instrumentation-test-key-do-not-use"
        private const val TEST_API_KEY_ROTATED = "instrumentation-test-key-rotated-do-not-use"
        private const val TEST_MODEL = "instrumentation-model"
        private const val ANCHOR = "synthetic-selection-anchor"
        private const val SELECTION = "synthetic selected passage for reader chat tests"
        private const val NEW_ANCHOR = "synthetic-selection-anchor-next"
        private const val NEW_SELECTION = "different synthetic selected passage"
        private const val RETRY_QUESTION = "retry-question-marker"
        private const val RETRY_ANSWER = "retry-answer-marker"
        private const val FAILED_QUESTION = "failed-question-marker"
        private const val AFTER_FAILURE_QUESTION = "after-failure-question-marker"
        private const val STOPPED_QUESTION = "stopped-question-marker"
        private const val AFTER_STOP_QUESTION = "after-stop-question-marker"
        private const val CLEARED_QUESTION = "cleared-question-marker"
        private const val OLD_SELECTION_QUESTION = "old-selection-question-marker"
        private const val WAIT_TIMEOUT_MS = 15_000L
        private const val GATE_WAIT_MS = 12_000L
        private const val IDLE_SETTLE_MS = 150L
        private const val POLL_INTERVAL_MS = 20L
        private val AI_PREFERENCE_KEYS = listOf(
            PreferKey.aiEndpoint,
            PreferKey.aiApiKey,
            PreferKey.aiModel,
            PreferKey.aiTemperature,
            PreferKey.aiMaxTokens,
            PreferKey.aiTimeoutSec,
            PreferKey.aiHtmlMaxChars,
            PreferKey.aiAutoRepair,
            PreferKey.aiExplainMaxChars,
            PreferKey.aiReaderAssistantEnabled
        )
    }
}
