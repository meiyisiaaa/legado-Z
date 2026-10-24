package io.legado.app.ui.book.toc

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.widget.compose.LegadoMotion
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Fixed chapter data and callbacks; no reader, repository, Fragment or network. */
class ChapterListVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.let { target ->
        target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        })
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val chapters = List(30) { index ->
        BookChapter(bookUrl = "visual://toc", url = "visual://chapter/$index", index = index,
            title = when (index) {
                0 -> "第一卷 · 远方来信"
                1 -> "第二章 穿过漫长雨季以后仍未抵达的车站和一封没有邮戳的来信"
                else -> "第${index + 1}章 灯塔下的回信"
            }, isVolume = index == 0, isVip = index == 2 || index == 3, isPay = index == 3,
            tag = if (index == 1) "昨日 20:10" else null,
            wordCount = if (index == 0) "卷字数不应展示" else "3200字")
    }
    private val fixtureState get() = ChapterListUiState(chapters = chapters,
        currentChapterIndex = 1, currentChapterInfo = "${chapters[1].title}（2 / 30）",
        countWords = true, cachedFileNames = setOf(chapters[3].getFileName()))

    @Composable
    private fun Fixture(state: ChapterListUiState, width: Dp = 10_000.dp, fontScale: Float? = null,
                        motion: LegadoMotion? = null, onOpen: (BookChapter) -> Unit = {},
                        onLongClick: (String) -> Unit = {}, onRetry: () -> Unit = {}) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalContext provides context,
            LocalDensity provides Density(density.density, fontScale
                ?: arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
            LegadoTheme {
                CompositionLocalProvider(LocalLegadoMotion provides (motion ?: LocalLegadoMotion.current)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = width).fillMaxSize()) {
                            LegadoTopAppBar(title = { Text(context.getString(R.string.chapter_list)) }, actions = {
                                IconButton(onClick = {}) {
                                    Icon(painterResource(R.drawable.ic_search), context.getString(R.string.search))
                                }
                                IconButton(onClick = {}) {
                                    Icon(painterResource(R.drawable.ic_more_vert), context.getString(R.string.more_menu))
                                }
                            })
                            ChapterListScreen(state, onOpen, onLongClick, onRetry,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    private fun render(state: ChapterListUiState = fixtureState, width: Dp = 10_000.dp,
                       fontScale: Float? = null, motion: LegadoMotion? = null,
                       onOpen: (BookChapter) -> Unit = {}, onLongClick: (String) -> Unit = {},
                       onRetry: () -> Unit = {}) {
        compose.setContent { Fixture(state, width, fontScale, motion, onOpen, onLongClick, onRetry) }
    }

    private fun record(state: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(3_500, 10_000)
        val directory = File(context.getExternalFilesDir(null), "toc-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun normalChaptersPreserveActionsAndStatuses() {
        var opened: BookChapter? = null
        var fullTitle: String? = null
        render(onOpen = { opened = it }, onLongClick = { fullTitle = it })
        fun scrollToChapter(index: Int): SemanticsNodeInteraction {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
                .performScrollToNode(hasText(chapters[index].title))
            return compose.onNodeWithText(chapters[index].title)
        }
        val current = compose.onNode(hasText(chapters[1].title)
            and hasContentDescription(context.getString(R.string.current_chapter))
            and hasContentDescription(context.getString(R.string.toc_not_cached)))
        current.assertIsSelected().assertHeightIsAtLeast(48.dp)
        current.assert(hasText(chapters[1].tag!!) and hasText(chapters[1].wordCount!!))
        compose.onNodeWithText(chapters[1].tag!!, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText(chapters[1].wordCount!!) and hasAnyAncestor(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(chapters[1].wordCount!!)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                val layout = layouts.single()
                assertEquals(1, layout.lineCount)
                assertFalse(layout.isLineEllipsized(0))
                assertTrue(layout.getLineBottom(0) <= layout.size.height + 1f)
            }
        compose.onNodeWithText(chapters[0].wordCount!!).assertDoesNotExist()
        compose.onNode(hasText(chapters[2].title)
            and hasContentDescription(context.getString(R.string.toc_vip_chapter))).assertIsDisplayed()
        scrollToChapter(3).assertIsDisplayed()
        compose.onNode(hasText(chapters[3].title)
            and hasContentDescription(context.getString(R.string.toc_not_cached))).assertDoesNotExist()
        compose.onNode(hasText(chapters[3].title)
            and hasContentDescription(context.getString(R.string.toc_vip_chapter))).assertDoesNotExist()
        scrollToChapter(2).performClick()
        assertEquals(chapters[2], opened)
        scrollToChapter(1).performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertEquals(chapters[1].title, fullTitle)
        record("normal")
    }

    @Test fun firstLastAndCurrentPositionRemainReachable() {
        render()
        compose.onNodeWithContentDescription(context.getString(R.string.toc_last_chapter)).performClick()
        compose.onNodeWithText(chapters.last().title).assertIsDisplayed()
        record("last")
        compose.onNodeWithContentDescription(context.getString(R.string.toc_first_chapter)).performClick()
        compose.onNodeWithText(chapters.first().title).assertIsDisplayed()
        compose.onNodeWithText(fixtureState.currentChapterInfo).performClick()
        compose.onNodeWithText(chapters[1].title).assertIsSelected().assertIsDisplayed()
    }

    @Test fun narrowLargeTypeKeepsTwoLineTitlesAndPositionControls() {
        render(width = 320.dp, fontScale = 1.5f)
        compose.onNodeWithText(chapters[1].title).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText(chapters[1].title, useUnmergedTree = true)
            .assertTextEquals(chapters[1].title)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                val layout = layouts.single()
                assertEquals(2, layout.lineCount)
                // didOverflowHeight includes intentional maxLines truncation; check the rendered line box.
                assertTrue(layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
                assertTrue(layout.isLineEllipsized(layout.lineCount - 1))
            }
        compose.onNodeWithText(fixtureState.currentChapterInfo).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.toc_first_chapter))
            .assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription(context.getString(R.string.toc_last_chapter))
            .assertIsDisplayed().assertWidthIsAtLeast(48.dp)
        record("narrow-large-type")
    }

    @Test fun localBookOmitsRemoteCacheStatus() {
        render(fixtureState.copy(isLocalBook = true))
        compose.onNodeWithText(chapters[1].title).assertIsDisplayed().assertIsSelected()
        compose.onAllNodesWithContentDescription(context.getString(R.string.toc_not_cached))
            .assertCountEquals(0)
        record("local")
    }

    @Test fun emptyAndSearchEmptyRemainDistinct() {
        var searchKey by mutableStateOf<String?>(null)
        compose.setContent { Fixture(ChapterListUiState(searchKey = searchKey)) }
        compose.onNodeWithText(context.getString(R.string.chapter_list_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.toc_first_chapter)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.toc_last_chapter)).assertIsNotEnabled()
        record("empty")
        compose.runOnIdle { searchKey = "未找到的章节" }
        compose.onNodeWithText(context.getString(R.string.chapter_search_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chapter_list_empty)).assertDoesNotExist()
        record("search-empty")
    }

    @Test fun loadingWithReducedMotionKeepsVisibleStatus() {
        render(ChapterListUiState(isLoading = true), motion = LegadoMotion(false))
        compose.onNodeWithContentDescription(context.getString(R.string.data_loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chapter_list_empty)).assertDoesNotExist()
        record("loading-reduced-motion")
    }

    @Test fun longErrorCanScrollToRetry() {
        var retries = 0
        render(ChapterListUiState(loadError = "目录暂时无法读取，请稍后重试。".repeat(48)),
            width = 320.dp, fontScale = 1.5f, onRetry = { retries++ })
        compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.toc_first_chapter)).assertIsDisplayed()
        record("error")
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertEquals(1, retries)
    }

    @Test fun renamedChapterWithSameIdentityRefreshesFullTitle() {
        var title by mutableStateOf("旧章节名")
        var longTitle: String? = null
        compose.setContent {
            Fixture(ChapterListUiState(chapters = listOf(chapters[1].copy(title = title)),
                currentChapterIndex = 1), onLongClick = { longTitle = it })
        }
        compose.onNodeWithText("旧章节名").assertIsDisplayed()
        compose.runOnIdle { title = "同一章节更新后的完整章名" }
        compose.onNodeWithText(title).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("旧章节名").assertDoesNotExist()
        assertEquals(title, longTitle)
    }
}
