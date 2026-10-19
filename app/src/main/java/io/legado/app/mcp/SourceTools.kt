package io.legado.app.mcp

import androidx.sqlite.db.SimpleSQLiteQuery
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.source.BookSourceImport
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.model.Debug
import io.legado.app.model.DebugLogSanitizer
import io.legado.app.model.DebugRunEvent
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunResult
import io.legado.app.model.DebugRunSnapshot
import io.legado.app.model.DebugRunner
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.GSON
import io.legado.app.utils.isAbsUrl
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.Callable

private const val MAX_SOURCE_CHARS = 1_000_000
private const val MAX_URL_CHARS = 4_096
private const val MAX_NAME_CHARS = 512
private const val MAX_GROUP_CHARS = 512
private const val MAX_QUERY_CHARS = 256
private const val MAX_PAGE = 1_000_000
private const val MAX_DELETE_URLS = 100
private const val MAX_TOOL_RESPONSE_BYTES = 1_000_000
private const val MAX_DEBUG_TEXT_CHARS = 48_000

/**
 * Creates one source-tool server for a single authenticated MCP session.
 * The caller supplies a unique owner so overlapping debug calls cannot cancel another session.
 */
fun createSourceServer(owner: String): Server {
    require(owner.isNotBlank() && owner.length <= 256) { "Invalid MCP owner" }

    val server = Server(
        Implementation(name = "legado-source-tools", version = "1.0.0"),
        ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)))
    )

    server.addTool(
        name = "list_sources",
        description = "List book-source summaries with bounded pagination.",
        inputSchema = schema(
            properties = buildJsonObject {
                put("query", stringProperty("Optional substring matched against source name, URL, or group."))
                put("page", integerProperty("1-based page number; defaults to 1."))
                put("limit", integerProperty("Items per page, from 1 to 100; defaults to 50."))
            }
        )
    ) { request ->
        toolCall {
            val args = ToolArgs(request, setOf("query", "page", "limit"))
            val query = args.optionalString("query", MAX_QUERY_CHARS)?.trim().orEmpty()
            val page = args.optionalInt("page") ?: 1
            val limit = args.optionalInt("limit") ?: 50
            if (page !in 1..MAX_PAGE) fail("INVALID_ARGUMENT", "page must be between 1 and $MAX_PAGE")
            if (limit !in 1..100) fail("INVALID_ARGUMENT", "limit must be between 1 and 100")
            withContext(Dispatchers.IO) {
                listSources(query, page, limit)
            }
        }
    }

    server.addTool(
        name = "get_source",
        description = "Export a saved book source as restorable JSON or its original single-file JavaScript.",
        inputSchema = schema(
            properties = buildJsonObject {
                put("url", stringProperty("Exact bookSourceUrl of the saved source."))
                put("format", enumStringProperty(listOf("json", "js"), "Export format; defaults to json."))
            },
            required = listOf("url")
        )
    ) { request ->
        toolCall {
            val args = ToolArgs(request, setOf("url", "format"))
            val url = args.requiredString("url", MAX_URL_CHARS)
            val format = args.optionalString("format", 8)?.lowercase() ?: "json"
            if (format != "json" && format != "js") {
                fail("INVALID_ARGUMENT", "format must be json or js")
            }
            withContext(Dispatchers.IO) {
                val source = appDb.bookSourceDao.getBookSource(url)
                    ?: fail("NOT_FOUND", "No saved source has that URL")
                val exported = when (format) {
                    "json" -> GSON.toJson(source)
                    else -> source.mainJs?.takeIf { it.isNotBlank() }
                        ?: fail("UNAVAILABLE_FORMAT", "This source has no original single-file JavaScript")
                }
                if (exported.toByteArray(Charsets.UTF_8).size > MAX_TOOL_RESPONSE_BYTES) {
                    fail("SOURCE_TOO_LARGE", "Export exceeds the 1 MB MCP response limit")
                }
                textResult(exported)
            }
        }
    }

    server.addTool(
        name = "save_source",
        description = "Parse and save one inline JSON or single-file JavaScript book source.",
        inputSchema = schema(
            properties = buildJsonObject {
                put("source", stringProperty("Inline source JSON or raw single-file JavaScript; maximum 1,000,000 characters."))
                put("overwrite", booleanProperty("When true, replace source rules while preserving local settings; defaults to false."))
            },
            required = listOf("source")
        )
    ) { request ->
        toolCall {
            val args = ToolArgs(request, setOf("source", "overwrite"))
            val sourceText = args.requiredString("source", MAX_SOURCE_CHARS)
            val overwrite = args.optionalBoolean("overwrite") ?: false
            val parsed = parseSource(sourceText)
            validateSource(parsed)

            val saveResult = withContext(Dispatchers.IO) {
                appDb.runInTransaction(Callable {
                    val existing = appDb.bookSourceDao.getBookSource(parsed.bookSourceUrl)
                    if (existing != null && !overwrite) {
                        SourceSaveResult(saved = null, previous = existing, conflict = true)
                    } else {
                        val saved = existing?.let { BookSourceImport.mergeOverwrite(it, parsed) } ?: parsed
                        appDb.bookSourceDao.insert(saved)
                        SourceSaveResult(saved = saved, previous = existing, conflict = false)
                    }
                })
            }
            if (saveResult.conflict) {
                fail("CONFLICT", "A source with this URL already exists; set overwrite=true to replace its rules")
            }
            val saved = requireNotNull(saveResult.saved)
            val previous = saveResult.previous
            withContext(Dispatchers.IO) {
                if (previous == null) {
                    SourceHelp.adjustSortNumber()
                } else {
                    if (previous.exploreUrl != saved.exploreUrl) previous.clearExploreKindsCache()
                    if (previous.jsLib != saved.jsLib) SharedJsScope.remove(previous.jsLib)
                    SourceConfig.removeSource(saved.bookSourceUrl)
                    ConcurrentRateLimiter.concurrentRecordMap.remove(saved.bookSourceUrl)
                }
            }
            jsonResult {
                put("ok", true)
                put("url", saved.bookSourceUrl)
                put("action", if (previous == null) "inserted" else "overwritten")
            }
        }
    }

    server.addTool(
        name = "delete_sources",
        description = "Delete only the explicitly supplied saved book-source URLs.",
        inputSchema = schema(
            properties = buildJsonObject {
                put("urls", buildJsonObject {
                    put("type", "array")
                    put("items", stringProperty("Exact bookSourceUrl."))
                    put("minItems", 1)
                    put("maxItems", MAX_DELETE_URLS)
                })
            },
            required = listOf("urls")
        )
    ) { request ->
        toolCall {
            val args = ToolArgs(request, setOf("urls"))
            val urls = args.requiredStringArray("urls", MAX_DELETE_URLS, MAX_URL_CHARS)
            if (urls.distinct().size != urls.size) fail("INVALID_ARGUMENT", "urls must not contain duplicates")
            val (deletedCount, missingCount) = withContext(Dispatchers.IO) {
                val existing = urls.mapNotNull { appDb.bookSourceDao.getBookSource(it) }
                if (existing.isNotEmpty()) SourceHelp.deleteBookSources(existing)
                val found = existing.map { it.bookSourceUrl }.toSet()
                found.size to (urls.size - found.size)
            }
            jsonResult {
                put("ok", true)
                put("deletedCount", deletedCount)
                put("notFoundCount", missingCount)
            }
        }
    }

    server.addTool(
        name = "check_source",
        description = "Parse and structurally validate a saved source or inline source without network access or database writes.",
        inputSchema = schema(
            properties = buildJsonObject {
                put("url", stringProperty("Saved bookSourceUrl; use this or source."))
                put("source", stringProperty("Inline source JSON or raw single-file JavaScript, up to 1,000,000 characters; use this or url."))
            }
        )
    ) { request ->
        toolCall {
            val args = ToolArgs(request, setOf("url", "source"))
            val url = args.optionalString("url", MAX_URL_CHARS)
            val text = args.optionalString("source", MAX_SOURCE_CHARS)
            if ((url == null) == (text == null)) {
                fail("INVALID_ARGUMENT", "Provide exactly one of url or source")
            }
            if (url?.isBlank() == true) fail("INVALID_ARGUMENT", "url must not be blank")
            var response: CallToolResult? = null
            var validationFailure: ToolFailure? = null
            val run = DebugRunner.run(
                ownerId = owner,
                kind = DebugRunKind.MCP,
                sourceUrl = url,
                capture = false
            ) {
                try {
                    val source = if (url != null) {
                        val stored = withContext(Dispatchers.IO) {
                            appDb.bookSourceDao.getBookSource(url)
                                ?: fail("NOT_FOUND", "No saved source has that URL")
                        }
                        canonicalizeSaved(stored)
                    } else {
                        parseSource(text!!)
                    }
                    validateSource(source)
                    response = jsonResult {
                        put("valid", true)
                        put("validationOnly", true)
                        put("bookSourceUrl", source.bookSourceUrl)
                        put("bookSourceName", source.bookSourceName)
                        put("bookSourceType", source.bookSourceType)
                        put("singleFileJs", source.isJsSource())
                    }
                } catch (failure: ToolFailure) {
                    validationFailure = failure
                    throw failure
                }
            }
            validationFailure?.let { return@toolCall jsonError(it.code, it.message) }
            when (run) {
                is DebugRunResult.Busy -> jsonError("BUSY", "A debug or validation run owned by another MCP session is active")
                is DebugRunResult.Completed -> response
                    ?: jsonError("OPERATION_FAILED", "Source validation did not produce a result")
            }
        }
    }

    server.addTool(
        name = "debug_source",
        description = "Run one explicit source debug using a saved source or an inline draft; drafts are never persisted.",
        inputSchema = schema(
            properties = buildJsonObject {
                put("url", stringProperty("Saved bookSourceUrl; use this or draft."))
                put("draft", stringProperty("Inline source JSON or raw single-file JavaScript; use this or url."))
                put("key", stringProperty("Search keyword or source URL/rule selector such as ++TOC_URL or --CONTENT_URL."))
            },
            required = listOf("key")
        )
    ) { request ->
        toolCall {
            val args = ToolArgs(request, setOf("url", "draft", "key"))
            val url = args.optionalString("url", MAX_URL_CHARS)
            val draft = args.optionalString("draft", MAX_SOURCE_CHARS)
            val key = args.requiredString("key", MAX_URL_CHARS)
            if ((url == null) == (draft == null)) {
                fail("INVALID_ARGUMENT", "Provide exactly one of url or draft")
            }
            if (url?.isBlank() == true) fail("INVALID_ARGUMENT", "url must not be blank")
            var validationFailure: ToolFailure? = null

            when (val result = DebugRunner.run(
                ownerId = owner,
                kind = DebugRunKind.MCP,
                sourceUrl = url
            ) {
                try {
                    val source = if (url != null) {
                        withContext(Dispatchers.IO) {
                            appDb.bookSourceDao.getBookSource(url)
                                ?: fail("NOT_FOUND", "No saved source has that URL")
                        }
                    } else {
                        parseSource(draft!!)
                    }
                    validateSource(source)
                    validateDebugKey(source, key)
                    Debug.startDebug(source, key)
                } catch (failure: ToolFailure) {
                    validationFailure = failure
                    throw failure
                }
            }) {
                is DebugRunResult.Completed -> validationFailure?.let {
                    jsonError(it.code, it.message)
                } ?: textResult(result.snapshot.forMcpOutput().toJson(MAX_DEBUG_TEXT_CHARS))
                is DebugRunResult.Busy -> jsonError(
                    "BUSY",
                    "A debug or validation run owned by another MCP session is active"
                )
            }
        }
    }

    return server
}

