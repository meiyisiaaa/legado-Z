package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.elonen.NanoHTTPD
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class BookSourcePipelineCompatibilityTest {

    @Test
    fun traditionalRulePipelineWorksWithNullAndEmptyMainJsUsingOnlyLocalHttp() = runBlocking {
        val server = HtmlFixtureServer()
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        try {
            val baseUrl = "http://127.0.0.1:${server.listeningPort}"
            listOf<String?>(null, "").forEachIndexed { index, mainJs ->
                val source = traditionalSource(baseUrl, mainJs, index)
                assertFalse(source.isJsSource())

                val searchBook = WebBook.searchBookAwait(source, "fixture").single()
                assertEquals("Legacy Search Book", searchBook.name)
                assertEquals("Legacy Search Author", searchBook.author)
                assertEquals("$baseUrl/book", searchBook.bookUrl)

                val book = WebBook.getBookInfoAwait(source, searchBook.toBook())
                assertEquals("Legacy Detail Book", book.name)
                assertEquals("Legacy Detail Author", book.author)
                assertEquals("$baseUrl/toc", book.tocUrl)

                val chapter = WebBook.getChapterListAwait(source, book).getOrThrow().single()
                assertEquals("Legacy Chapter", chapter.title)
                assertEquals("/chapter", chapter.url)
                assertEquals("$baseUrl/chapter", chapter.getAbsoluteURL())

                val content = WebBook.getContentAwait(
                    bookSource = source,
                    book = book,
                    bookChapter = chapter,
                    nextChapterUrl = "$baseUrl/next",
                    needSave = false
                )
                assertTrue(content.contains("Traditional fixture content"))
            }

            assertEquals(
                listOf("/search", "/book", "/toc", "/chapter", "/search", "/book", "/toc", "/chapter"),
                server.requests
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun singleJsFunctionsReachWebBookAndRetainHostAndArgumentBindings() = runBlocking {
        val baseUrl = "http://127.0.0.1:12345"
        val script = """
            var config = { bookSourceUrl: '$baseUrl', bookSourceName: 'fixture-js' };
            function sameText(actual, expected) {
              return String(actual) === String(expected);
            }
            function checkSource() {
              if (!sameText(baseUrl, source.getKey())) throw new Error('source binding was not forwarded');
            }
            function search(key, page) {
              checkSource();
              if (String(key) !== 'fixture-key' || Number(page) !== 9) throw new Error('search arguments were not forwarded');
              return [{ name: 'JS Search Book', author: 'JS Search Author',
                bookUrl: '/book/' + java.md5Encode('abc') }];
            }
            function getBookInfo(book) {
              checkSource();
              if (!sameText(book.bookUrl, baseUrl + '/book/' + java.md5Encode('abc')))
                throw new Error('book was not forwarded to getBookInfo');
              return { name: 'Info ' + java.md5Encode(book.bookUrl),
                author: 'Author ' + java.md5Encode('abc'), tocUrl: '/toc' };
            }
            function getChapters(book) {
              checkSource();
              if (!sameText(book.tocUrl, baseUrl + '/toc')) throw new Error('updated book was not forwarded');
              return [{ title: 'Chapter ' + java.md5Encode(book.name), url: '/chapter' }];
            }
            function getContent(chapter, book, nextChapterUrl) {
              checkSource();
              if (!sameText(chapter.title, 'Chapter ' + java.md5Encode(book.name)))
                throw new Error('chapter argument was not forwarded');
              if (!sameText(chapter.bookUrl, book.bookUrl)) throw new Error('chapter book was not forwarded');
              if (!sameText(nextChapterUrl, baseUrl + '/next')) throw new Error('nextChapterUrl was not forwarded');
              return 'JS content|' + java.md5Encode('abc') + '|' + chapter.title + '|' + book.name + '|' + nextChapterUrl;
            }
        """.trimIndent()
        val source = BookSource(
            bookSourceUrl = baseUrl,
            bookSourceName = "fixture-js",
            mainJs = script
        )

        val searchBook = WebBook.searchBookAwait(source, "fixture-key", page = 9).single()
        val expectedBookUrl = "$baseUrl/book/${MD5Utils.md5Encode("abc")}"
        assertEquals(expectedBookUrl, searchBook.bookUrl)

        val book = WebBook.getBookInfoAwait(source, searchBook.toBook())
        val expectedBookName = "Info ${MD5Utils.md5Encode(expectedBookUrl)}"
        assertEquals(expectedBookName, book.name)
        assertEquals("Author ${MD5Utils.md5Encode("abc")}", book.author)
        assertEquals("$baseUrl/toc", book.tocUrl)

        val chapter = WebBook.getChapterListAwait(source, book).getOrThrow().single()
        val expectedChapterTitle = "Chapter ${MD5Utils.md5Encode(expectedBookName)}"
        assertEquals(expectedChapterTitle, chapter.title)
        assertEquals("$baseUrl/chapter", chapter.url)

        val nextChapterUrl = "$baseUrl/next"
        val content = WebBook.getContentAwait(
            bookSource = source,
            book = book,
            bookChapter = chapter,
            nextChapterUrl = nextChapterUrl,
            needSave = false
        )
        assertEquals(
            "JS content|${MD5Utils.md5Encode("abc")}|$expectedChapterTitle|$expectedBookName|$nextChapterUrl",
            content
        )
    }

    private fun traditionalSource(baseUrl: String, mainJs: String?, index: Int) = BookSource(
        bookSourceUrl = baseUrl,
        bookSourceName = "legacy-$index",
        mainJs = mainJs,
        searchUrl = "$baseUrl/search?key={{key}}",
        ruleSearch = SearchRule(
            bookList = ".book-list li",
            name = "a.name@text",
            author = ".author@text",
            bookUrl = "a.name@href"
        ),
        ruleBookInfo = BookInfoRule(
            name = ".title@text",
            author = ".author@text",
            tocUrl = "a.toc@href",
            canReName = "1"
        ),
        ruleToc = TocRule(
            chapterList = ".chapter-list li",
            chapterName = "a@text",
            chapterUrl = "a@href"
        ),
        ruleContent = ContentRule(content = "#content@text")
    )

    private class HtmlFixtureServer : NanoHTTPD("127.0.0.1", 0) {
        val requests = CopyOnWriteArrayList<String>()

        override fun serve(session: IHTTPSession): Response {
            requests.add(session.uri)
            val body = when (session.uri) {
                "/search" -> """
                    <ul class="book-list"><li>
                      <a class="name" href="/book">Legacy Search Book</a>
                      <span class="author">Legacy Search Author</span>
                    </li></ul>
                """.trimIndent()

                "/book" -> """
                    <h1 class="title">Legacy Detail Book</h1>
                    <span class="author">Legacy Detail Author</span>
                    <a class="toc" href="/toc">Table of contents</a>
                """.trimIndent()

                "/toc" -> """
                    <ul class="chapter-list"><li><a href="/chapter">Legacy Chapter</a></li></ul>
                """.trimIndent()

                "/chapter" -> "<div id=\"content\">Traditional fixture content</div>"
                else -> return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain; charset=utf-8",
                    "unexpected fixture path: ${session.uri}"
                )
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                body
            )
        }
    }
}
