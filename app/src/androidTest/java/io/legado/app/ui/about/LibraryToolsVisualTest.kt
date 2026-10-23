package io.legado.app.ui.about

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.bookmark.AllBookmarkScreen
import io.legado.app.ui.widget.compose.LegadoMotion
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** In-memory content and callbacks only; the Activity tests own search and DAO behavior. */
class LibraryToolsVisualTest {
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
    private val bookmarks = List(12) { index ->
        Bookmark(time = index + 1L,
            bookName = if (index < 8) "北岸来信" else "群山之上的漫长冬日与另一封尚未抵达的回信",
            bookAuthor = when { index < 4 -> "林青遥"; index < 8 -> "同名书的另一位作者"; else -> "许知远" },
            chapterName = if (index == 1) "第二章 穿过漫长雨季以后仍未抵达的车站和一封没有邮戳的来信"
                else "第${index + 1}章 灯塔下的回信",
            bookText = if (index == 2) "" else "北岸邮局最后一名投递员发现一叠迟到多年的信，每封信都把小镇带回一个温柔的选择。".repeat(if (index == 1) 4 else 1),
            content = if (index % 2 == 0) "下次回到港口这一章，看看那些还没有寄出的信。" else "")
    }
    private val records = listOf(
        ReadRecordShow("北岸来信", 98_765_000L, 1_779_379_200_000L),
        ReadRecordShow("群山之上的漫长冬日与另一封尚未抵达的回信", 72_500_000L, 1_779_292_800_000L),
        ReadRecordShow("夜航西飞", 3_650_000L, 1_779_206_400_000L),
        ReadRecordShow("浮生六记", 65_000L, 0L),
        ReadRecordShow("山河来信", 0L, 1_779_120_000_000L)
    )