private suspend fun listSources(query: String, page: Int, limit: Int): CallToolResult {
    val db = appDb.openHelper.readableDatabase
    val where = if (query.isEmpty()) "" else """
        where instr(lower(bookSourceName), lower(?)) > 0
           or instr(lower(bookSourceUrl), lower(?)) > 0
           or instr(lower(coalesce(bookSourceGroup, '')), lower(?)) > 0
    """.trimIndent()
    val filterArgs: Array<Any> = if (query.isEmpty()) emptyArray() else arrayOf(query, query, query)
    val total = db.query(
        SimpleSQLiteQuery("select count(*) from book_sources_part $where", filterArgs)
    ).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
    val offset = (page - 1L) * limit
    val queryArgs = filterArgs.toMutableList().apply { add(limit); add(offset) }.toTypedArray()
    val items = db.query(
        SimpleSQLiteQuery(
            """select bookSourceUrl, bookSourceName, bookSourceGroup, bookSourceType,
                enabled, enabledExplore, customOrder
                from book_sources_part $where
                order by customOrder asc, bookSourceUrl asc limit ? offset ?""".trimIndent(),
            queryArgs
        )
    ).use { cursor ->
        val urlCol = cursor.getColumnIndexOrThrow("bookSourceUrl")
        val nameCol = cursor.getColumnIndexOrThrow("bookSourceName")
        val groupCol = cursor.getColumnIndexOrThrow("bookSourceGroup")
        val typeCol = cursor.getColumnIndexOrThrow("bookSourceType")
        val enabledCol = cursor.getColumnIndexOrThrow("enabled")
        val exploreCol = cursor.getColumnIndexOrThrow("enabledExplore")
        val orderCol = cursor.getColumnIndexOrThrow("customOrder")
        buildJsonArray {
            while (cursor.moveToNext()) {
                val url = cursor.getString(urlCol).orEmpty()
                val name = cursor.getString(nameCol).orEmpty()
                val group = if (cursor.isNull(groupCol)) null else cursor.getString(groupCol)
                add(buildJsonObject {
                    put("url", url.take(MAX_URL_CHARS))
                    if (url.length > MAX_URL_CHARS) put("urlTruncated", true)
                    put("name", name.take(MAX_NAME_CHARS))
                    if (name.length > MAX_NAME_CHARS) put("nameTruncated", true)
                    if (group != null) put("group", group.take(MAX_GROUP_CHARS))
                    if (group != null && group.length > MAX_GROUP_CHARS) put("groupTruncated", true)
                    put("type", cursor.getInt(typeCol))
                    put("enabled", cursor.getInt(enabledCol) != 0)
                    put("enabledExplore", cursor.getInt(exploreCol) != 0)
                    put("customOrder", cursor.getInt(orderCol))
                })
            }
        }
    }
    val result = buildJsonObject {
        put("page", page)
        put("limit", limit)
        put("total", total)
        put("items", items)
    }
    val output = result.toString()
    if (output.toByteArray(Charsets.UTF_8).size > MAX_TOOL_RESPONSE_BYTES) {
        fail("RESULT_TOO_LARGE", "Source summaries exceed the 1 MB response limit; request a smaller limit")
    }
    return textResult(output)
}

