package io.legado.app.ui.book.toc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChapterAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyCatalogShowsFeedbackAndDisablesPositionControls() {
        compose.setContent { LegadoTheme { ChapterListScreen(ChapterListUiState(), {}, {}) } }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(io.legado.app.R.string.empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription("回到目录顶部").assertIsNotEnabled()
        compose.onNodeWithContentDescription("跳到目录底部").assertIsNotEnabled()
    }

    @Test fun currentChapterIsSelectedAndAnotherChapterOpens() {
        var opened = -1
        val chapters = listOf(
            BookChapter(bookUrl = "qa://toc", url = "1", title = "第一章", index = 0),
            BookChapter(bookUrl = "qa://toc", url = "2", title = "第二章", index = 1)
        )
        compose.setContent {
            LegadoTheme { ChapterListScreen(ChapterListUiState(chapters = chapters), { opened = it.index }, {}) }
        }
        compose.onNodeWithText("第一章").assertIsSelected()
        compose.onNodeWithText("第二章").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
