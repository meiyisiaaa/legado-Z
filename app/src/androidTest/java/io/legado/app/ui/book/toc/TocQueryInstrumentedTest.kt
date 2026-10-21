package io.legado.app.ui.book.toc

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.widget.TitleBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TocQueryInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun searchingVideoTocKeepsFullVolumeDirectoryForChapterResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bookDao = appDb.bookDao
        val chapterDao = appDb.bookChapterDao
        val uniqueId = UUID.randomUUID().toString()
        val bookUrl = "toc-query://$uniqueId"
        val targetTitle = "TocQueryTarget-$uniqueId"
        val noMatchQuery = "TocQueryNoMatch-$uniqueId"
        val book = Book(
            bookUrl = bookUrl,
            name = "TocQueryFixture-$uniqueId",
            author = "TocQueryAuthor-$uniqueId",
            type = BookType.video,
            totalChapterNum = 4,
            latestChapterTitle = targetTitle
        )
        val chapters = listOf(
            BookChapter(
                url = "$bookUrl/0",
                title = "Volume 0-$uniqueId",
                isVolume = true,
                bookUrl = bookUrl,
                index = 0
            ),
            BookChapter(
                url = "$bookUrl/1",
                title = "Chapter 1-$uniqueId",
                bookUrl = bookUrl,
                index = 1
            ),
            BookChapter(
                url = "$bookUrl/2",
                title = "Volume 1-$uniqueId",
                isVolume = true,
                bookUrl = bookUrl,
                index = 2
            ),
            BookChapter(
                url = "$bookUrl/3",
                title = targetTitle,
                bookUrl = bookUrl,
                index = 3
            )
        )
        var bookInserted = false
        var scenario: ActivityScenario<TocActivity>? = null

        try {
            runBlocking(Dispatchers.IO) {
                check(!bookDao.has(bookUrl) && !bookDao.has(book.name, book.author)) {
                    "UUID fixture book already exists"
                }
                bookDao.insert(book)
                bookInserted = true
                chapterDao.insert(*chapters.toTypedArray())
            }

            scenario = ActivityScenario.launchActivityForResult<TocActivity>(
                Intent(context, TocActivity::class.java).putExtra("bookUrl", bookUrl)
            )
            lateinit var emptyLabel: String
            lateinit var loadingLabel: String
            scenario!!.onActivity { activity ->
                emptyLabel = activity.getString(R.string.chapter_search_empty)
                loadingLabel = activity.getString(R.string.data_loading)
            }
            waitForText(targetTitle)

            scenario!!.onActivity { activity ->
                val searchView = activity.findViewById<TitleBar>(R.id.title_bar)
                    .menu.findItem(R.id.menu_search).actionView as SearchView
                searchView.setQuery(targetTitle.dropLast(4), false)
                searchView.setQuery(targetTitle, false)
                searchView.setQuery(noMatchQuery, false)
                assertEquals(noMatchQuery, searchView.query.toString())
            }
            waitForNoResults(emptyLabel, loadingLabel, targetTitle)
            composeRule.onNodeWithText(emptyLabel).assertIsDisplayed()

            scenario!!.onActivity { activity ->
                val searchView = activity.findViewById<TitleBar>(R.id.title_bar)
                    .menu.findItem(R.id.menu_search).actionView as SearchView
                searchView.setQuery(targetTitle, false)
            }
            waitForText(targetTitle)
            composeRule.onNodeWithText(targetTitle).assertIsDisplayed().performClick()

            val result = scenario!!.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val resultData = checkNotNull(result.resultData)
            assertEquals(3, resultData.getIntExtra("index", -1))
            assertEquals(1, resultData.getIntExtra("durVolumeIndex", -1))
            assertEquals(0, resultData.getIntExtra("chapterInVolumeIndex", -1))
        } finally {
            try {
                scenario?.close()
            } finally {
                if (bookInserted) {
                    runBlocking(Dispatchers.IO) {
                        chapterDao.delByBook(bookUrl)
                        bookDao.delete(book)
                    }
                }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForNoResults(emptyLabel: String, loadingLabel: String, targetTitle: String) {
        var stableSince = 0L
        composeRule.waitUntil(timeoutMillis = 20_000) {
            val isEmpty = composeRule.onAllNodesWithText(emptyLabel).fetchSemanticsNodes().isNotEmpty()
            val isLoading = composeRule.onAllNodesWithContentDescription(loadingLabel)
                .fetchSemanticsNodes().isNotEmpty()
            val targetVisible = composeRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty()
            if (isEmpty && !isLoading && !targetVisible) {
                val now = SystemClock.uptimeMillis()
                if (stableSince == 0L) stableSince = now
                now - stableSince >= 300
            } else {
                stableSince = 0L
                false
            }
        }
        assertTrue(
            "The no-match query should settle on the empty state with no target result",
            composeRule.onAllNodesWithText(emptyLabel).fetchSemanticsNodes().isNotEmpty()
                && composeRule.onAllNodesWithContentDescription(loadingLabel).fetchSemanticsNodes().isEmpty()
                && composeRule.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isEmpty()
        )
    }
}