private suspend fun parseSource(text: String): BookSource {
    if (text.isBlank()) fail("INVALID_SOURCE", "source must not be blank")
    if (text.length > MAX_SOURCE_CHARS) fail("SOURCE_TOO_LARGE", "source exceeds the 1,000,000-character input limit")
    return try {
        BookSourceImport.parse(text, currentCoroutineContext())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        fail("INVALID_SOURCE", safeParseFailure(failure))
    }
}

private suspend fun canonicalizeSaved(source: BookSource): BookSource {
    return try {
        BookSourceImport.canonicalize(source, currentCoroutineContext())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        fail("INVALID_SOURCE", safeParseFailure(failure))
    }
}

private fun safeParseFailure(failure: Exception): String {
    val detail = failure.localizedMessage
        ?.let(DebugLogSanitizer::sanitize)
        ?.replace(Regex("[\\r\\n\\t]+"), " ")
        ?.take(300)
    return detail?.takeIf { it.isNotBlank() }?.let { "Source parsing failed: $it" }
        ?: "Source JSON or JavaScript could not be parsed"
}

private fun validateSource(source: BookSource) {
    if (source.bookSourceUrl.isBlank()) fail("INVALID_SOURCE", "bookSourceUrl must not be blank")
    if (source.bookSourceUrl.length > MAX_URL_CHARS) fail("INVALID_SOURCE", "bookSourceUrl exceeds 4096 characters")
    if (source.bookSourceName.isBlank()) fail("INVALID_SOURCE", "bookSourceName must not be blank")
    if (source.bookSourceName.length > MAX_NAME_CHARS) fail("INVALID_SOURCE", "bookSourceName exceeds 512 characters")
    if (source.bookSourceType !in BookSourceType.default..BookSourceType.video) {
        fail("INVALID_SOURCE", "bookSourceType must be between 0 and 4")
    }
    if ((source.mainJs?.length ?: 0) > MAX_SOURCE_CHARS) {
        fail("INVALID_SOURCE", "mainJs exceeds the 1,000,000-character source limit")
    }
}

