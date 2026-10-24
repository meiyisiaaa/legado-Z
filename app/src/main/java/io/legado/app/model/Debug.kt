package io.legado.app.model

import android.annotation.SuppressLint
import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.help.book.isWebFile
import io.legado.app.help.source.sortUrls
import io.legado.app.model.rss.Rss
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.stackTraceStr
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Routes existing source-debug logs through the current coroutine's owned run token. */
object Debug {
    val debugMessageMap = ConcurrentHashMap<String, String>()
    private val debugTimeMap = ConcurrentHashMap<String, Long>()

    /** Derived from the single runner lease; no second mutable batch state is kept here. */
    val isChecking: Boolean
        get() = DebugRunner.activeKind == DebugRunKind.CHECK_SOURCE_BATCH

    @SuppressLint("ConstantLocale")
    private val debugTimeFormat = SimpleDateFormat("[mm:ss.SSS]", Locale.getDefault())

    @Synchronized
    fun log(
        sourceUrl: String?,
        msg: String = "",
        print: Boolean = true,
        isHtml: Boolean = false,
        showTime: Boolean = true,
        state: Int = 1
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("sourceDebug", msg)
        }

        val context = DebugRunContext.currentOrNull()
        if (print && context != null) {
            var displayMessage = if (isHtml) HtmlFormatter.format(msg) else msg
            if (showTime) {
                val elapsed = System.currentTimeMillis() - context.startedAtMillis
                displayMessage = "${debugTimeFormat.format(Date(elapsed))} $displayMessage"
            }
            context.emit(sourceUrl, msg, displayMessage, state)
        }

