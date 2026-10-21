package io.legado.app.ui.book.cache

import android.view.View
import androidx.appcompat.view.menu.MenuBuilder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CacheScreenSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyCacheLoadingAndFailureExposeAccessibleFeedbackAndRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loading = mutableStateOf(true)
        val loadError = mutableStateOf<String?>(null)
        val books = mutableStateOf(emptyList<Book>())
        var retryCount = 0

        composeRule.setContent {
            LegadoTheme {
                CacheScreen(
                    books = books.value,
                    cacheChapters = emptyMap(),
                    revision = 0,
                    isLoading = loading.value,
                    onDownload = {},
                    onExport = {},
                    exportProgress = { null },
                    exportMessage = { null },
                    loadError = loadError.value,
                    onRetry = { retryCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.runOnIdle {
            loading.value = false
            loadError.value = "读取失败"
        }
        composeRule.onNodeWithText("读取失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.runOnIdle {
            books.value = listOf(Book(bookUrl = "ui-test://cached", name = "已缓存书籍"))
            loading.value = true
            loadError.value = null
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.runOnIdle {
            loading.value = true
            loadError.value = "读取失败"
        }
        composeRule.onNodeWithText("读取失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.onNodeWithText(context.getString(R.string.retry)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun cacheActionsRequireReadyBooksButKeepStopAvailableWhileRunning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val menu = MenuBuilder(context).apply {
            add(0, R.id.menu_download, 0, "Download")
            add(0, R.id.menu_export_all, 0, "Export all")
            add(0, R.id.menu_download_after, 0, "Download after")
            add(0, R.id.menu_download_all, 0, "Download all")
        }
        val download = menu.findItem(R.id.menu_download)
        val downloadActionView = View(context)
        download.actionView = downloadActionView
        val exportAll = menu.findItem(R.id.menu_export_all)
        val downloadAfter = menu.findItem(R.id.menu_download_after)
        val downloadAll = menu.findItem(R.id.menu_download_all)

        fun assertStartsUnavailable() {
            assertFalse(download.isEnabled)
            assertFalse(downloadActionView.isEnabled)
            assertFalse(exportAll.isEnabled)
            assertFalse(downloadAfter.isEnabled)
            assertFalse(downloadAll.isEnabled)
        }

        updateCacheActionMenuEnabled(
            menu = menu,
            booksLoaded = false,
            hasLoadError = false,
            hasBooks = true,
            isRunning = false
        )
        assertStartsUnavailable()
        assertTrue(downloadAfter.isVisible)
        assertTrue(downloadAll.isVisible)
        assertEquals(context.getString(R.string.menu_download_after), downloadAfter.title)
        assertEquals(context.getString(R.string.menu_download_all), downloadAll.title)
        updateCacheActionMenuEnabled(
            menu = menu,
            booksLoaded = true,
            hasLoadError = true,
            hasBooks = true,
            isRunning = false
        )
        assertStartsUnavailable()
        updateCacheActionMenuEnabled(
            menu = menu,
            booksLoaded = true,
            hasLoadError = false,
            hasBooks = false,
            isRunning = false
        )
        assertStartsUnavailable()

        updateCacheActionMenuEnabled(
            menu = menu,
            booksLoaded = false,
            hasLoadError = true,
            hasBooks = false,
            isRunning = true
        )
        assertTrue(download.isEnabled)
        assertTrue(downloadActionView.isEnabled)
        assertFalse(exportAll.isEnabled)
        assertTrue(downloadAfter.isVisible)
        assertEquals(context.getString(R.string.stop), downloadAfter.title)
        assertTrue(downloadAfter.isEnabled)
        assertFalse(downloadAll.isVisible)
        assertFalse(downloadAll.isEnabled)

        updateCacheActionMenuEnabled(
            menu = menu,
            booksLoaded = true,
            hasLoadError = false,
            hasBooks = true,
            isRunning = false
        )
        assertTrue(download.isEnabled)
        assertTrue(downloadActionView.isEnabled)
        assertTrue(exportAll.isEnabled)
        assertTrue(downloadAfter.isVisible)
        assertEquals(context.getString(R.string.menu_download_after), downloadAfter.title)
        assertTrue(downloadAfter.isEnabled)
        assertTrue(downloadAll.isVisible)
        assertEquals(context.getString(R.string.menu_download_all), downloadAll.title)
        assertTrue(downloadAll.isEnabled)
    }
}