private fun validateDebugKey(source: BookSource, key: String) {
    if (key.length > MAX_URL_CHARS) fail("INVALID_ARGUMENT", "key exceeds 4096 characters")
    if (source.bookSourceType == BookSourceType.file &&
        (!key.isAbsUrl() || key.startsWith("++") || key.startsWith("--") || key.contains("::"))
    ) {
        fail(
            "INVALID_ARGUMENT",
            "File sources can only debug a direct metadata/download-page URL; content and discovery selectors are disabled"
        )
    }
}

/** Drop raw response bodies and parsed field values before returning diagnostics over MCP. */
internal fun DebugRunSnapshot.forMcpOutput(): DebugRunSnapshot = copy(
    events = events.filterNot { event ->
        event.state in setOf(10, 20, 30, 40) || event.state < 0 ||
            event.payload.trimStart().startsWith("└")
    }.map { event ->
        event.copy(
            payload = DebugLogSanitizer.sanitize(event.payload),
            displayMessage = DebugLogSanitizer.sanitize(event.displayMessage)
        )
    },
    error = if (terminalState == "failed") {
        "Debug failed; details are omitted to protect source and book content"
    } else {
        null
    }
)

private class ToolFailure(val code: String, override val message: String) : Exception(message)

private data class SourceSaveResult(
    val saved: BookSource?,
    val previous: BookSource?,
    val conflict: Boolean
)

