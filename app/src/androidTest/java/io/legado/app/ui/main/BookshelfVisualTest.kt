package io.legado.app.ui.main

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.main.bookshelf.BookshelfContent
import io.legado.app.ui.main.bookshelf.BookshelfGroups
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Fixed visual fixtures; no database writes, network sources or preference changes. */
class BookshelfVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.let { target ->
        target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        })
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val books = listOf(
        Book(bookUrl = "visual://north", name = "北岸来信", author = "林青遥",
            durChapterTitle = "第一章 没有邮戳的信", latestChapterTitle = "第十二章 灯塔下的回信",
            totalChapterNum = 12, durChapterIndex = 2, lastCheckCount = 2),
        Book(bookUrl = "visual://mountain", name = "远山回声", author = "顾南枝",
            durChapterTitle = "第二章 山屋里的回信", latestChapterTitle = "第二章 山屋里的回信"),
        Book(bookUrl = "visual://long", name = "在漫长的旅途中寻找一座没有名字的图书馆与那些未曾寄出的信",
            author = "一位名字很长的作者与另一位合著者",
            durChapterTitle = "第一百二十八章 穿过漫长雨季以后仍未抵达的车站",
            latestChapterTitle = "第二百三十六章 在旅程终点重读一封迟到的来信",
            totalChapterNum = 236, durChapterIndex = 127, lastCheckCount = 3),
        Book(bookUrl = "visual://paper", name = "纸舟与星河", author = "周寻山",
            durChapterTitle = "第一章 折给夜空的船", latestChapterTitle = "第三章 山坡上的望远镜"),
        Book(bookUrl = "visual://fog", name = "雾港拾光", author = "沈栖灯",
            durChapterTitle = "第一章 旧钟不再报时", latestChapterTitle = "第三章 天亮之前的船")
    )

    private fun render(layout: Int = 0, entries: List<Any> = books, loading: Boolean = false,
                       error: String? = null, onOpen: (Any) -> Unit = {},
                       onDetails: (Any) -> Unit = {}, onRetry: () -> Unit = {}) {
        val localizedContext = context
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localizedContext, LocalDensity provides Density(density.density,
                arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                LegadoTheme {
                    Column(Modifier.fillMaxSize()) {
                        LegadoTopAppBar(title = {
                            var selected by remember { mutableIntStateOf(0) }
                            BookshelfGroups(listOf(BookGroup(51, "全部"), BookGroup(52, "文学")), selected,
                                { selected = it }, {})
                        }, actions = {
                            IconButton(onClick = {}) {
                                Icon(painterResource(R.drawable.ic_search), "搜索")
                            }
                            IconButton(onClick = {}) {
                                Icon(painterResource(R.drawable.ic_more_vert), "更多")
                            }
                        })
                        Box(Modifier.weight(1f)) {
                            BookshelfContent(entries, BookGroup.IdAll, layout, 0, false, { false }, {},
                                onOpen, onDetails, isLoading = loading, error = error, onRetry = onRetry)
                        }
                        MainNavigation(listOf(0, 1, 2, 3), 0, 0, {})
                    }
                }
            }
        }
    }

    private fun record(state: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // Capture the complete rendered window after transient system overlays have settled.
        instrumentation.uiAutomation.waitForIdle(3_500, 10_000)
        val directory = File(context.getExternalFilesDir(null), "bookshelf-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun normalShelf() {
        var opened: Any? = null
        var details: Any? = null
        render(onOpen = { opened = it }, onDetails = { details = it })
        record("normal")
        compose.onAllNodesWithText(context.getString(R.string.bookshelf_reading_chapter), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val result = mutableListOf<TextLayoutResult>()
                action(result)
                assertFalse(result.single().hasVisualOverflow)
            }
        compose.onNodeWithText(books[2].name).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(books[2], opened)
        compose.onNodeWithText(books[2].name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val result = mutableListOf<TextLayoutResult>()
                action(result)
                assertEquals(2, result.single().lineCount)
            }
        compose.onNodeWithText(books[0].name).performScrollTo().performTouchInput { longClick() }
        assertEquals(books[0], details)
        compose.onNodeWithText("文学").performClick().assertIsSelected()
    }

    @Test fun compactShelf() {
        render(layout = 1)
        compose.onNodeWithText(books[0].name).assertIsDisplayed()
        compose.onNodeWithText(books[2].name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val result = mutableListOf<TextLayoutResult>()
                action(result)
                assertEquals(1, result.single().lineCount)
            }
        record("compact")
    }

    @Test fun gridShelf() {
        render(layout = 3)
        compose.onNodeWithText(books[0].author).assertDoesNotExist()
        record("grid")
    }

    @Test fun denseGridShelf() {
        render(layout = 6)
        compose.onNodeWithText(books[0].name).assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        record("grid6")
        compose.onNodeWithText("108", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val result = mutableListOf<TextLayoutResult>()
                action(result)
                assertEquals(1, result.single().lineCount)
                val layout = result.single()
                assertFalse(layout.didOverflowHeight)
                assertFalse(layout.isLineEllipsized(0))
                // Centered text can reuse a wider paragraph; compare the glyph span with its measured box.
                assertTrue(layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f)
            }
    }

    @Test fun emptyShelf() {
        render(entries = emptyList())
        compose.onNodeWithText(context.getString(R.string.bookshelf_empty)).assertIsDisplayed()
        record("empty")
    }

    @Test fun loadingShelf() {
        render(entries = emptyList(), loading = true)
        compose.onNodeWithText(context.getString(R.string.bookshelf_loading)).assertIsDisplayed()
        record("loading")
    }

    @Test fun errorShelf() {
        var retries = 0
        render(entries = emptyList(), error = "无法读取本地书架。".repeat(36), onRetry = { retries++ })
        record("error-top")
        val retry = compose.onNodeWithText(context.getString(R.string.retry))
        retry.performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.my)).assertIsDisplayed()
        record("error")
        retry.performClick()
        assertEquals(1, retries)
    }
}
