package io.legado.app.help.source

import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.jsSource.JsSourceConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext

/** Shared parser and overwrite merge for Android import, web tooling and MCP. */
object BookSourceImport {

    /**
     * Parses raw single-file JS or source JSON. For JSON carrying `mainJs`, script
     * config is canonical and user-managed fields from the JSON envelope are kept.
     */
    suspend fun parse(text: String, coroutineContext: CoroutineContext): BookSource {
        val input = text.trim()
        if (input.isEmpty()) throw NoStackTraceException("书源内容不能为空")
        val parsed = when {
            input.isJsonObject() -> GSON.fromJsonObject<BookSource>(input).getOrThrow()
            input.isJsonArray() -> GSON.fromJsonArray<BookSource>(input)
                .getOrThrow().firstOrNull()
                ?: throw NoStackTraceException("书源数组为空")

            else -> return JsSourceConfig.extract(text, coroutineContext)
        }
        return canonicalize(parsed, coroutineContext)
    }

    /** Rebuilds script-owned fields from mainJs, preserving local user settings. */
    suspend fun canonicalize(
        source: BookSource,
        coroutineContext: CoroutineContext
    ): BookSource {
        val mainJs = source.mainJs?.takeIf { it.isNotBlank() } ?: return source
        val canonical = try {
            JsSourceConfig.extract(mainJs, coroutineContext)
        } catch (e: CancellationException) {
            throw e
        }
        return preserveUserSettings(source, canonical)
    }

    /** Applies incoming source content while retaining the existing local settings. */
    fun mergeOverwrite(existing: BookSource, incoming: BookSource): BookSource =
        preserveUserSettings(existing, incoming)

    private fun preserveUserSettings(settings: BookSource, source: BookSource): BookSource =
        source.copy(
            bookSourceGroup = settings.bookSourceGroup,
            customOrder = settings.customOrder,
            enabled = settings.enabled,
            enabledExplore = settings.enabledExplore,
            enabledCookieJar = settings.enabledCookieJar,
            weight = settings.weight,
            eventListener = settings.eventListener,
            customButton = settings.customButton
        )
}
