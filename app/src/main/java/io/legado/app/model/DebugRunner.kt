package io.legado.app.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ThreadContextElement

private val debugRunThreadLocal = ThreadLocal<DebugRunContext?>()

enum class DebugRunKind {
    BOOK_UI,
    RSS_UI,
    BOOK_WEBSOCKET,
    RSS_WEBSOCKET,
    DRAFT_BOOK,
    CHECK_SOURCE_BATCH,
    MCP
}

data class DebugRunEvent(
    val sequence: Long,
    val timestampMillis: Long,
    val sourceUrl: String?,
    val state: Int,
    /** Raw payload used by the explicit evidence picker. */
    val payload: String,
    /** Existing timestamped/formatted text shown in debug UIs. */
    val displayMessage: String
)

data class DebugRunSnapshot(
    val runId: String,
    val ownerId: String,
    val kind: DebugRunKind,
    val sourceUrl: String?,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val terminalState: String,
    val error: String?,
    val truncated: Boolean,
    val events: List<DebugRunEvent>
) {
    /** A bounded, sanitized representation suitable for an explicitly requested MCP result. */
    fun toJson(maxChars: Int = DEFAULT_SERIALIZED_CHARS): String {
        val limit = maxChars.coerceAtLeast(256)
        val boundedEvents = events.map { event ->
            event.copy(
                sourceUrl = event.sourceUrl?.let(DebugLogSanitizer::sanitize),
                payload = DebugLogSanitizer.sanitize(event.payload),
                displayMessage = DebugLogSanitizer.sanitize(event.displayMessage)
            )
        }.toMutableList()
        var didTruncate = truncated
        while (true) {
            val json = JsonObject().apply {
                addProperty("runId", runId)
                addProperty("ownerId", ownerId)
                addProperty("kind", kind.name)
                addProperty("sourceUrl", sourceUrl?.let(DebugLogSanitizer::sanitize))
                addProperty("startedAtMillis", startedAtMillis)
                addProperty("finishedAtMillis", finishedAtMillis)
                addProperty("terminalState", terminalState)
                addProperty("error", error?.let(DebugLogSanitizer::sanitize))
                addProperty("truncated", didTruncate)
            }
            val array = JsonArray()
            boundedEvents.forEach { event ->
                array.add(JsonObject().apply {
                    addProperty("sequence", event.sequence)
                    addProperty("timestampMillis", event.timestampMillis)
                    addProperty("sourceUrl", event.sourceUrl)
                    addProperty("state", event.state)
                    addProperty("payload", event.payload)
                    addProperty("displayMessage", event.displayMessage)
                })
            }
            json.add("events", array)
            val result = json.toString()
            if (result.length <= limit) return result
            didTruncate = true
            if (boundedEvents.isNotEmpty()) {
                val lastIndex = boundedEvents.lastIndex
                val last = boundedEvents[lastIndex]
                val longest = maxOf(last.payload.length, last.displayMessage.length)
                if (longest > 128) {
                    val newLength = (longest / 2).coerceAtLeast(64)
                    boundedEvents[lastIndex] = last.copy(
                        payload = last.payload.take(newLength),
                        displayMessage = last.displayMessage.take(newLength)
                    )
                } else {
                    boundedEvents.removeAt(lastIndex)
                }
            } else {
                // The fixed metadata itself is larger than the requested bound.
                return JsonObject().apply {
                    addProperty("runId", runId.take(64))
                    addProperty("kind", kind.name)
                    addProperty("terminalState", terminalState.take(32))
                    addProperty("truncated", true)
                }
                    .toString()
            }
        }
    }

    /** Plain text for a bounded prompt or log view. Callers still choose which states to include. */
    fun sanitizedText(maxChars: Int = DEFAULT_SERIALIZED_CHARS): String {
        val limit = maxChars.coerceAtLeast(0)
        if (limit == 0) return ""
        val output = StringBuilder()
        events.forEach { event ->
            if (output.length >= limit) return@forEach
            val line = "[${event.state}] ${DebugLogSanitizer.sanitize(event.payload)}\n"
            val remaining = limit - output.length
            output.append(line.take(remaining))
        }
        return output.toString()
    }

    companion object {
        const val DEFAULT_SERIALIZED_CHARS = 24_000
    }
}

sealed interface DebugRunResult {
    data class Completed(val snapshot: DebugRunSnapshot) : DebugRunResult
    data class Busy(val activeOwnerId: String) : DebugRunResult
}

/**
 * A run token carried in coroutine context and restored for captured callbacks. Source URL alone
 * is deliberately insufficient: a late callback from a previous run for the same URL is rejected
 * by the runner's active run ID check.
 */
