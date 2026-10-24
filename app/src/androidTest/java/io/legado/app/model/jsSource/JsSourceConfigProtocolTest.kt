package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import com.google.gson.JsonArray
import org.junit.Assert.assertNull

@RunWith(AndroidJUnit4::class)
class JsSourceConfigProtocolTest {

    @Test
    fun normalSourceExtractsOptionalDiscoverLoginAndReviewPairsAndKeepsRawScript() = runBlocking {
        val script = """
            var config = {
              bookSourceUrl: 'https://example.org',
              bookSourceName: 'single-file',
              exploreUrl: [{ title: 'Latest', url: 'https://example.org/latest' }]
            };
            function search(key, page) { return []; }
            function explore(url, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
            function loginUi(state) { return { rows: [] }; }
            function loginAction(action, state, form) { return { state: state }; }
            function getReviewSummary(book) { return []; }
            function getReviewDetail(review) { return {}; }
        """.trimIndent()

        val source = JsSourceConfig.extract(script, currentCoroutineContext())

        assertEquals(script, source.mainJs)
        assertEquals(JsSourceConfig.LOGIN_UI_V2_MARKER, source.loginUi)
        assertTrue(source.exploreUrl.orEmpty().contains("Latest"))
    }

    @Test
    fun fileSourceRequiresSearchAndBookInfoWithoutChapterFunctions() = runBlocking {
        val script = """
            var config = {
              bookSourceUrl: 'https://files.example.org',
              bookSourceName: 'file source',
              bookSourceType: ${BookSourceType.file}
            };
            function search(key, page) { return []; }
            function getBookInfo(book) { return { downloadUrls: [] }; }
        """.trimIndent()

        val source = JsSourceConfig.extract(script, currentCoroutineContext())

        assertEquals(BookSourceType.file, source.bookSourceType)
        assertEquals(script, source.mainJs)
    }

    @Test
    fun reviewFunctionsMustBeDeclaredAsPair() = runBlocking {
        val script = """
            var config = { bookSourceUrl: 'https://example.org', bookSourceName: 'single-file' };
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
            function getReviewSummary(book) { return []; }
        """.trimIndent()

        val error = runCatching {
            JsSourceConfig.extract(script, currentCoroutineContext())
        }.exceptionOrNull()

        assertTrue(error is NoStackTraceException)
    }

    @Test
    fun v2LoginFunctionsReceiveJsonObjectsAndReturnSerializableRows() = runBlocking {
        val script = """
            var config = { bookSourceUrl: 'https://example.org', bookSourceName: 'single-file' };
            function loginUi(state) {
              return { rows: [{ type: 'text', name: 'Account', key: 'account', value: state.account || '' }] };
            }
            function loginAction(action, state, form) {
              return { state: { account: form.account }, login: true };
            }
        """.trimIndent()
        val source = BookSource(
            bookSourceUrl = "https://example.org",
            bookSourceName = "single-file",
            mainJs = script
        )
        val engine = JsSourceEngine(source, currentCoroutineContext())

        val uiJson = engine.callFunctionWithBindings(
            "loginUi",
            listOf("state" to "{}"),
            emptyList(),
            setOf("state")
        )
        val ui = io.legado.app.ui.login.SourceLoginV2Ui.parse(uiJson)
        val actionJson = engine.callFunctionWithBindings(
            "loginAction",
            listOf("action" to "submit", "state" to "{}", "form" to "{\"account\":\"reader\"}"),
            emptyList(),
            setOf("state", "form")
        )

        assertEquals("account", ui.rows.single().key)
        assertTrue(actionJson.orEmpty().contains("\"account\":\"reader\""))
        assertTrue(actionJson.orEmpty().contains("\"login\":true"))
    }

    @Test
    fun reviewSummaryAndDetailAreDispatchedThroughWebBook() = runBlocking {
        val script = """
            function getReviewSummary(book) { return [{ id: book.bookUrl + '#review' }]; }
            function getReviewDetail(review) { return { text: 'detail:' + review.id }; }
        """.trimIndent()
        val source = BookSource(
            bookSourceUrl = "https://example.org",
            bookSourceName = "review-source",
            mainJs = script
        )

        val summary = WebBook.getReviewSummaryAwait(
            source,
            Book(bookUrl = "https://example.org/book")
        )
        val detail = WebBook.getReviewDetailAwait(
            source,
            GSON.fromJson(summary.orEmpty(), JsonArray::class.java).single()
        )

        assertTrue(summary.orEmpty().contains("https://example.org/book#review"))
        assertTrue(detail.orEmpty().contains("detail:https://example.org/book#review"))
    }

    @Test
    fun v2RowsMatchAllNativeTypesForEveryBookSourceType() = runBlocking {
        val sourceTypes = listOf(
            BookSourceType.default,
            BookSourceType.audio,
            BookSourceType.image,
            BookSourceType.file,
            BookSourceType.video
        )
        val expectedRowTypes = listOf("text", "password", "label", "select", "toggle", "button")

        sourceTypes.forEach { sourceType ->
            val typeFunctions = if (sourceType == BookSourceType.file) {
                "function getBookInfo(book) { return { downloadUrls: [] }; }"
            } else {
                """
                    function getChapters(book) { return []; }
                    function getContent(chapter, book, nextChapterUrl) { return ''; }
                """.trimIndent()
            }
            val script = """
                var config = {
                  bookSourceUrl: 'https://type$sourceType.example.org',
                  bookSourceName: 'type-$sourceType',
                  bookSourceType: $sourceType
                };
                function search(key, page) { return []; }
                $typeFunctions
                function loginUi(state) {
                  return { rows: [
                    { name: '账号', type: 'text', key: 'account', hint: '邮箱', value: 'saved', style: { layout_flexGrow: 1, layout_justifySelf: 'center' } },
                    { name: '密码', type: 'password', key: 'password' },
                    { name: '提示文字', type: 'label' },
                    { name: '线路', type: 'select', key: 'line', options: ['A', 'B'], value: 'B' },
                    { name: '记住登录', type: 'toggle', key: 'remember', value: 'true' },
                    { name: '发送验证码', type: 'button', action: 'sendCode', countdown: 30 }
                  ] };
                }
                function loginAction(action, state, form) { return { state: state }; }
            """.trimIndent()
            val source = JsSourceConfig.extract(script, currentCoroutineContext())
            val rowsJson = JsSourceEngine(source, currentCoroutineContext()).callFunctionWithBindings(
                "loginUi",
                listOf("state" to "{}"),
                emptyList(),
                setOf("state")
            )
            val rows = io.legado.app.ui.login.SourceLoginV2Ui.parse(rowsJson).rows

            assertEquals(sourceType, source.bookSourceType)
            assertEquals(expectedRowTypes, rows.map { it.type })
            assertEquals("账号", rows[0].name)
            assertEquals("account", rows[0].key)
            assertEquals("邮箱", rows[0].hint)
            assertEquals("saved", rows[0].value)
            assertNull(rows[2].key)
            assertEquals("提示文字", rows[2].label)
            assertEquals(1f, rows[0].style!!.layout_flexGrow, 0f)
            assertEquals("center", rows[0].style!!.layout_justifySelf)
            assertEquals(listOf("A", "B"), rows[3].options)
            assertEquals("B", rows[3].value)
            assertEquals("sendCode", rows[5].action)
            assertEquals(30, rows[5].countdown)
        }
    }
}
