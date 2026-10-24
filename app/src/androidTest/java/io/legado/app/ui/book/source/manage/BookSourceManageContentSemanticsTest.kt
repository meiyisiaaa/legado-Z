package io.legado.app.ui.book.source.manage

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class BookSourceManageContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourceControlsNameTheirSourceAndReportSelectionAndEnabledState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = BookSourcePart(
            bookSourceUrl = "https://example.invalid",
            bookSourceName = "示例书源",
            enabled = false
        )
        val sourceName = source.getDisPlayNameGroup()

        composeRule.setContent {
            LegadoTheme {
                BookSourceManageContent(
                    sources = listOf(source),
                    groups = emptyList(),
                    query = "",
                    selectedUrls = emptySet(),
                    selectionVersion = 0,
                    sort = BookSourceSort.Default,
                    sortAscending = true,
                    groupByDomain = false,
                    sourceHost = { "example.invalid" },
                    debugMessages = mapOf(source.bookSourceUrl to "检测失败"),
                    isChecking = false,
                    onBack = {},
                    onQueryChange = {},
                    onTopAction = {},
                    onSort = {},
                    onToggleSortDirection = {},
                    onToggleGroupByDomain = {},
                    onSelectionAction = {},
                    onSelectionChange = { _, _ -> },
                    onEnabledChange = { _, _ -> },
                    onExploreChange = { _, _ -> },
                    onEdit = {},
                    onLogin = {},
                    onMove = { _, _, _, _ -> },
                    onSearchBook = {},
                    onDebug = {},
                    onTop = {},
                    onBottom = {},
                    onDelete = {},
                    onSelectAll = {},
                    onRevertSelection = {},
                    onDeleteSelected = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(sourceName).assertIsOff()
        composeRule.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.enable)}").assertIsOff()
        composeRule.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.edit)}")
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription("$sourceName, ${context.getString(R.string.more_menu)}")
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithContentDescription(context.getString(R.string.select_all)).assertIsOff()
        composeRule.onNodeWithText("检测失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)
        )
    }
}
