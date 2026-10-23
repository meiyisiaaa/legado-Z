package io.legado.app.ui.book.info

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class BookInfoScreenInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactIdentityLeavesTheCurrentChapterVisible() {
        compose.setContent { LegadoTheme {
            Box(Modifier.width(320.dp).height(536.dp)) {
                BookInfoScreen(BookInfoUiState(name = "北岸来信", author = "林青遥", origin = "本地书源",
                    group = "未分组", kinds = listOf("5100", "原创·海边日常"),
                    currentChapter = "第一章 没有邮戳的信", loading = false),
                    coverContent = { Box(it) }, introContent = {},
                    onCoverClick = {}, onCoverLongClick = {}, onNameClick = {}, onNameLongClick = {},
                    onAuthorClick = {}, onAuthorLongClick = {}, onOriginClick = {}, onChangeSource = {},
                    onChangeGroup = {}, onOpenToc = {}, onKindClick = {}, onKindLongClick = {},
                    onShelfClick = {}, onReadClick = {})
            }
        } }
        compose.onNodeWithText("北岸来信").assertIsDisplayed()
        compose.onNodeWithText("第一章 没有邮戳的信").assertIsDisplayed()
        val chapterBounds = compose.onNodeWithText("第一章 没有邮戳的信").fetchSemanticsNode().boundsInRoot
        val readBounds = compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.reading)).fetchSemanticsNode().boundsInRoot
        assertTrue("The entire current chapter stays above the fixed actions", chapterBounds.bottom <= readBounds.top)
    }

    @Test fun shelfReadingAndTocActionsRemainSeparate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var inShelf by mutableStateOf(false)
        var shelfChanges = 0
        var reads = 0
        var tocOpens = 0
        compose.setContent { LegadoTheme {
            BookInfoScreen(BookInfoUiState(name = "详情交互测试", author = "作者", origin = "离线来源",
                currentChapter = "第一章", latestChapter = "第二章", inBookshelf = inShelf, loading = false),
                coverContent = { Box(it) }, introContent = {},
                onCoverClick = {}, onCoverLongClick = {}, onNameClick = {}, onNameLongClick = {},
                onAuthorClick = {}, onAuthorLongClick = {}, onOriginClick = {}, onChangeSource = {},
                onChangeGroup = {}, onOpenToc = { tocOpens++ }, onKindClick = {}, onKindLongClick = {},
                onShelfClick = { inShelf = !inShelf; shelfChanges++ }, onReadClick = { reads++ })
        } }
        compose.onNodeWithText(context.getString(R.string.add_to_bookshelf)).performClick()
        compose.onNodeWithText(context.getString(R.string.reading)).performClick()
        compose.onNodeWithText(context.getString(R.string.remove_from_bookshelf)).performClick()
        compose.onNodeWithText(context.getString(R.string.view_toc)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, shelfChanges)
            assertEquals(1, reads)
            assertEquals(1, tocOpens)
        }
    }

    @Test
    fun longShelfActionFitsAtNarrowWidthWithLargeFontAndRemainsClickable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localizedConfiguration = Configuration(context.resources.configuration).apply {
            setLocale(Locale("vi"))
        }
        val localizedContext = context.createConfigurationContext(localizedConfiguration)
        val composeConfiguration = Configuration(localizedContext.resources.configuration)
        val deleteLabel = localizedContext.resources.getString(R.string.remove_from_bookshelf)
        val readLabel = localizedContext.resources.getString(R.string.reading)
        assertEquals("Xóa khỏi tủ sách", deleteLabel)
        var shelfClicks = 0

        compose.setContent {
            LegadoTheme {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides composeConfiguration,
                    LocalDensity provides Density(currentDensity.density, fontScale = 2f)
                ) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        BookInfoScreen(
                            state = BookInfoUiState(
                                name = "窄屏字体缩放测试",
                                inBookshelf = true,
                                loading = false
                            ),
                            coverContent = { Box(it) },
                            introContent = {},
                            onCoverClick = {},
                            onCoverLongClick = {},
                            onNameClick = {},
                            onNameLongClick = {},
                            onAuthorClick = {},
                            onAuthorLongClick = {},
                            onOriginClick = {},
                            onChangeSource = {},
                            onChangeGroup = {},
                            onOpenToc = {},
                            onKindClick = {},
                            onKindLongClick = {},
                            onShelfClick = { shelfClicks++ },
                            onReadClick = {}
                        )
                    }
                }
            }
        }

        val textLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(deleteLabel, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getTextLayoutResult ->
                assertTrue(getTextLayoutResult(textLayouts))
            }
        assertEquals(1, textLayouts.size)
        assertTrue("The large-font delete label should wrap in 320dp", textLayouts.single().lineCount > 1)
        assertFalse(
            "The full delete label must fit within the button height at font scale 2",
            textLayouts.single().didOverflowHeight
        )

        val deleteButtonBounds = compose.onNodeWithText(deleteLabel).fetchSemanticsNode().boundsInRoot
        val readButtonBounds = compose.onNodeWithText(readLabel).fetchSemanticsNode().boundsInRoot
        assertTrue("The shelf action must remain left of the reading action", deleteButtonBounds.left < readButtonBounds.left)
        assertEquals(
            "Both bottom actions must remain the same height",
            deleteButtonBounds.height,
            readButtonBounds.height,
            0.5f
        )

        compose.onNodeWithText(deleteLabel).performClick()
        compose.runOnIdle { assertEquals(1, shelfClicks) }
    }
}