class DebugRunContext internal constructor(
    val runId: String,
    val ownerId: String,
    val kind: DebugRunKind,
    val sourceUrl: String?,
    val capture: Boolean,
    internal val startedAtMillis: Long,
    internal val onEvent: ((DebugRunEvent) -> Unit)?,
    private val runner: DebugRunner
) : AbstractCoroutineContextElement(Key), ThreadContextElement<DebugRunContext?> {

    companion object Key : CoroutineContext.Key<DebugRunContext> {
        fun currentOrNull(): DebugRunContext? = debugRunThreadLocal.get()

        fun <T> with(context: DebugRunContext, block: () -> T): T {
            val previous = debugRunThreadLocal.get()
            debugRunThreadLocal.set(context)
            return try {
                block()
            } finally {
                if (previous == null) debugRunThreadLocal.remove()
                else debugRunThreadLocal.set(previous)
            }
        }
    }

    private val accepting = AtomicBoolean(true)
    private val sequence = AtomicLong(0)
    private val events = ArrayList<DebugRunEvent>()
    private var storedChars = 0
    private var didTruncate = false
    private var terminalState = "running"
    private var error: String? = null

    override fun updateThreadContext(context: CoroutineContext): DebugRunContext? {
        val previous = currentOrNull()
        debugRunThreadLocal.set(this)
        return previous
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: DebugRunContext?) {
        if (oldState == null) debugRunThreadLocal.remove()
        else debugRunThreadLocal.set(oldState)
    }

    /** Capture this token when registering any callback that may log after its producer returns. */
    fun wrapCallback(block: () -> Unit): () -> Unit = { Key.with(this, block) }

    internal fun isAccepting(): Boolean = accepting.get()

    internal fun whileCurrent(block: () -> Unit) {
        synchronized(events) {
            if (accepting.get() && runner.isCurrent(this)) block()
        }
    }

    internal fun stopAccepting() {
        synchronized(events) {
            accepting.set(false)
        }
    }

    internal fun emit(
        eventSourceUrl: String?,
        payload: String,
        displayMessage: String,
        state: Int
    ) {
        if (!capture) return
        if (sourceUrl != null && sourceUrl != eventSourceUrl) return
        val boundedPayload = payload.take(Limits.MAX_EVENT_CHARS)
        val boundedDisplay = displayMessage.take(Limits.MAX_EVENT_CHARS)
        synchronized(events) {
            // Validate and append under the same lock used by stopAccepting/snapshot. A late
            // wrapped callback can never append after the completion snapshot was taken.
            if (!accepting.get() || !runner.isCurrent(this)) return
            val length = boundedPayload.length + boundedDisplay.length
            if (events.size >= Limits.MAX_EVENTS || storedChars + length > Limits.MAX_TOTAL_CHARS) {
                didTruncate = true
                return
            }
            val event = DebugRunEvent(
                sequence = sequence.incrementAndGet(),
                timestampMillis = System.currentTimeMillis(),
                sourceUrl = eventSourceUrl,
                state = state,
                payload = boundedPayload,
                displayMessage = boundedDisplay
            )
            events.add(event)
            storedChars += length
            // Serialize event delivery with shutdown so no old-run callback is delivered after
            // the lease is released.
            runCatching { onEvent?.invoke(event) }
        }
    }

    internal fun markError(throwable: Throwable) {
        synchronized(events) {
            terminalState = "failed"
            error = DebugLogSanitizer.sanitize(
                throwable.localizedMessage ?: throwable.javaClass.simpleName
            ).take(2_000)
        }
    }

    internal fun markCancelled() {
        synchronized(events) {
            terminalState = "cancelled"
        }
    }

    internal fun markCompleted() {
        synchronized(events) {
            if (terminalState == "running") terminalState = "completed"
        }
    }

    internal fun snapshot(finishedAtMillis: Long): DebugRunSnapshot = synchronized(events) {
        DebugRunSnapshot(
            runId = runId,
            ownerId = ownerId,
            kind = kind,
            sourceUrl = sourceUrl,
            startedAtMillis = startedAtMillis,
            finishedAtMillis = finishedAtMillis,
            terminalState = terminalState,
            error = error,
            truncated = didTruncate,
            events = events.toList()
        )
    }

    private object Limits {
        const val MAX_EVENTS = 256
        const val MAX_EVENT_CHARS = 64_000
        const val MAX_TOTAL_CHARS = 512_000
    }
}

object DebugRunner {
    private val gate = Mutex()
    @Volatile
    private var activeSession: Session? = null

