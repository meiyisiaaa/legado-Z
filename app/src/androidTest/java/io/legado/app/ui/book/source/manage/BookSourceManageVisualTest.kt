package io.legado.app.ui.book.source.manage

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Controlled content exercises presentation and callbacks without changing saved sources. */
class BookSourceManageVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext.let { target ->
        val dark = AppConfig.isNightTheme && !AppConfig.isEInkMode
        val localizedContext = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        ContextThemeWrapper(localizedContext, if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
    }
    private val sources = listOf(
        BookSourcePart(bookSourceUrl = "https://letters.example.invalid/", bookSourceName = "远方来信",
            bookSourceGroup = "文学", enabled = false, hasLoginUrl = true, hasExploreUrl = true),
        BookSourcePart(bookSourceUrl = "https://library.example.invalid/", bookSourceName = "在漫长的旅途中寻找一座没有名字的图书馆与那些未曾寄出的信",
            bookSourceGroup = "文学, 典藏"),
        BookSourcePart(bookSourceUrl = "https://paper.example.invalid/", bookSourceName = "纸间拾光")
    )
    private val dragSources = listOf(
        BookSourcePart(bookSourceUrl = "https://drag-first.example.invalid/", bookSourceName = "拖动起点"),
        BookSourcePart(bookSourceUrl = "https://drag-middle.example.invalid/", bookSourceName = "行高不同的第二条书源与完整的手动排序目标"),
        BookSourcePart(bookSourceUrl = "https://drag-last.example.invalid/", bookSourceName = "最后一条")
    )