        if (context?.kind == DebugRunKind.CHECK_SOURCE_BATCH && sourceUrl != null && msg.length < 30) {
            context.whileCurrent {
                var printMsg = if (isHtml) HtmlFormatter.format(msg) else msg
                val startTime = debugTimeMap[sourceUrl]
                if (showTime && startTime != null) {
                    val time = debugTimeFormat.format(Date(System.currentTimeMillis() - startTime))
                    printMsg = printMsg.replace(AppPattern.debugMessageSymbolRegex, "")
                    debugMessageMap[sourceUrl] = "$time $printMsg"
                }
            }
        }
    }

    @Synchronized
    fun log(msg: String?) {
        log(DebugRunContext.currentOrNull()?.sourceUrl, msg ?: "", true)
    }

    @Synchronized
    fun startChecking(source: BookSource) {
        val context = DebugRunContext.currentOrNull()
        if (context?.kind != DebugRunKind.CHECK_SOURCE_BATCH) return
        context.whileCurrent {
            debugTimeMap[source.bookSourceUrl] = System.currentTimeMillis()
            debugMessageMap[source.bookSourceUrl] = "${debugTimeFormat.format(Date(0))} 开始校验"
        }
    }

    fun getRespondTime(sourceUrl: String): Long {
        return debugTimeMap[sourceUrl] ?: CheckSource.timeout
    }

    @Synchronized
    fun updateFinalMessage(sourceUrl: String, state: String) {
        val startTime = debugTimeMap[sourceUrl] ?: return
        if (debugMessageMap[sourceUrl] == null) return
        val spendingTime = System.currentTimeMillis() - startTime
        debugTimeMap[sourceUrl] =
            if (state == "校验成功") spendingTime else CheckSource.timeout + spendingTime
        val printTime = debugTimeFormat.format(Date(spendingTime))
        debugMessageMap[sourceUrl] = "$printTime $state"
    }

    suspend fun startDebug(rssSource: RssSource) {
        val sourceUrl = rssSource.sourceUrl
        try {
            val sort = rssSource.sortUrls().first()
            log(sourceUrl, "︾开始解析")
            val (articles, _) = Rss.getArticlesAwait(sort.first, sort.second, rssSource, 1)
            if (articles.isEmpty()) {
                log(sourceUrl, "⇒列表页解析成功，为空")
                log(sourceUrl, "︽解析完成", state = 1000)
                return
            }
            parseRssArticle(sourceUrl, rssSource, articles.first())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            log(sourceUrl, failure.stackTraceStr, state = -1)
            throw failure
        }
    }

    suspend fun startDebug(rssSource: RssSource, key: String) {
        val sourceUrl = rssSource.sourceUrl
        try {
            when {
                key.contains("::") -> {
                    val name = key.substringBefore("::")
                    val url = key.substringAfter("::")
                    log(sourceUrl, "⇒开始访问分类页:$url")
                    log(sourceUrl, "︾开始解析分类页")
                    val (articles, _) = Rss.getArticlesAwait(name, url, rssSource, 1)
                    if (articles.isEmpty()) {
                        log(sourceUrl, "⇒列表页解析成功，为空")
                        log(sourceUrl, "︽解析完成", state = 1000)
                    } else {
                        parseRssArticle(sourceUrl, rssSource, articles.first())
                    }
                }

                key.isAbsUrl() -> {
                    val ruleContent = rssSource.ruleContent
                    if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
                        if (ruleContent.isNullOrEmpty()) {
                            log(sourceUrl, "⇒内容规则为空，默认获取整个网页", state = 1000)
                        } else {
                            val article = RssArticle().apply {
                                origin = sourceUrl
                                link = key
                            }
                            log(sourceUrl, "⇒开始访问内容页:$key")
                            rssContentDebug(article, ruleContent, rssSource)
                        }
                    } else {
                        log(sourceUrl, "⇒存在描述规则，不解析内容页", state = 1000)
                    }
                }

                else -> {
                    val searchUrl = rssSource.searchUrl
                    if (searchUrl.isNullOrEmpty()) {
                        log(sourceUrl, "⇒搜索URL为空", state = -1)
                        return
                    }
                    log(sourceUrl, "⇒开始搜索关键字:$key")
                    log(sourceUrl, "︾开始解析搜索页")
                    val (articles, _) = Rss.getArticlesAwait("搜索", searchUrl, rssSource, 1, key)
                    if (articles.isEmpty()) {
                        log(sourceUrl, "⇒列表页解析成功，为空")
                        log(sourceUrl, "︽解析完成", state = 1000)
                    } else {
                        parseRssArticle(sourceUrl, rssSource, articles.first())
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            log(sourceUrl, failure.stackTraceStr, state = -1)
            throw failure
        }
    }

    private suspend fun parseRssArticle(sourceUrl: String, rssSource: RssSource, article: RssArticle) {
        val ruleContent = rssSource.ruleContent
        if (!rssSource.ruleArticles.isNullOrBlank() && rssSource.ruleDescription.isNullOrBlank()) {
            log(sourceUrl, "︽列表页解析完成")
            log(sourceUrl, showTime = false)
            if (ruleContent.isNullOrEmpty()) {
                log(sourceUrl, "⇒内容规则为空，默认获取整个网页", state = 1000)
            } else {
                rssContentDebug(article, ruleContent, rssSource)
            }
        } else {
            log(sourceUrl, "⇒存在描述规则，不解析内容页", state = 1000)
        }
    }

    private suspend fun rssContentDebug(
        article: RssArticle,
        ruleContent: String,
        rssSource: RssSource
    ) {
        val sourceUrl = rssSource.sourceUrl
        log(sourceUrl, "︾开始解析内容页")
        val content = Rss.getContentAwait(article, ruleContent, rssSource)
        log(sourceUrl, content)
        log(sourceUrl, "︽内容页解析完成", state = 1000)
    }

    suspend fun startDebug(bookSource: BookSource, key: String) {
        val sourceUrl = bookSource.bookSourceUrl
        try {
            when {
                key.isAbsUrl() -> {
                    val book = Book().apply {
                        origin = sourceUrl
                        bookUrl = key
                    }
                    log(sourceUrl, "⇒开始访问详情页:$key")
                    infoDebug(bookSource, book)
                }

                key.contains("::") -> {
                    val url = key.substringAfter("::")
                    log(sourceUrl, "⇒开始访问发现页:$url")
                    exploreDebug(bookSource, url)
                }

                key.startsWith("++") -> {
                    val url = key.substring(2)
                    val book = Book().apply {
                        origin = sourceUrl
                        tocUrl = url
                    }
                    log(sourceUrl, "⇒开始访问目录页:$url")
                    tocDebug(bookSource, book)
                }

                key.startsWith("--") -> {
                    val url = key.substring(2)
                    val book = Book().apply { origin = sourceUrl }
                    val chapter = BookChapter().apply {
                        title = "调试"
                        this.url = url
                    }
                    log(sourceUrl, "⇒开始访问正文页:$url")
                    contentDebug(bookSource, book, chapter, null)
                }

                else -> {
                    log(sourceUrl, "⇒开始搜索关键字:$key")
                    searchDebug(bookSource, key)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            log(sourceUrl, failure.stackTraceStr, state = -1)
            throw failure
        }
    }

    private suspend fun exploreDebug(bookSource: BookSource, url: String) {
        val sourceUrl = bookSource.bookSourceUrl
        log(sourceUrl, "︾开始解析发现页")
        val exploreBooks = WebBook.exploreBookAwait(bookSource, url, 1)
        if (exploreBooks.isNotEmpty()) {
            log(sourceUrl, "︽发现页解析完成")
            log(sourceUrl, showTime = false)
            infoDebug(bookSource, exploreBooks[0].toBook())
        } else {
            log(sourceUrl, "︽未获取到书籍", state = -1)
        }
    }

    private suspend fun searchDebug(bookSource: BookSource, key: String) {
        val sourceUrl = bookSource.bookSourceUrl
        log(sourceUrl, "︾开始解析搜索页")
        val searchBooks = WebBook.searchBookAwait(bookSource, key, 1)
        if (searchBooks.isNotEmpty()) {
            log(sourceUrl, "︽搜索页解析完成")
            log(sourceUrl, showTime = false)
            infoDebug(bookSource, searchBooks[0].toBook())
        } else {
            log(sourceUrl, "︽未获取到书籍", state = -1)
        }
    }

    private suspend fun infoDebug(bookSource: BookSource, book: Book) {
        val sourceUrl = bookSource.bookSourceUrl
        if (book.tocUrl.isNotBlank()) {
            log(sourceUrl, "≡已获取目录链接,跳过详情页")
            log(sourceUrl, showTime = false)
            tocDebug(bookSource, book)
            return
        }
        log(sourceUrl, "︾开始解析详情页")
        WebBook.getBookInfoAwait(bookSource, book)
        log(sourceUrl, "︽详情页解析完成")
        log(sourceUrl, showTime = false)
        if (!book.isWebFile) {
            tocDebug(bookSource, book)
        } else {
            log(sourceUrl, "≡文件类书源跳过解析目录", state = 1000)
        }
    }

    private suspend fun tocDebug(bookSource: BookSource, book: Book) {
        val sourceUrl = bookSource.bookSourceUrl
        log(sourceUrl, "︾开始解析目录页")
        val chapters = WebBook.getChapterListAwait(bookSource, book).getOrThrow()
        log(sourceUrl, "︽目录页解析完成")
        log(sourceUrl, showTime = false)
        val toc = chapters.filter { !(it.isVolume && it.url.startsWith(it.title)) }
        if (toc.isEmpty()) {
            log(sourceUrl, "≡没有正文章节", state = 1000)
            return
        }
        val nextChapterUrl = toc.getOrNull(1)?.url ?: toc.first().url
        contentDebug(bookSource, book, toc.first(), nextChapterUrl)
    }

    private suspend fun contentDebug(
        bookSource: BookSource,
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String?
    ) {
        val sourceUrl = bookSource.bookSourceUrl
        log(sourceUrl, "︾开始解析正文页")
        WebBook.getContentAwait(
            bookSource = bookSource,
            book = book,
            bookChapter = chapter,
            nextChapterUrl = nextChapterUrl,
            needSave = false
        )
        log(sourceUrl, "︽正文页解析完成", state = 1000)
    }
}