    private val mutableActiveOwnerId = MutableStateFlow<String?>(null)
    val activeOwnerId: StateFlow<String?> = mutableActiveOwnerId

    val activeKind: DebugRunKind?
        get() = activeSession?.context?.kind

    val hasActiveSession: Boolean
        get() = activeSession != null

    /**
     * Runs one explicit debug or the complete source-check batch under a single app-wide lease.
     * A same-owner retry cancels and joins its previous run before starting; another owner gets
     * BUSY and never cancels work it does not own.
     */
    suspend fun run(
        ownerId: String,
        kind: DebugRunKind,
        sourceUrl: String? = null,
        capture: Boolean = true,
        onEvent: ((DebugRunEvent) -> Unit)? = null,
        block: suspend CoroutineScope.(DebugRunContext) -> Unit
    ): DebugRunResult {
        require(ownerId.isNotBlank()) { "ownerId must not be blank" }
        val callerContext = currentCoroutineContext()
        val completed = CompletableDeferred<DebugRunSnapshot>()
        lateinit var session: Session

        // Do not queue unrelated owners behind cancellation/join of an active run. In
        // particular, a requester must observe BUSY while the previous run's children unwind.
        activeSession?.takeIf { it.ownerId != ownerId }?.let {
            return DebugRunResult.Busy(it.ownerId)
        }

        val result = gate.withLock {
            val previous = activeSession
            if (previous != null && previous.ownerId != ownerId) {
                return@withLock DebugRunResult.Busy(previous.ownerId)
            }
            if (previous != null) {
                previous.context.stopAccepting()
                previous.job?.cancelAndJoin()
            }

            val runContext = DebugRunContext(
                runId = UUID.randomUUID().toString(),
                ownerId = ownerId,
                kind = kind,
                sourceUrl = sourceUrl,
                capture = capture,
                startedAtMillis = System.currentTimeMillis(),
                onEvent = onEvent,
                runner = this
            )
            session = Session(ownerId, runContext, completed)
            activeSession = session
            mutableActiveOwnerId.value = ownerId

            val worker = CoroutineScope(callerContext + runContext).launch(start = CoroutineStart.LAZY) {
                try {
                    block(this, runContext)
                    runContext.markCompleted()
                } catch (cancelled: CancellationException) {
                    runContext.markCancelled()
                    throw cancelled
                } catch (failure: Throwable) {
                    runContext.markError(failure)
                    // Since the failure is converted to a result snapshot, cancel children
                    // explicitly; otherwise the worker body could finish while they keep running.
                    coroutineContext.cancelChildren()
                }
            }
            worker.invokeOnCompletion { cause ->
                // Completion handlers run only after structured children have completed. Keep the
                // lease and token valid until then so child cleanup cannot leak into a new run.
                runContext.stopAccepting()
                when (cause) {
                    is CancellationException -> runContext.markCancelled()
                    null -> runContext.markCompleted()
                    else -> runContext.markError(cause)
                }
                val snapshot = runContext.snapshot(System.currentTimeMillis())
                synchronized(this@DebugRunner) {
                    if (activeSession === session) {
                        activeSession = null
                        mutableActiveOwnerId.value = null
                    }
                }
                completed.complete(snapshot)
            }
            session.job = worker
            worker.start()
            null
        }
        if (result is DebugRunResult.Busy) return result

        val worker = session.job ?: return DebugRunResult.Completed(completed.await())
        try {
            worker.join()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                worker.cancelAndJoin()
            }
            throw cancelled
        }
        return DebugRunResult.Completed(completed.await())
    }

    suspend fun cancelOwner(ownerId: String) {
        gate.withLock {
            val session = activeSession?.takeIf { it.ownerId == ownerId } ?: return
            session.context.stopAccepting()
            session.job?.cancelAndJoin()
        }
    }

    suspend fun cancelOwners(prefix: String) {
        gate.withLock {
            val session = activeSession?.takeIf { it.ownerId.startsWith(prefix) } ?: return
            session.context.stopAccepting()
            session.job?.cancelAndJoin()
        }
    }

    internal fun isCurrent(context: DebugRunContext): Boolean =
        activeSession?.let { it.context === context && it.context.isAccepting() } == true

    private data class Session(
        val ownerId: String,
        val context: DebugRunContext,
        val completed: CompletableDeferred<DebugRunSnapshot>,
        var job: Job? = null
    )
}

