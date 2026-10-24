package io.legado.app.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.main.bookshelf.BookshelfContent
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Read-only fixtures: no repository, network, preferences or saved book writes. */
class ReadingFilterContentTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.let { target ->
        target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        })
    }
    private val books = listOf(
        Book(bookUrl = "filter://new", name = "未展开的来信", author = "林青遥", totalChapterNum = 12),
        Book(bookUrl = "filter://reading", name = "远山回声", author = "顾南枝",
            totalChapterNum = 12, durChapterIndex = 2),
        Book(bookUrl = "filter://last", name = "灯塔下的回信", author = "周寻山",
            totalChapterNum = 12, durChapterIndex = 11)
    )

    private fun render(entries: () -> List<Any> = { books }, group: () -> Long = { BookGroup.IdAll },
                       layout: Int = 0, loading: Boolean = false, error: String? = null,
                       onOpen: (Any) -> Unit = {}, onDetails: (Any) -> Unit = {},
                       onRefresh: () -> Unit = {}) {
        val localized = context
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalInspectionMode provides true) {
                LegadoTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            BookshelfContent(entries(), group(), layout, 0, true, { false },
                                onRefresh, onOpen, onDetails, isLoading = loading, error = error)
                        }
                    }
                }
            }
        }
    }

    private fun choose(label: Int) = compose.onNodeWithText(context.getString(label))
        .performScrollTo().performClick().assertIsSelected()

    private fun record(state: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val args = InstrumentationRegistry.getArguments()
        val directory = File(context.getExternalFilesDir(null), "reading-filter-visual").apply { mkdirs() }
        val file = File(directory, "${args.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun filterSelectionShowsOnlyMatchingBook() {
        render()
        choose(R.string.reading_filter_reading)
        compose.onNodeWithText(books[1].name).assertIsDisplayed()
        compose.onNodeWithText(books[0].name).assertDoesNotExist()
        compose.onNodeWithText(books[2].name).assertDoesNotExist()
        record("reading")
        choose(R.string.reading_filter_last_chapter)
        compose.onNodeWithText(books[2].name).assertIsDisplayed()
        compose.onNodeWithText(books[1].name).assertDoesNotExist()
    }

    @Test fun noMatchesKeepsFiltersAndOffersClear() {
        render(entries = { listOf(books[0]) })
        choose(R.string.reading_filter_last_chapter)
        compose.onNodeWithText(context.getString(R.string.reading_filter_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bookshelf_empty_title)).assertDoesNotExist()
        record("empty-filter")
        compose.onNodeWithText(context.getString(R.string.reading_filter_clear)).performClick()
        compose.onNodeWithText(context.getString(R.string.reading_filter_all)).assertIsSelected()
        compose.onNodeWithText(books[0].name).assertIsDisplayed()
    }

    @Test fun groupSelectionIsIndependentAndRestoredOnReturn() {
        val group = mutableStateOf(BookGroup.IdAll)
        render(group = { group.value })
        choose(R.string.reading_filter_reading)
        compose.runOnIdle { group.value = 8L }
        compose.onNodeWithText(context.getString(R.string.reading_filter_all)).assertIsSelected()
        choose(R.string.reading_filter_not_started)
        compose.runOnIdle { group.value = BookGroup.IdAll }
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertIsSelected()
        compose.onNodeWithText(books[1].name).assertIsDisplayed()
    }

    @Test fun selectionSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val localized = context
        restoration.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalInspectionMode provides true) {
                LegadoTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                            BookshelfContent(books, BookGroup.IdAll, 0, 0, false, { false }, {}, {}, {})
                        }
                    }
                }
            }
        }
        choose(R.string.reading_filter_reading)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertIsSelected()
        compose.onNodeWithText(books[1].name).assertIsDisplayed()
        compose.onNodeWithText(books[0].name).assertDoesNotExist()
    }

    @Test fun rootAndFolderEntriesRemainUnfiltered() {
        val entries = mutableStateOf<List<Any>>(books)
        val group = mutableStateOf(BookGroup.IdRoot)
        render(entries = { entries.value }, group = { group.value })
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertDoesNotExist()
        compose.onNodeWithText(books[0].name).assertIsDisplayed()
        compose.runOnIdle {
            group.value = BookGroup.IdAll
            entries.value = listOf(BookGroup(8, "远行书目"), books[0])
        }
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertDoesNotExist()
        compose.onNodeWithText("远行书目").assertIsDisplayed()
    }

    @Test fun filteredGridRetainsOpenAndDetailsCallbacks() {
        var opened: Any? = null
        var detailed: Any? = null
        render(layout = 3, onOpen = { opened = it }, onDetails = { detailed = it })
        choose(R.string.reading_filter_last_chapter)
        compose.onNodeWithText(books[2].name).performClick()
        compose.onNodeWithText(books[2].name).performTouchInput { longClick() }
        assertEquals(books[2], opened)
        assertEquals(books[2], detailed)
    }

    @Test fun compactFilterKeepsOriginalBooksAndRefreshCallback() {
        var refreshes = 0
        var opened: Any? = null
        render(layout = 1, onOpen = { opened = it }, onRefresh = { refreshes++ })
        choose(R.string.reading_filter_reading)
        compose.onNodeWithText(books[1].name).performClick()
        assertEquals(books[1], opened)
        val bookBounds = compose.onNodeWithText(books[1].name).fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // Start in the vertical list; the filter strip only dispatches horizontal scroll.
        compose.onRoot().performTouchInput {
            swipeDown(startY = bookBounds.center.y - rootBounds.top, endY = bottom - 1f)
        }
        compose.runOnIdle { assertTrue(refreshes > 0) }
        assertEquals(3, books.size)
        assertEquals(0, books[0].durChapterIndex)
    }

    @Test fun trueEmptyDoesNotBecomeFilterEmpty() {
        render(entries = { emptyList() })
        compose.onNodeWithText(context.getString(R.string.bookshelf_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reading_filter_empty)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertDoesNotExist()
    }

    @Test fun loadingKeepsOriginalLoadingState() {
        render(loading = true)
        compose.onNodeWithText(context.getString(R.string.bookshelf_loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertDoesNotExist()
    }

    @Test fun errorKeepsOriginalRetryState() {
        var retries = 0
        render(error = "Local shelf unavailable", onRefresh = { retries++ })
        compose.onNodeWithText("Local shelf unavailable").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertEquals(1, retries)
        compose.onNodeWithText(context.getString(R.string.reading_filter_reading)).assertDoesNotExist()
    }
}
