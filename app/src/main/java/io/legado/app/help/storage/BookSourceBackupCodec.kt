package io.legado.app.help.storage

import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.BookSourceImport
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlin.coroutines.CoroutineContext

/** Single-file source normalization shared by the backup export and restore paths. */
object BookSourceBackupCodec {

    fun encode(sources: List<BookSource>): String = GSON.toJson(sources)

    suspend fun decode(json: String, coroutineContext: CoroutineContext): List<BookSource>? {
        val sources = GSON.fromJsonArray<BookSource>(json).getOrNull() ?: return null
        return canonicalize(sources, coroutineContext)
    }

    suspend fun canonicalize(
        sources: List<BookSource>,
        coroutineContext: CoroutineContext
    ): List<BookSource> = sources.map { BookSourceImport.canonicalize(it, coroutineContext) }
}