object DebugLogSanitizer {
    private val sensitiveJsonField = Regex(
        """(?i)^(?:password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|proxy-authorization|cookie|set-cookie|session[_-]?id|csrf(?:[_-]?token)?|xsrf(?:[_-]?token)?|x-csrf-token|authenticity[_-]?token)$"""
    )
    private val secretJson = Regex(
        """(?i)("(?:password|passwd|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|proxy-authorization|cookie|set-cookie|session[_-]?id|csrf(?:[_-]?token)?|xsrf(?:[_-]?token)?|x-csrf-token|authenticity[_-]?token)"\s*:\s*")[^"]*(")"""
    )
    private val authHeader = Regex(
        """(?im)(\b(?:authorization|proxy-authorization)\s*[:=]\s*)(?:bearer\s+)?[^\r\n,;"']+"""
    )
    private val cookieHeader = Regex("""(?im)(\b(?:set-cookie|cookie)\s*[:=]\s*)[^\r\n"']+""")
    private val bearer = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+")
    private val sensitiveAssignment = Regex(
        """(?i)(\b(?:token|access[_-]?token|refresh[_-]?token|api[_-]?key|password|passwd|secret|signature|session(?:[_-]?id)?|csrf(?:[_-]?token)?|xsrf(?:[_-]?token)?|x-csrf-token|authenticity[_-]?token)\b\s*[:=]\s*)(["']?)[^"'&<>\s,;]+(["']?)"""
    )
    private val sensitiveQuery = Regex(
        "(?i)([?&](?:token|access_token|refresh_token|api[_-]?key|key|auth|authorization|password|passwd|secret|signature|session(?:_id)?|cookie)=)[^&#\\s\"']+"
    )
    private val urlCredentials = Regex("(?i)(https?://)[^/@\\s:]+:[^/@\\s]+@")
    private val htmlInputTag = Regex("(?is)<input\\b[^>]*>")
    private val sensitiveHtmlFieldName = Regex(
        """(?i)\b(?:name|id)\s*=\s*(["']?)(?:password|passwd|token|access[_-]?token|refresh[_-]?token|api[_-]?key|secret|authorization|cookie|session[_-]?id|csrf(?:[_-]?token)?|xsrf(?:[_-]?token)?|x-csrf-token|authenticity[_-]?token)\1(?=\s|/?>|$)"""
    )
    private val quotedHtmlValue = Regex("""(?i)(\bvalue\s*=\s*)(["'])(.*?)\2""")
    private val unquotedHtmlValue = Regex("""(?i)(\bvalue\s*=\s*)([^\s>"']+)""")

    fun sanitize(input: String): String {
        val structured = runCatching { JsonParser.parseString(input) }.getOrNull()
        if (structured != null && (structured.isJsonObject || structured.isJsonArray)) {
            return sanitizeStructuredJson(structured).toString()
        }
        return sanitizeText(input)
    }

    private fun sanitizeStructuredJson(element: JsonElement): JsonElement = when {
        element.isJsonObject -> JsonObject().apply {
            element.asJsonObject.entrySet().forEach { (name, value) ->
                add(
                    name,
                    if (sensitiveJsonField.matches(name)) {
                        JsonPrimitive("[REDACTED]")
                    } else {
                        sanitizeStructuredJson(value)
                    }
                )
            }
        }

        element.isJsonArray -> JsonArray().apply {
            element.asJsonArray.forEach { add(sanitizeStructuredJson(it)) }
        }

        element.isJsonPrimitive && element.asJsonPrimitive.isString ->
            JsonPrimitive(sanitizeText(element.asString))

        else -> element.deepCopy()
    }

    private fun sanitizeText(input: String): String {
        val redacted = input
            .replace(secretJson) { "${it.groupValues[1]}[REDACTED]${it.groupValues[2]}" }
            .replace(authHeader) { "${it.groupValues[1]}[REDACTED]" }
            .replace(cookieHeader) { "${it.groupValues[1]}[REDACTED]" }
            .replace(bearer) { "Bearer [REDACTED]" }
            .replace(sensitiveAssignment) {
                "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]${it.groupValues[3]}"
            }
            .replace(sensitiveQuery) { "${it.groupValues[1]}[REDACTED]" }
            .replace(urlCredentials) { "${it.groupValues[1]}[REDACTED]@" }
        return htmlInputTag.replace(redacted) { match ->
            val tag = match.value
            if (!sensitiveHtmlFieldName.containsMatchIn(tag)) return@replace tag
            val quoted = quotedHtmlValue.replace(tag) {
                "${it.groupValues[1]}${it.groupValues[2]}[REDACTED]${it.groupValues[2]}"
            }
            unquotedHtmlValue.replace(quoted) {
                "${it.groupValues[1]}[REDACTED]"
            }
        }
    }
}