    @Composable
    private fun Fixture(title: String, width: Dp = 10_000.dp, fontScale: Float? = null,
                        motion: LegadoMotion? = null, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalContext provides context,
            LocalDensity provides Density(density.density, fontScale
                ?: arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
            LegadoTheme {
                assertTrue("正文与surface对比度需至少 4.5:1", ColorUtils.calculateContrast(
                    MaterialTheme.colorScheme.onSurface.toArgb(), MaterialTheme.colorScheme.surface.toArgb()) >= 4.5)
                CompositionLocalProvider(LocalLegadoMotion provides (motion ?: LocalLegadoMotion.current)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = width).fillMaxSize()) {
                            LegadoTopAppBar(title = { Text(title) })
                            content()
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
        val directory = File(context.getExternalFilesDir(null), "library-tools-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun bookmarkGroupsPreserveOpenAndLongEdit() {
        var opened: Bookmark? = null
        var edited: Pair<Bookmark, Int>? = null
        compose.setContent {
            Fixture("所有书签") {
                AllBookmarkScreen(bookmarks, onBookmarkClick = { opened = it },
                    onBookmarkLongClick = { item, index -> edited = item to index })
            }
        }
        compose.onNodeWithText(bookmarks[0].chapterName).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(bookmarks[0], opened)
        compose.onNodeWithText(bookmarks[0].chapterName)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertEquals(bookmarks[0] to 0, edited)
        record("bookmarks-normal")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(bookmarks[4].chapterName))
        compose.onNodeWithText("同名书的另一位作者").assertIsDisplayed()
        compose.onNodeWithText(bookmarks[4].chapterName)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertEquals(bookmarks[4] to 0, edited)
        record("bookmarks-grouped")
    }

    @Test fun bookmarkNarrowLargeTypeKeepsLongContentAndLastItemReachable() {
        compose.setContent {
            Fixture("所有书签", width = 320.dp, fontScale = 2f) {
                AllBookmarkScreen(bookmarks, onBookmarkClick = {}, onBookmarkLongClick = { _, _ -> })
            }
        }
        compose.onNodeWithText(bookmarks[0].chapterName).assertIsDisplayed()
        record("bookmarks-narrow-large-type")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(bookmarks.last().chapterName))
        compose.onNodeWithText(bookmarks.last().chapterName).assertIsDisplayed()
    }

    @Test fun bookmarkEmptyUsesExistingMessage() {
        compose.setContent {
            Fixture("所有书签") {
                AllBookmarkScreen(emptyList(), onBookmarkClick = {}, onBookmarkLongClick = { _, _ -> })
            }
        }
        compose.onNodeWithText(context.getString(R.string.empty)).assertIsDisplayed()
        record("bookmarks-empty")
    }

    @Test fun bookmarkLoadingAndErrorRetryRecoverToEmptyAndContent() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf(false)
        var items by mutableStateOf<List<Bookmark>>(emptyList())
        var retries = 0
        compose.setContent {
            Fixture("所有书签", motion = LegadoMotion(false)) {
                AllBookmarkScreen(items, onBookmarkClick = {}, onBookmarkLongClick = { _, _ -> },
                    loading = loading, loadError = error, onRetry = {
                        retries++
                        loading = true
                        error = false
                    })
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.empty)).assertDoesNotExist()
        record("bookmarks-loading-reduced-motion")
        compose.runOnIdle { loading = false; error = true }
        compose.onNodeWithText(context.getString(R.string.error_get_data)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.empty)).assertDoesNotExist()
        record("bookmarks-error")
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertEquals(1, retries)
        compose.onNodeWithContentDescription(context.getString(R.string.loading)).assertIsDisplayed()
        compose.runOnIdle { loading = false }
        compose.onNodeWithText(context.getString(R.string.empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).assertDoesNotExist()
        compose.runOnIdle { error = true }
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertEquals(2, retries)
        compose.runOnIdle { items = bookmarks; loading = false }
        compose.onNodeWithText(bookmarks[0].chapterName).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.loading)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.error_get_data)).assertDoesNotExist()
        record("bookmarks-recovered")
    }

    @Test fun bookmarkErrorAndRetryLoadingKeepExistingContentInteractive() {
        var loading by mutableStateOf(false)
        var error by mutableStateOf(true)
        var opened: Bookmark? = null
        var edited: Pair<Bookmark, Int>? = null
        compose.setContent {
            Fixture("所有书签", motion = LegadoMotion(false)) {
                AllBookmarkScreen(bookmarks, onBookmarkClick = { opened = it },
                    onBookmarkLongClick = { item, index -> edited = item to index },
                    loading = loading, loadError = error, onRetry = {
                        loading = true
                        error = false
                    })
            }
        }
        compose.onNodeWithText(context.getString(R.string.error_get_data)).assertIsDisplayed()
        compose.onNodeWithText(bookmarks[0].chapterName).assertIsDisplayed().performClick()
        assertEquals(bookmarks[0], opened)
        compose.onNodeWithText(bookmarks[0].chapterName)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertEquals(bookmarks[0] to 0, edited)
        record("bookmarks-content-error")
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        compose.onNodeWithText(context.getString(R.string.loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.error_get_data)).assertDoesNotExist()
        compose.runOnIdle { opened = null; edited = null }
        compose.onNodeWithText(bookmarks[0].chapterName).assertIsDisplayed().performClick()
        assertEquals(bookmarks[0], opened)
        compose.onNodeWithText(bookmarks[0].chapterName)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertEquals(bookmarks[0] to 0, edited)
        record("bookmarks-content-reloading")
    }

    @Test fun recordsPreserveOpenDeleteAndGlobalTotal() {
        var opened: ReadRecordShow? = null
        var deleted: ReadRecordShow? = null
        var opens = 0
        var cleared = 0
        compose.setContent {
            Fixture("阅读记录") {
                ReadRecordContent(records, "8天12小时35分钟", false, false, {}, { cleared++ },
                    onOpen = { opened = it; opens++ }, onDelete = { deleted = it })
            }
        }
        compose.onNodeWithText("8天12小时35分钟").assertIsDisplayed()
        compose.onNodeWithText(records[0].bookName).performClick()
        assertEquals(records[0], opened)
        compose.onNodeWithContentDescription("${context.getString(R.string.clear)} ${records[0].bookName}")
            .assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(records[0], deleted)
        assertEquals(1, opens)
        compose.onAllNodesWithText(context.getString(R.string.clear))[0].performClick()
        assertEquals(1, cleared)
        record("records-normal")
    }

    @Test fun recordsNarrowLargeTypeKeepDeleteAndLastItemReachable() {
        compose.setContent {
            Fixture("阅读记录", width = 320.dp, fontScale = 2f) {
                ReadRecordContent(records, "8天12小时35分钟", false, false, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(records[0].bookName).assertIsDisplayed()
        record("records-narrow-large-type")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .performScrollToNode(hasText(records.last().bookName))
        compose.onNodeWithText(records.last().bookName).assertIsDisplayed()
        compose.onNodeWithContentDescription("${context.getString(R.string.clear)} ${records.last().bookName}")
            .assertIsDisplayed().assertWidthIsAtLeast(48.dp)
    }

    @Test fun recordsLoadingErrorAndEmptyKeepRecoveryAndDisabledClear() {
        var loading by mutableStateOf(true)
        var error by mutableStateOf(false)
        var retries = 0
        compose.setContent {
            Fixture("阅读记录", motion = LegadoMotion(false)) {
                ReadRecordContent(emptyList(), "0秒", loading, error, { retries++ }, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.clear)).assertIsNotEnabled()
        record("records-loading-reduced-motion")
        compose.runOnIdle { loading = false; error = true }
        compose.onNodeWithText(context.getString(R.string.error_get_data)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        assertEquals(1, retries)
        record("records-error")
        compose.runOnIdle { error = false }
        compose.onNodeWithText(context.getString(R.string.read_record_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).assertDoesNotExist()
        record("records-empty")
    }
}
