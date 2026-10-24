package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.exception.NoStackTraceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsSourceConfigSecurityTest {

    @Test
    fun safeMetadataScopeDoesNotExposeJavaGlobalsEvenThroughFunctionConstructor() = runBlocking {
        val source = JsSourceConfig.extract(
            """
            var config = {
              bookSourceUrl: 'https://example.org',
              bookSourceName: [
                typeof Packages,
                typeof java,
                typeof JavaAdapter,
                typeof JavaImporter,
                Object.constructor.constructor('return typeof Packages')(),
                Object.constructor.constructor('return typeof java')()
              ].join('|')
            };
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
            """.trimIndent(),
            currentCoroutineContext()
        )

        assertEquals("undefined|undefined|undefined|undefined|undefined|undefined", source.bookSourceName)
    }

    @Test
    fun constructorEscapeCannotResolveJavaPackages() = runBlocking {
        val script = """
            var config = { bookSourceUrl: 'https://example.org', bookSourceName: 'escape' };
            Object.constructor.constructor('return Packages.java.lang.System')();
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
        """.trimIndent()

        val error = runCatching {
            JsSourceConfig.extract(script, currentCoroutineContext())
        }.exceptionOrNull()
        assertTrue(error is NoStackTraceException)
    }

    @Test(timeout = 5_000)
    fun cancellingInfiniteMetadataScriptInterruptsRhinoInstructionLoop() = runBlocking {
        val script = """
            while (true) { }
            var config = { bookSourceUrl: 'https://example.org', bookSourceName: 'loop' };
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
        """.trimIndent()
        val work = async(Dispatchers.Default) {
            JsSourceConfig.extract(script, currentCoroutineContext())
        }
        delay(100)
        val completed = withTimeout(2_000) {
            work.cancelAndJoin()
            true
        }
        assertTrue(completed)
        assertFalse(work.isActive)
    }

    @Test(timeout = 20_000)
    fun parserTimeoutIsExplicitAndLeavesCallerAvailableForNextParse() = runBlocking {
        val callerJob: Job = currentCoroutineContext()[Job]
            ?: throw AssertionError("test must run in a cancellable caller job")
        val script = infiniteMetadataScript()

        val timeoutError = try {
            JsSourceConfig.extract(script, currentCoroutineContext())
            null
        } catch (error: NoStackTraceException) {
            error
        }

        assertNotNull(timeoutError)
        assertEquals("JS源配置解析超时（10秒），已终止执行", timeoutError?.message)
        assertTrue("parser-owned timeout must not cancel its caller", callerJob.isActive)

        val parsed = JsSourceConfig.extract(validMetadataScript(), currentCoroutineContext())
        assertEquals("after-timeout", parsed.bookSourceName)
        assertTrue("caller job must remain active after the follow-up parse", callerJob.isActive)
    }

    @Test(timeout = 8_000)
    fun shorterCallerTimeoutRemainsCancellationException() = runBlocking {
        val callerJob: Job = currentCoroutineContext()[Job]
            ?: throw AssertionError("test must run in a cancellable caller job")
        val error = runCatching {
            withTimeout(1_000) {
                JsSourceConfig.extract(infiniteMetadataScript(), currentCoroutineContext())
            }
        }.exceptionOrNull()

        assertTrue("external timeout should propagate as cancellation", error is CancellationException)
        assertFalse("external timeout must not become a parser error", error is NoStackTraceException)
        assertTrue("a caught child timeout must leave the caller job active", callerJob.isActive)
    }

    private fun infiniteMetadataScript() = """
        while (true) { }
        var config = { bookSourceUrl: 'https://example.org', bookSourceName: 'loop' };
        function search(key, page) { return []; }
        function getChapters(book) { return []; }
        function getContent(chapter, book, nextChapterUrl) { return ''; }
    """.trimIndent()

    private fun validMetadataScript() = """
        var config = { bookSourceUrl: 'https://example.org', bookSourceName: 'after-timeout' };
        function search(key, page) { return []; }
        function getChapters(book) { return []; }
        function getContent(chapter, book, nextChapterUrl) { return ''; }
    """.trimIndent()
}