private fun fail(code: String, message: String): Nothing = throw ToolFailure(code, message)

private suspend fun toolCall(block: suspend () -> CallToolResult): CallToolResult = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: ToolFailure) {
    jsonError(failure.code, failure.message)
} catch (_: NoStackTraceException) {
    jsonError("OPERATION_FAILED", "Source operation failed")
} catch (_: Exception) {
    jsonError("OPERATION_FAILED", "Source operation failed")
}

private fun jsonError(code: String, message: String): CallToolResult = textResult(
    buildJsonObject {
        put("error", buildJsonObject {
            put("code", code)
            put("message", DebugLogSanitizer.sanitize(message).take(500))
        })
    }.toString(),
    isError = true
)

private fun jsonResult(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): CallToolResult =
    textResult(buildJsonObject(block).toString())

private fun textResult(text: String, isError: Boolean = false): CallToolResult = CallToolResult(
    content = listOf(TextContent(text = text)),
    isError = isError
)

private class ToolArgs(request: CallToolRequest, private val allowed: Set<String>) {
    private val values = request.arguments ?: JsonObject(emptyMap())

    init {
        val unknown = values.keys - allowed
        if (unknown.isNotEmpty()) fail("INVALID_ARGUMENT", "Unknown argument: ${unknown.first().take(80)}")
    }

    fun requiredString(name: String, maxChars: Int): String =
        optionalString(name, maxChars)
            ?.takeIf { it.isNotBlank() }
            ?: fail("INVALID_ARGUMENT", "$name is required and must not be blank")

    fun optionalString(name: String, maxChars: Int): String? {
        val element = values[name] ?: return null
        val primitive = element as? JsonPrimitive
            ?: fail("INVALID_ARGUMENT", "$name must be a string")
        if (!primitive.isString) fail("INVALID_ARGUMENT", "$name must be a string")
        val value = primitive.content
        if (value.length > maxChars) fail("INVALID_ARGUMENT", "$name exceeds $maxChars characters")
        return value
    }

    fun optionalInt(name: String): Int? {
        val element = values[name] ?: return null
        val primitive = element as? JsonPrimitive
            ?: fail("INVALID_ARGUMENT", "$name must be an integer")
        if (primitive.isString) fail("INVALID_ARGUMENT", "$name must be an integer")
        return primitive.intOrNull ?: fail("INVALID_ARGUMENT", "$name must be an integer")
    }

    fun optionalBoolean(name: String): Boolean? {
        val element = values[name] ?: return null
        val primitive = element as? JsonPrimitive
            ?: fail("INVALID_ARGUMENT", "$name must be a boolean")
        if (primitive.isString) fail("INVALID_ARGUMENT", "$name must be a boolean")
        return primitive.booleanOrNull ?: fail("INVALID_ARGUMENT", "$name must be a boolean")
    }

    fun requiredStringArray(name: String, maxItems: Int, maxChars: Int): List<String> {
        val array = values[name] as? JsonArray ?: fail("INVALID_ARGUMENT", "$name must be an array")
        if (array.isEmpty() || array.size > maxItems) {
            fail("INVALID_ARGUMENT", "$name must contain between 1 and $maxItems items")
        }
        return array.mapIndexed { index, element ->
            val primitive = element as? JsonPrimitive
                ?: fail("INVALID_ARGUMENT", "$name[$index] must be a string")
            if (!primitive.isString) fail("INVALID_ARGUMENT", "$name[$index] must be a string")
            primitive.content.also {
                if (it.isBlank()) fail("INVALID_ARGUMENT", "$name[$index] must not be blank")
                if (it.length > maxChars) fail("INVALID_ARGUMENT", "$name[$index] exceeds $maxChars characters")
            }
        }
    }
}

private fun schema(
    properties: JsonObject,
    required: List<String> = emptyList()
): ToolSchema = ToolSchema(properties = properties, required = required)

private fun stringProperty(description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
}

private fun integerProperty(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

private fun booleanProperty(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

private fun enumStringProperty(values: List<String>, description: String): JsonObject = buildJsonObject {
    put("type", "string")
    put("enum", JsonArray(values.map(::JsonPrimitive)))
    put("description", description)
}
