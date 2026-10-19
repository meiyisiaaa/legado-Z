package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.exception.NoStackTraceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
