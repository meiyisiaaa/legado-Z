package io.legado.app.ui.book.toc

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChapterAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyCatalogShowsFeedbackAndDisablesPositionControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LegadoTheme {
                ChapterListScreen(ChapterListUiState(), {}, {}, onRetry = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.chapter_list_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription("回到目录顶部").assertIsNotEnabled()
        compose.onNodeWithContentDescription("跳到目录底部").assertIsNotEnabled()
    }

    @Test fun loadingCatalogDoesNotShowEitherEmptyMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LegadoTheme {
                ChapterListScreen(
                    state = ChapterListUiState(searchKey = "999777", isLoading = true),
                    onChapterClick = {},
                    onChapterLongClick = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.data_loading))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText(context.getString(R.string.chapter_list_empty)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.chapter_search_empty)).assertDoesNotExist()
    }

    @Test fun chapterLoadErrorIsAnnouncedAndCanBeRetried() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var retryCount = 0
        compose.setContent {
            LegadoTheme {
                ChapterListScreen(
                    state = ChapterListUiState(
                        searchKey = "999777",
                        loadError = context.getString(R.string.error_get_data)
                    ),
                    onChapterClick = {},
                    onChapterLongClick = {},
                    onRetry = { retryCount++ }
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.error_get_data))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        compose.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test fun filteredEmptyStateDoesNotClaimTheCatalogIsEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LegadoTheme {
                ChapterListScreen(
                    state = ChapterListUiState(searchKey = "999777"),
                    onChapterClick = {},
                    onChapterLongClick = {},
                    onRetry = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.chapter_search_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chapter_list_empty)).assertDoesNotExist()
    }

    @Test fun currentChapterIsSelectedAndAnotherChapterOpens() {
        var opened = -1
        val chapters = listOf(
            BookChapter(bookUrl = "qa://toc", url = "1", title = "第一章", index = 0),
            BookChapter(bookUrl = "qa://toc", url = "2", title = "第二章", index = 1)
        )
        compose.setContent {
            LegadoTheme {
                ChapterListScreen(
                    state = ChapterListUiState(chapters = chapters),
                    onChapterClick = { opened = it.index },
                    onChapterLongClick = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("第一章").assertIsSelected()
        compose.onNodeWithText("第二章").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