    private fun render(
        entries: List<BookSourcePart> = sources,
        query: String = "",
        width: Dp = 10_000.dp,
        fontScale: Float? = null,
        byDomain: Boolean = false,
        onQuery: (String) -> Unit = {},
        onEdit: (BookSourcePart) -> Unit = {},
        onEnabled: (BookSourcePart, Boolean) -> Unit = { _, _ -> },
        onExplore: (BookSourcePart, Boolean) -> Unit = { _, _ -> },
        onMove: (String, String, Boolean) -> Unit = { _, _, _ -> },
        onAction: (Int) -> Unit = {}
    ) {
        val localizedContext = context
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localizedContext,
                LocalDensity provides Density(density.density, fontScale
                    ?: arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                LegadoTheme {
                    assertTrue("正文与surface对比度需至少 4.5:1", ColorUtils.calculateContrast(
                        MaterialTheme.colorScheme.onSurface.toArgb(), MaterialTheme.colorScheme.surface.toArgb()) >= 4.5)
                    var currentQuery by remember { mutableStateOf(query) }
                    var selected by remember {
                        mutableStateOf(entries.take(1).map { it.bookSourceUrl }.toSet())
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = width).fillMaxSize()) {
                            BookSourceManageContent(
                                sources = entries, groups = listOf("文学", "典藏"), query = currentQuery,
                                selectedUrls = selected, selectionVersion = selected.size,
                                sort = BookSourceSort.Default, sortAscending = true, groupByDomain = byDomain,
                                sourceHost = { it.substringAfter("https://").substringBefore('/') },
                                debugMessages = entries.take(1).associate { it.bookSourceUrl to "检测失败：连接超时" },
                                isChecking = false, onBack = {},
                                onQueryChange = { currentQuery = it; onQuery(it) },
                                onTopAction = onAction, onSort = {}, onToggleSortDirection = {},
                                onToggleGroupByDomain = {}, onSelectionAction = onAction,
                                onSelectionChange = { source, checked ->
                                    selected = if (checked) selected + source.bookSourceUrl else selected - source.bookSourceUrl
                                },
                                onEnabledChange = onEnabled, onExploreChange = onExplore, onEdit = onEdit,
                                onLogin = {},
                                onMove = { from, to, after, onFinally -> onMove(from, to, after); onFinally() },
                                onSearchBook = {}, onDebug = {},
                                onTop = {}, onBottom = {}, onDelete = {},
                                onSelectAll = { selected = entries.map { it.bookSourceUrl }.toSet() },
                                onRevertSelection = { selected = entries.map { it.bookSourceUrl }.toSet() - selected },
                                onDeleteSelected = {}
                            )
                        }
                    }
                }
            }
        }
    }

    private fun record(state: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(1_000, 5_000)
        val directory = File(context.getExternalFilesDir(null), "book-source-v3-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    private fun drag(from: BookSourcePart, to: BookSourcePart) {
        val start = compose.onNodeWithText(from.bookSourceName).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithText(to.bookSourceName).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot.center
        val rootOrigin = compose.onRoot().fetchSemanticsNode().boundsInRoot.topLeft
        compose.onRoot().performTouchInput {
            down(start - rootOrigin)
            moveTo(start - rootOrigin, delayMillis = ViewConfiguration.getLongPressTimeout().toLong() + 100L)
            for (step in 1..10) {
                moveTo(start + (target - start) * (step / 10f) - rootOrigin, delayMillis = 16L)
            }
            up()
        }
    }

    @Test fun sourceControlsAndExploreMenuKeepTheirCallbacks() {
        var edited: BookSourcePart? = null
        var enabled: Pair<BookSourcePart, Boolean>? = null
        var explored: Pair<BookSourcePart, Boolean>? = null
        render(onEdit = { edited = it }, onEnabled = { source, checked -> enabled = source to checked },
            onExplore = { source, checked -> explored = source to checked })
        val sourceName = sources[0].getDisPlayNameGroup()
        record("normal")
        compose.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.edit)}")
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp).performClick()
        compose.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.enable)}")
            .assertIsOff().performClick()
        compose.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.more_menu)}")
            .performClick()
        compose.onNodeWithText(context.getString(R.string.login)).assertIsDisplayed()
        record("source-menu")
        compose.onNodeWithText(context.getString(R.string.disable_explore)).performClick()
        compose.runOnIdle {
            assertEquals(sources[0], edited)
            assertEquals(sources[0] to true, enabled)
            assertEquals(sources[0] to false, explored)
        }
    }

    @Test fun emptySearchCanClearAndAddSource() {
        var query: String? = null
        var action = 0
        render(entries = emptyList(), query = "未找到的书源", onQuery = { query = it }, onAction = { action = it })
        compose.onNodeWithText(context.getString(R.string.empty)).assertIsDisplayed()
        record("search-empty")
        compose.onNodeWithContentDescription(context.getString(R.string.clear)).performClick()
        compose.onNodeWithText(context.getString(R.string.add_book_source)).performClick()
        compose.runOnIdle {
            assertEquals("", query)
            assertEquals(R.id.menu_add_book_source, action)
        }
    }

    @Test fun narrowLargeTextKeepsSourceToolsReachable() {
        var edited: BookSourcePart? = null
        render(width = 320.dp, fontScale = 2f, onEdit = { edited = it })
        val sourceName = sources[0].getDisPlayNameGroup()
        compose.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.edit)}")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(sources[0], edited) }
        record("narrow-large-type")
    }

    @Test fun domainGroupingKeepsHeaderAndHidesManualPositionActions() {
        render(byDomain = true)
        compose.onNodeWithText("letters.example.invalid").assertIsDisplayed()
        compose.onNodeWithContentDescription("${sources[0].getDisPlayNameGroup()}, ${context.getString(R.string.more_menu)}")
            .performClick()
        compose.onNodeWithText(context.getString(R.string.to_top)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.to_bottom)).assertDoesNotExist()
    }

    @Test fun longPressDragMovesSourceAfterTheVisibleTarget() {
        val moves = mutableListOf<Triple<String, String, Boolean>>()
        render(entries = dragSources, fontScale = 1f,
            onMove = { from, to, after -> moves += Triple(from, to, after) })
        drag(dragSources[0], dragSources[1])
        compose.runOnIdle {
            assertEquals(listOf(Triple(dragSources[0].bookSourceUrl, dragSources[1].bookSourceUrl, true)), moves)
        }
    }

    @Test fun longPressDragMovesSourceBeforeThePreviousVisibleTarget() {
        val moves = mutableListOf<Triple<String, String, Boolean>>()
        render(entries = dragSources, fontScale = 1f,
            onMove = { from, to, after -> moves += Triple(from, to, after) })
        drag(dragSources[1], dragSources[0])
        compose.runOnIdle {
            assertEquals(listOf(Triple(dragSources[1].bookSourceUrl, dragSources[0].bookSourceUrl, false)), moves)
        }
    }

    @Test fun longPressDragCanCrossAVisibleRow() {
        val moves = mutableListOf<Triple<String, String, Boolean>>()
        render(entries = dragSources, fontScale = 1f,
            onMove = { from, to, after -> moves += Triple(from, to, after) })
        drag(dragSources[0], dragSources[2])
        compose.runOnIdle {
            assertEquals(listOf(Triple(dragSources[0].bookSourceUrl, dragSources[2].bookSourceUrl, true)), moves)
        }
    }

    @Test fun longPressWithoutChangingRowsDoesNotMoveSource() {
        val moves = mutableListOf<Triple<String, String, Boolean>>()
        render(entries = dragSources, fontScale = 1f,
            onMove = { from, to, after -> moves += Triple(from, to, after) })
        drag(dragSources[0], dragSources[0])
        compose.runOnIdle { assertTrue(moves.isEmpty()) }
    }

    @Test fun groupAddRenameDeleteAndCloseKeepTheirCallbacks() {
        val group = "文学与经典"
        var added = ""
        var renamed: Pair<String, String>? = null
        var deleted = ""
        var closes = 0
        val localizedContext = context
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localizedContext,
                LocalDensity provides Density(density.density,
                    arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                LegadoTheme {
                    BookSourceGroupManageContent(listOf(group, "来自远方的图书馆与未寄出的信"),
                        onAdd = { added = it }, onRename = { old, new -> renamed = old to new },
                        onDelete = { deleted = it }, onClose = { closes++ })
                }
            }
        }
        record("groups")
        compose.onNodeWithContentDescription("$group, ${context.getString(R.string.edit)}")
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp).performClick()
        record("group-edit")
        compose.onNode(hasSetTextAction()).performTextReplacement("重新命名")
        compose.onNodeWithText(context.getString(R.string.ok)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.add_group)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("新增分组")
        compose.onNodeWithText(context.getString(R.string.ok)).performClick()
        compose.onNodeWithContentDescription("$group, ${context.getString(R.string.delete)}").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.runOnIdle {
            assertEquals("新增分组", added)
            assertEquals(group to "重新命名", renamed)
            assertEquals(group, deleted)
            assertEquals(1, closes)
        }
    }
}
