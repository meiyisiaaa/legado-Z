package io.legado.app.model.jsSource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils

/** Maps the T synchronous JS-source JSON contract onto local entities (GPL-3.0 protocol). */
object JsSourceMarshaller {

    fun parseSearchBooks(json: String?, source: BookSource): ArrayList<SearchBook> {
        val array = parseArray(json, "search")
        return ArrayList<SearchBook>(array.size()).apply {
            array.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val book = runCatching { GSON.fromJson(obj, SearchBook::class.java) }
                    .getOrNull() ?: return@forEach
                if (book.name.isBlank() || book.bookUrl.isBlank()) return@forEach
                book.bookUrl = absolute(source.bookSourceUrl, book.bookUrl)
                book.coverUrl = book.coverUrl?.takeIf { it.isNotBlank() }
                    ?.let { absolute(book.bookUrl, it) }
                if (book.tocUrl.isNotBlank()) {
                    book.tocUrl = absolute(book.bookUrl, book.tocUrl)
                }
                book.origin = source.bookSourceUrl
                book.originName = source.bookSourceName
                book.originOrder = source.customOrder
                book.type = resolveType(obj, source)
                add(book)
            }
        }
    }

    fun mergeBookInfo(
        book: Book,
        json: String?,
        source: BookSource,
        canReName: Boolean = true
    ) {
        if (json.isNullOrBlank()) return
        val obj = runCatching { GSON.fromJson(json, JsonObject::class.java) }.getOrNull()
            ?: throw NoStackTraceException("getBookInfo 返回值不是 JSON 对象")
        obj.entrySet().forEach { (key, value) ->
            if (value.isJsonNull) return@forEach
            when (key) {
                "name" -> if (canReName || book.name.isBlank()) book.name = value.asString
                "author" -> if (canReName || book.author.isBlank()) book.author = value.asString
                "intro" -> book.intro = value.asString
                "coverUrl" -> book.coverUrl = absolute(book.bookUrl, value.asString)
                "kind" -> book.kind = value.asString
                "wordCount" -> book.wordCount = value.asString
                "latestChapterTitle" -> book.latestChapterTitle = value.asString
                "tocUrl" -> book.tocUrl = absolute(book.bookUrl, value.asString)
                "downloadUrls" -> {
                    val values = value.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
                        it.takeUnless { element -> element.isJsonNull }?.let { element ->
                            runCatching { absolute(book.bookUrl, element.asString) }.getOrNull()
                        }
                    }
                    if (values == null) {
                        Debug.log(source.bookSourceUrl, "⇒downloadUrls 必须是字符串数组")
                    } else {
                        book.downloadUrls = values
                    }
                }
                "variable" -> {
                    val variableJson = when {
                        value.isJsonObject -> value.toString()
                        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
                        else -> null
                    }
                    val values: Map<String, String>? = variableJson?.let {
                        runCatching {
                            GSON.fromJson<Map<String, String>>(it, MAP_TYPE)
                        }.getOrNull()
                    }
                    if (values == null) {
                        Debug.log(source.bookSourceUrl, "⇒variable 不是合法 JSON 对象，已忽略")
                    } else {
                        book.variableMap.clear()
                        book.variableMap.putAll(values)
                        book.variable = GSON.toJson(book.variableMap)
                    }
                }
                "type" -> value.asIntOrNull()?.let(::validateBookType)?.let {
                    book.type = it
                }
            }
        }
    }

    fun parseChapters(json: String?, book: Book): List<BookChapter> {
        val array = parseArray(json, "getChapters")
        val chapters = ArrayList<BookChapter>(array.size())
        array.forEach { item ->
            val chapter = runCatching { GSON.fromJson(item, BookChapter::class.java) }
                .getOrNull() ?: return@forEach
            if (chapter.title.isBlank() || chapter.url.isBlank()) return@forEach
            if (!(chapter.isVolume && chapter.url == chapter.title)) {
                chapter.url = absolute(book.tocUrl, chapter.url)
            }
            chapter.bookUrl = book.bookUrl
            chapter.baseUrl = book.tocUrl
            chapter.index = chapters.size
            chapters.add(chapter)
        }
        return chapters
    }

    private fun parseArray(json: String?, functionName: String): JsonArray {
        if (json.isNullOrBlank()) return JsonArray()
        return runCatching { GSON.fromJson(json, JsonArray::class.java) }.getOrNull()
            ?: throw NoStackTraceException("$functionName 必须返回 JSON 数组")
    }

    private fun resolveType(obj: JsonObject, source: BookSource): Int =
        obj.get("type")?.asIntOrNull()?.let(::validateBookType) ?: source.getBookType()

    private fun validateBookType(value: Int): Int? =
        value.takeIf { it == 0 || it and BookType.allBookType.inv() == 0 }

    private fun absolute(baseUrl: String, url: String): String =
        NetworkUtils.getAbsoluteURL(baseUrl, url)

    private fun com.google.gson.JsonElement.asIntOrNull(): Int? =
        runCatching { asInt }.getOrNull()

    private val MAP_TYPE = com.google.gson.reflect.TypeToken
        .getParameterized(Map::class.java, String::class.java, String::class.java).type
}
