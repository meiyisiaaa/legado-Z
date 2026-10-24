package io.legado.app.model.jsSource

import com.google.gson.JsonElement
import io.legado.app.R
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.exception.TocEmptyException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeAllBookType
import io.legado.app.help.source.getBookType
import io.legado.app.model.Debug
import io.legado.app.model.webBook.BookChapterList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx

/** Synchronous T-compatible single-file source calls, routed through WebBook. */
object JsSourceBook {

    suspend fun searchAwait(
        source: BookSource,
        key: String,
        page: Int?,
        filter: ((String, String, String?) -> Boolean)?,
        shouldBreak: ((Int) -> Boolean)?
    ): ArrayList<SearchBook> {
        val engine = JsSourceEngine(source, currentCoroutineContext())
        val raw = engine.callFunction("search", listOf("key" to key, "page" to (page ?: 1)))
        Debug.log(source.bookSourceUrl, "≡函数执行成功:search")
        Debug.log(source.bookSourceUrl, raw.orEmpty(), state = 10)
        val parsed = JsSourceMarshaller.parseSearchBooks(raw, source)
        val result = arrayListOf<SearchBook>()
        for (book in parsed) {
            if (filter == null || filter(book.name, book.author, book.kind)) result.add(book)
            if (shouldBreak?.invoke(result.size) == true) break
        }
        logBookList(source, result)
        currentCoroutineContext().ensureActive()
        return result
    }

    suspend fun exploreAwait(source: BookSource, url: String, page: Int?): ArrayList<SearchBook> {
        val engine = JsSourceEngine(source, currentCoroutineContext())
        val raw = engine.callFunction("explore", listOf("url" to url, "page" to (page ?: 1)))
        Debug.log(source.bookSourceUrl, "≡函数执行成功:explore")
        Debug.log(source.bookSourceUrl, raw.orEmpty(), state = 10)
        val books = JsSourceMarshaller.parseSearchBooks(raw, source)
        logBookList(source, books)
        currentCoroutineContext().ensureActive()
        return books
    }

    suspend fun getBookInfoAwait(
        source: BookSource,
        book: Book,
        canReName: Boolean = true
    ): Book {
        book.removeAllBookType()
        book.addType(source.getBookType())
        val raw = JsSourceEngine(source, currentCoroutineContext())
            .callFunctionIfExists("getBookInfo", listOf("book" to book))
        if (raw != null) {
            Debug.log(source.bookSourceUrl, "≡函数执行成功:getBookInfo")
            Debug.log(source.bookSourceUrl, raw, state = 20)
        }
        JsSourceMarshaller.mergeBookInfo(book, raw, source, canReName)
        if (book.isWebFile) {
            if (book.downloadUrls.isNullOrEmpty()) throw NoStackTraceException("下载链接为空")
        } else if (book.tocUrl.isBlank()) {
            book.tocUrl = book.bookUrl
        }
        currentCoroutineContext().ensureActive()
        return book
    }

    /** Returns the source-defined review summary payload as serialized JSON. */
    suspend fun getReviewSummaryAwait(source: BookSource, book: Book): String? {
        val raw = JsSourceEngine(source, currentCoroutineContext())
            .callFunction("getReviewSummary", listOf("book" to book))
        Debug.log(source.bookSourceUrl, "≡函数执行成功:getReviewSummary")
        Debug.log(source.bookSourceUrl, raw.orEmpty(), state = 20)
        currentCoroutineContext().ensureActive()
        return raw
    }

    /** Passes one summary item as a JSON value to the paired detail function. */
    suspend fun getReviewDetailAwait(source: BookSource, review: JsonElement): String? {
        val raw = JsSourceEngine(source, currentCoroutineContext()).callFunctionWithBindings(
            name = "getReviewDetail",
            args = listOf("review" to review.toString()),
            bindings = emptyList(),
            jsonArgumentNames = setOf("review")
        )
        Debug.log(source.bookSourceUrl, "≡函数执行成功:getReviewDetail")
        Debug.log(source.bookSourceUrl, raw.orEmpty(), state = 20)
        currentCoroutineContext().ensureActive()
        return raw
    }

    suspend fun getChapterListAwait(source: BookSource, book: Book): List<BookChapter> {
        if (source.bookSourceType == BookSourceType.file) {
            throw NoStackTraceException("文件类型JS书源不支持 getChapters")
        }
        book.removeAllBookType()
        book.addType(source.getBookType())
        val raw = JsSourceEngine(source, currentCoroutineContext())
            .callFunction("getChapters", listOf("book" to book))
        Debug.log(source.bookSourceUrl, "≡函数执行成功:getChapters")
        Debug.log(source.bookSourceUrl, raw.orEmpty(), state = 30)
        val chapters = JsSourceMarshaller.parseChapters(raw, book)
        if (chapters.isEmpty()) {
            throw TocEmptyException(appCtx.getString(R.string.chapter_list_empty))
        }
        BookChapterList.updateBookTocInfo(book, chapters)
        currentCoroutineContext().ensureActive()
        return chapters
    }

    suspend fun getContentAwait(
        source: BookSource,
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String?,
        needSave: Boolean
    ): String {
        if (source.bookSourceType == BookSourceType.file) {
            throw NoStackTraceException("文件类型JS书源不支持 getContent")
        }
        if (chapter.isVolume && chapter.url.startsWith(chapter.title)) return ""
        val raw = JsSourceEngine(source, currentCoroutineContext()).callFunction(
            "getContent",
            listOf(
                "chapter" to chapter,
                "book" to book,
                "nextChapterUrl" to nextChapterUrl
            )
        )
        val content = raw.orEmpty()
        if (content.isBlank()) throw ContentEmptyException("内容为空")
        Debug.log(source.bookSourceUrl, "≡函数执行成功:getContent")
        Debug.log(source.bookSourceUrl, content, state = 40)
        if (needSave) BookHelp.saveContent(source, book, chapter, content)
        currentCoroutineContext().ensureActive()
        return content
    }

    private fun logBookList(source: BookSource, books: List<SearchBook>) {
        Debug.log(source.bookSourceUrl, "┌获取书籍列表")
        Debug.log(source.bookSourceUrl, "└列表大小:${books.size}")
        Debug.log(source.bookSourceUrl, "◇书籍总数:${books.size}")
    }
}
