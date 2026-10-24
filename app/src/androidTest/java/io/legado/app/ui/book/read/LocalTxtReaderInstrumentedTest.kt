package io.legado.app.ui.book.read

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.book.removeLocalUriCache
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.ui.book.read.page.ReadView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalTxtReaderInstrumentedTest {

    @Test
    fun privateTxtImportOpensRendersTocAndPersistsReadingProgress() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var hasExistingReaderState = false
        var previousInBookshelf = false
        var previousIsComic = false
        instrumentation.runOnMainSync {
            hasExistingReaderState = ReadBook.callBack != null || ReadBook.book != null
            previousInBookshelf = ReadBook.inBookshelf
            previousIsComic = ReadBookConfig.isComic
        }
        assumeTrue("Skip to avoid replacing an existing reader owner/state", !hasExistingReaderState)

        val context = instrumentation.targetContext
        val fixtureDir = File(context.cacheDir, "reader-smoke-${UUID.randomUUID()}")
        val fixtureFile = File(fixtureDir, "local-reader-${UUID.randomUUID()}.txt")
        val firstMarker = "LOCAL_READER_CHAPTER_ONE_MARKER"
        val secondMarker = "LOCAL_READER_CHAPTER_TWO_MARKER"
        var importedBook: Book? = null
        var scenario: ActivityScenario<ReadBookActivity>? = null

        try {
            assertTrue("Could not create private fixture directory", fixtureDir.mkdirs())
            fixtureFile.writeText(
                buildString {
                    appendLine("第一章 本地阅读验收")
                    appendLine(firstMarker)
                    appendLine("第一章正文内容。".repeat(120))
                    appendLine("第二章 进度保存验收")
                    appendLine(secondMarker)
                    appendLine("第二章正文内容。".repeat(120))
                },
                Charsets.UTF_8
            )

            importedBook = LocalBook.importFile(Uri.fromFile(fixtureFile))
            importedBook!!.apply {
                // Keep the two-heading fixture deterministic across devices and installed TOC rules.
                // Require the heading separator so body lines beginning with "第一章正文" are not TOC entries.
                tocUrl = "^第[一二]章[ \\t]+.*\$${TextFile.spaceChars}"
                save()
            }

            val launchIntent = Intent(context, ReadBookActivity::class.java)
                .putExtra("bookUrl", importedBook!!.bookUrl)
                .putExtra("inBookshelf", true)
            scenario = ActivityScenario.launch<ReadBookActivity>(launchIntent)

            await("first chapter layout") {
                var ready = false
                scenario!!.onActivity { activity ->
                    val chapter = ReadBook.curTextChapter
                    val page = activity.findViewById<ReadView>(R.id.read_view).curPage.textPage
                    ready = ReadBook.book?.bookUrl == importedBook!!.bookUrl &&
                            ReadBook.chapterSize == 2 &&
                            chapter?.chapter?.index == 0 &&
                            chapter?.isCompleted == true &&
                            page.isCompleted && page.text.contains(firstMarker)
                }
                ready
            }

            val chapters = appDb.bookChapterDao.getChapterList(importedBook!!.bookUrl)
            assertEquals("The imported TXT should produce two readable chapters", 2, chapters.size)
            assertEquals("第一章 本地阅读验收", chapters[0].title)
            assertEquals("第二章 进度保存验收", chapters[1].title)
            val secondContent = LocalBook.getContent(importedBook!!, chapters[1])
            assertNotNull(secondContent)
            assertTrue(secondContent!!.contains(secondMarker))

            // Use the reader's actual chapter navigation path, then close through Activity lifecycle.
            scenario!!.onActivity {
                ReadBook.openChapter(1)
            }
            await("second chapter layout") {
                var ready = false
                scenario!!.onActivity { activity ->
                    val chapter = ReadBook.curTextChapter
                    val page = activity.findViewById<ReadView>(R.id.read_view).curPage.textPage
                    ready = ReadBook.book?.bookUrl == importedBook!!.bookUrl &&
                            ReadBook.durChapterIndex == 1 &&
                            chapter?.chapter?.index == 1 &&
                            chapter?.isCompleted == true &&
                            page.isCompleted && page.text.contains(secondMarker)
                }
                ready
            }
            var readerHasOwner = false
            instrumentation.runOnMainSync {
                readerHasOwner = ReadBook.callBack != null
            }
            assertTrue("The reader activity should own the reader callback", readerHasOwner)

            scenario!!.close()
            scenario = null
            await("reader owner release") {
                var noOwner = false
                instrumentation.runOnMainSync {
                    noOwner = ReadBook.callBack == null
                }
                noOwner
            }
            await("persisted chapter progress") {
                appDb.bookDao.getBook(importedBook!!.bookUrl)?.durChapterIndex == 1
            }
            assertEquals(1, appDb.bookDao.getBook(importedBook!!.bookUrl)?.durChapterIndex)
        } finally {
            scenario?.close()
            importedBook?.let { ownedBook ->
                appDb.bookChapterDao.delByBook(ownedBook.bookUrl)
                appDb.readRecordDao.deleteByName(ownedBook.name)
                appDb.bookDao.getBook(ownedBook.bookUrl)?.let { persistedBook ->
                    LocalBook.deleteBook(persistedBook, deleteOriginal = true)
                    persistedBook.delete()
                }
                ownedBook.removeLocalUriCache()
                fixtureFile.delete()
                TextFile.clear()
            } ?: fixtureFile.delete()
            fixtureDir.delete()
            instrumentation.runOnMainSync {
                if (ReadBook.callBack == null) {
                    if (ReadBook.book?.bookUrl == importedBook?.bookUrl) {
                        ReadBook.book = null
                    }
                    ReadBook.clearTextChapter()
                    ReadBook.chapterSize = 0
                    ReadBook.simulatedChapterSize = 0
                    ReadBook.durChapterIndex = 0
                    ReadBook.durChapterPos = 0
                    ReadBook.bookSource = null
                    ReadBook.contentProcessor = null
                    ReadBook.inBookshelf = previousInBookshelf
                    ReadBookConfig.isComic = previousIsComic
                }
            }
        }
    }

    private fun await(label: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $label")
    }
}
