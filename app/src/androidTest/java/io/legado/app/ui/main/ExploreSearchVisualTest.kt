package io.legado.app.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.search.SearchBookUi
import io.legado.app.ui.book.search.SearchScreen
import io.legado.app.ui.book.search.SearchToolbar
import io.legado.app.ui.book.search.SearchUiState
import io.legado.app.ui.main.explore.ExploreContent
import io.legado.app.ui.main.explore.ExploreSourceAction
import io.legado.app.ui.main.explore.ExploreUiState
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Fixed UI states; no source providers, database writes, search jobs or preference changes. */
class ExploreSearchVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext.let { target ->
        target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        })
    }
    private val sources = listOf(
        BookSourcePart("visual://library", "在漫长的旅途中寻找一座没有名字的图书馆与那些未曾寄出的信",
            "文学 · 长篇与随笔", hasLoginUrl = true),
        BookSourcePart("visual://stars", "星河书房", "科幻 · 冒险"),
        BookSourcePart("visual://mountain", "山野阅读室", "纪实 · 自然")
    )
    private val kinds = listOf(
        ExploreKind(title = "新书", url = "visual://new"),
        ExploreKind(title = "编辑推荐", url = "visual://editors"),
        ExploreKind(title = "旅途中的长篇故事", url = "visual://journeys")
    )
    private val books = listOf(
        SearchBookUi("visual://north", "在漫长的旅途中寻找一座没有名字的图书馆与那些未曾寄出的信",
            "林青遥", null, null, 12, "第一百二十八章 雨季结束以后的车站",
            "一封没有邮戳的来信，让远行的人重新找到属于自己的阅读时光。",
            listOf("文学", "连载"), true),
        SearchBookUi("visual://fog", "雾港拾光", "沈栖灯", null, null, 3,
            "第三章 天亮之前的船", "从旧书店开始，重读一个港口的故事。", listOf("悬疑"), false)
    )

    @Composable
    private fun Fixture(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalContext provides context,
            LocalDensity provides Density(density.density,
                arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
            LegadoTheme { content() }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    private fun renderExplore(initial: ExploreUiState, onToggle: (BookSourcePart) -> Unit = {},
                              onAction: (BookSourcePart, ExploreSourceAction) -> Unit = { _, _ -> },
                              onKind: (String, String, String?) -> Unit = { _, _, _ -> },
                              onRetry: () -> Unit = {}) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            Fixture {
                Column(Modifier.fillMaxSize()) {
                    LegadoTopAppBar(title = { Text(context.getString(R.string.discovery)) }, actions = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(R.drawable.ic_search), context.getString(R.string.search))
                        }
                    })
                    ExploreContent(state, onToggleSource = { source ->
                        onToggle(source)
                        state = state.copy(expandedSourceUrl = source.bookSourceUrl.takeUnless {
                            it == state.expandedSourceUrl
                        })
                    }, onSourceAction = onAction, onOpenExplore = onKind,
                        onRetrySources = onRetry, onRetryGroups = {},
                        // The production Android kind renderer needs a saved BookSource; exercise its UI slot.
                        kindContent = { source, entries, _, _ ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                entries.forEach { kind ->
                                    TextButton(onClick = {
                                        onKind(source.bookSourceUrl, kind.title, kind.url)
                                    }) { Text(kind.title) }
                                }
                            }
                        }, modifier = Modifier.weight(1f))
                    MainNavigation(listOf(0, 1, 2, 3), 1, 0, {})
                }
            }
        }
    }

    private fun renderSearch(state: SearchUiState, onBook: (SearchBookUi) -> Unit = {},
                             onHistory: (String) -> Unit = {}, onDelete: (String) -> Unit = {},
                             onStop: () -> Unit = {}, onContinue: () -> Unit = {}) {
        compose.setContent {
            Fixture {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    Surface(color = if (AppConfig.isEInkMode) Color.White
                        else Color(ThemeStore.primaryColor(context))) {
                        SearchToolbar(state, onQueryChange = {}, onQueryFocusChange = {}, onSubmit = {},
                            modifier = Modifier.statusBarsPadding())
                    }
                    SearchScreen(state, onHistoryClick = onHistory, onHistoryDelete = onDelete,
                        onClearHistory = {}, onLocalBookClick = {}, onBookClick = { book, _ -> onBook(book) },
                        onStopSearch = onStop, onContinueSearch = onContinue, onListEndReached = {},
                        modifier = Modifier.weight(1f))
                }
            }
        }
    }

    private fun record(state: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(3_500, 10_000)
        val directory = File(context.getExternalFilesDir(null), "explore-search-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun expandedSourcesKeepGroupsKindsAndLongMenuActions() {
        var toggled: BookSourcePart? = null
        var action: Pair<BookSourcePart, ExploreSourceAction>? = null
        var opened: Triple<String, String, String?>? = null
        renderExplore(ExploreUiState(sources = sources, groups = listOf("文学", "科幻"),
            expandedSourceUrl = sources[0].bookSourceUrl,
            kindsBySource = mapOf(sources[0].bookSourceUrl to kinds), isResumed = true),
            onToggle = { toggled = it }, onAction = { source, value -> action = source to value },
            onKind = { source, title, url -> opened = Triple(source, title, url) })
        compose.onNodeWithText(sources[0].bookSourceGroup!!).assertIsDisplayed()
        compose.onNodeWithText(kinds[0].title).assertIsDisplayed().performClick()
        assertEquals(Triple(sources[0].bookSourceUrl, kinds[0].title, kinds[0].url), opened)
        record("explore-normal")
        compose.onNodeWithText(sources[0].bookSourceName)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        record("explore-menu")
        compose.onNodeWithText(context.getString(R.string.edit)).performClick()
        assertEquals(sources[0] to ExploreSourceAction.Edit, action)
        compose.onNodeWithText(sources[1].bookSourceName).performScrollTo().performClick()
        assertEquals(sources[1], toggled)
    }

    @Test fun longExploreErrorKeepsRetryReachable() {
        var retries = 0
        renderExplore(ExploreUiState(sourcesError = "发现书源加载失败，请检查当前网络与书源配置。".repeat(48)),
            onRetry = { retries++ })
        record("explore-error-top")
        val retry = compose.onNodeWithText(context.getString(R.string.retry))
        retry.performScrollTo().assertIsDisplayed()
        record("explore-error")
        retry.performClick()
        assertEquals(1, retries)
    }

    @Test fun searchResultsKeepLongTitlesSourceCountAndShelfStatus() {
        var opened: SearchBookUi? = null
        renderSearch(SearchUiState(query = "图书馆", showInputHelp = false, results = books),
            onBook = { opened = it })
        compose.onNodeWithContentDescription(context.getString(R.string.search_already_on_shelf))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.search_result_sources, books[0].originCount))
            .assertIsDisplayed()
        compose.onNodeWithText(books[0].name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                assertEquals(2, layouts.single().lineCount)
                val layout = layouts.single()
                assertTrue("The final title line must fit its measured height",
                    layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
            }
        record("search-results")
        compose.onNodeWithText(books[0].name).performClick()
        assertEquals(books[0], opened)
    }

    @Test fun completedSearchShowsEmptyResultState() {
        renderSearch(SearchUiState(query = "雾港", showInputHelp = false))
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.search_paused)).assertDoesNotExist()
        record("search-empty")
    }

    @Test fun pausedEmptySearchWithMoreSourcesCanResume() {
        var resumes = 0
        renderSearch(SearchUiState(query = "雾港", showInputHelp = false,
            manuallyStopped = true, hasMore = true), onContinue = { resumes++ })
        compose.onNodeWithText(context.getString(R.string.search_paused)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertDoesNotExist()
        record("search-paused")
        compose.onNodeWithText(context.getString(R.string.search_resume)).performScrollTo().performClick()
        assertEquals(1, resumes)
    }

    @Test fun pausedSearchWithResultsCanResume() {
        var resumes = 0
        renderSearch(SearchUiState(query = "雾港", showInputHelp = false,
            results = books, manuallyStopped = true, hasMore = true), onContinue = { resumes++ })
        compose.onNodeWithText(books[0].name).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertDoesNotExist()
        val resumeLabel = context.getString(R.string.search_resume)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(resumeLabel))
        val resume = compose.onNodeWithText(resumeLabel).assertIsDisplayed()
        assertEquals(0, resumes)
        record("search-paused-results")
        resume.performClick()
        assertEquals(1, resumes)
    }

    @Test fun searchingWithoutResultsDoesNotClaimEmptyOrPaused() {
        var stops = 0
        renderSearch(SearchUiState(query = "雾港", showInputHelp = false, isSearching = true,
            hasMore = true), onStop = { stops++ })
        compose.onNodeWithText(context.getString(R.string.search_no_results)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.search_paused)).assertDoesNotExist()
        compose.onNodeWithContentDescription("停止搜索").assertIsDisplayed()
        record("search-loading")
        compose.onNodeWithContentDescription("停止搜索").performClick()
        assertEquals(1, stops)
    }

    @Test fun longHistoryTermKeepsDeleteTargetAndCallbacks() {
        val term = "在漫长的旅途中寻找一座没有名字的图书馆与那些未曾寄出的信".repeat(5)
        var searched: String? = null
        var deleted: String? = null
        renderSearch(SearchUiState(history = listOf(term)),
            onHistory = { searched = it }, onDelete = { deleted = it })
        val delete = compose.onNodeWithContentDescription("删除搜索记录")
            .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        record("search-history")
        compose.onNodeWithText(term).performClick()
        assertEquals(term, searched)
        delete.performClick()
        assertEquals(term, deleted)
    }
}
