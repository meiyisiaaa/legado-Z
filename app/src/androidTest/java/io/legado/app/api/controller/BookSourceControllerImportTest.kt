package io.legado.app.api.controller

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BookSourceControllerImportTest {

    @Test
    fun rawJsPreviewDoesNotPersistAndSaveRoundTripsMainJs() = runBlocking {
        val sourceUrl = "https://example.org/raw-js-${UUID.randomUUID()}"
        val script = """
            var config = {
              bookSourceUrl: '$sourceUrl',
              bookSourceName: 'Raw JS fixture'
            };
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
        """.trimIndent()

        try {
            val preview = withContext(IO) {
                BookSourceController.parseBookSource(GSON.toJson(mapOf("text" to script)))
            }
            assertTrue(preview.errorMsg, preview.isSuccess)
            assertEquals(script, (preview.data as BookSource).mainJs)
            assertNull(withContext(IO) { appDb.bookSourceDao.getBookSource(sourceUrl) })

            val saved = withContext(IO) {
                BookSourceController.saveSource(script)
            }
            assertTrue(saved.errorMsg, saved.isSuccess)
            assertEquals(
                script,
                withContext(IO) { appDb.bookSourceDao.getBookSource(sourceUrl)?.mainJs }
            )
        } finally {
            withContext(IO) { appDb.bookSourceDao.delete(sourceUrl) }
        }
    }
}
