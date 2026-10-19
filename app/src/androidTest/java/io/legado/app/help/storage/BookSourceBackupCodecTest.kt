package io.legado.app.help.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.BookSourceImport
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookSourceBackupCodecTest {

    @Test
    fun backupJsonRoundTripRetainsAndCanonicalizesSingleFileSource() = runBlocking {
        val script = """
            var config = {
              bookSourceUrl: 'https://backup.example.org',
              bookSourceName: 'backup-js',
              exploreUrl: [{ title: 'Latest', url: '/latest' }]
            };
            function search(key, page) { return []; }
            function explore(url, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book, nextChapterUrl) { return ''; }
        """.trimIndent()
        val source = BookSourceImport.parse(script, currentCoroutineContext()).copy(
            bookSourceGroup = "local group",
            enabled = false,
            customOrder = 17
        )

        val backupJson = BookSourceBackupCodec.encode(listOf(source))
        val restored = BookSourceBackupCodec.decode(backupJson, currentCoroutineContext())!!.single()

        assertEquals(script, restored.mainJs)
        assertEquals("backup-js", restored.bookSourceName)
        assertTrue(restored.exploreUrl.orEmpty().contains("Latest"))
        assertEquals("local group", restored.bookSourceGroup)
        assertEquals(false, restored.enabled)
        assertEquals(17, restored.customOrder)
    }
}
