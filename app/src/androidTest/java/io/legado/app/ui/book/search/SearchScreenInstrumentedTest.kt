package io.legado.app.ui.book.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editingSuggestionsHideContinueSearchForPreviousQuery() {
        var state by mutableStateOf(SearchUiState(query = "未提交草稿", hasMore = true))
        composeRule.setContent {
            LegadoTheme {
                SearchScreen(state, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
            }
        }

        composeRule.onNodeWithContentDescription("继续搜索").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(showInputHelp = false) }
        composeRule.onNodeWithContentDescription("继续搜索").assertExists()
    }

    @Test
    fun historyEntryStartsSearch() {
        var selectedHistory: String? = null
        composeRule.setContent {
            LegadoTheme {
                SearchScreen(
                    state = SearchUiState(history = listOf("三体")),
                    onHistoryClick = { selectedHistory = it },
                    onHistoryDelete = {},
                    onClearHistory = {},
                    onLocalBookClick = {},
                    onBookClick = { _, _ -> },
                    onStopSearch = {},
                    onContinueSearch = {},
                    onListEndReached = {}
                )
            }
        }

        composeRule.onNodeWithText("三体").performClick()

        assertEquals("三体", selectedHistory)
    }

    @Test
    fun historyDeleteHasAccessibleControl() {
        var deletedHistory: String? = null
        composeRule.setContent {
            LegadoTheme {
                SearchScreen(
                    state = SearchUiState(history = listOf("三体")),
                    onHistoryClick = {},
                    onHistoryDelete = { deletedHistory = it },
                    onClearHistory = {},
                    onLocalBookClick = {},
                    onBookClick = { _, _ -> },
                    onStopSearch = {},
                    onContinueSearch = {},
                    onListEndReached = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("删除搜索记录").assertExists().performClick()

        assertEquals("三体", deletedHistory)
    }

    @Test
    fun matchingBookshelfBookOpensThroughCallback() {
        var openedBook: SearchLocalBookUi? = null
        val localBook = SearchLocalBookUi("本地书名", "作者", "local://book")
        composeRule.setContent {
            LegadoTheme {
                SearchScreen(
                    state = SearchUiState(localBooks = listOf(localBook)),
                    onHistoryClick = {},
                    onHistoryDelete = {},
                    onClearHistory = {},
                    onLocalBookClick = { openedBook = it },
                    onBookClick = { _, _ -> },
                    onStopSearch = {},
                    onContinueSearch = {},
                    onListEndReached = {}
                )
            }
        }

        composeRule.onNodeWithText("本地书名").performClick()

        assertEquals(localBook, openedBook)
    }

    @Test
    fun searchToolbarGrowsWithLargeFontScale() {
        val fontScale = mutableFloatStateOf(1f)
        var minimumToolbarHeightPx = 0f
        composeRule.setContent {
            val systemDensity = LocalDensity.current
            minimumToolbarHeightPx = with(systemDensity) { 56.dp.toPx() }
            CompositionLocalProvider(
                LocalDensity provides Density(systemDensity.density, fontScale.floatValue)
            ) {
                LegadoTheme {
                    SearchToolbar(
                        state = SearchUiState(),
                        onQueryChange = {},
                        onQueryFocusChange = {},
                        onSubmit = {},
                        modifier = Modifier.testTag("search_toolbar")
                    )
                }
            }
        }

        val toolbar = composeRule.onNodeWithTag("search_toolbar")
        val defaultHeight = toolbar.fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Search toolbar should fit the default text field height",
            defaultHeight >= minimumToolbarHeightPx
        )
        composeRule.runOnIdle { fontScale.floatValue = 2f }
        composeRule.waitForIdle()
        val largeFontHeight = toolbar.fetchSemanticsNode().boundsInRoot.height

        assertTrue("Search toolbar should expand for large text", largeFontHeight > defaultHeight)
    }

    @Test
    fun searchToolbarClearsFocusWhenResultsReplaceSuggestions() {
        var state by mutableStateOf(
            SearchUiState(
                query = "三体",
                queryFocused = true,
                showInputHelp = true,
                focusRequestId = 1
            )
        )
        composeRule.setContent {
            LegadoTheme {
                SearchToolbar(
                    state = state,
                    onQueryChange = {},
                    onQueryFocusChange = {},
                    onSubmit = {}
                )
            }
        }

        val queryField = composeRule.onNode(hasSetTextAction())
        queryField.assertIsFocused()

        composeRule.runOnIdle {
            state = state.copy(queryFocused = false, showInputHelp = false)
        }
        composeRule.waitForIdle()

        queryField.assertIsNotFocused()
    }
}
