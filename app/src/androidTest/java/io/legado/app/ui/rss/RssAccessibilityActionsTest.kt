package io.legado.app.ui.rss

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.rss.read.ReadRssComposeBar
import io.legado.app.ui.rss.source.manage.RssSourceManageContent
import io.legado.app.ui.rss.subscription.RuleSubContent
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RssAccessibilityActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun rssSourceMoveActionsAreBoundedAndDispatchCorrectNeighbor() {
        val first = RssSource(sourceUrl = "https://example.invalid/first", sourceName = "第一源")
        val last = RssSource(sourceUrl = "https://example.invalid/last", sourceName = "末尾源")
        val moves = mutableListOf<Triple<String, String, Boolean>>()

        composeRule.setContent {
            LegadoTheme {
                RssSourceManageContent(
                    sources = listOf(first, last),
                    groups = emptyList(),
                    query = "",
                    selectedUrls = emptySet(),
                    selectionVersion = 0,
                    onBack = {},
                    onQueryChange = {},
                    onTopAction = {},
                    onSelectionAction = {},
                    onSelectionChange = { _, _ -> },
                    onEnabledChange = { _, _ -> },
                    onEdit = {},
                    onTop = {},
                    onBottom = {},
                    onMove = { source, target, movedDown, _ ->
                        moves += Triple(source, target, movedDown)
                    },
                    onDelete = {},
                    onSelectAll = {},
                    onRevertSelection = {},
                    onDeleteSelected = {}
                )
            }
        }

        val actions = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config[SemanticsActions.CustomActions] }

        assertEquals(2, actions.size)
        assertEquals(
            listOf(context.getString(R.string.move_down)),
            actions[0].map { it.label }
        )
        assertEquals(
            listOf(context.getString(R.string.move_up)),
            actions[1].map { it.label }
        )

        assertTrue(actions[0].single().action())
        assertTrue(actions[1].single().action())
        assertEquals(
            listOf(
                Triple(first.sourceUrl, last.sourceUrl, true),
                Triple(last.sourceUrl, first.sourceUrl, false)
            ),
            moves
        )
    }

    @Test
    fun ruleSubscriptionMoveActionsAreBoundedAndDispatchCorrectIndices() {
        val first = RuleSub(id = 101L, name = "第一规则", url = "https://example.invalid/first")
        val last = RuleSub(id = 102L, name = "末尾规则", url = "https://example.invalid/last")
        val moves = mutableListOf<Pair<Int, Int>>()
        var dragFinishedCount = 0

        composeRule.setContent {
            LegadoTheme {
                RuleSubContent(
                    subscriptions = listOf(first, last),
                    editing = null,
                    typeLabels = listOf("书籍"),
                    onBack = {},
                    onAdd = {},
                    onOpen = {},
                    onEdit = {},
                    onDelete = {},
                    onMove = { from, to -> moves.add(from to to) },
                    onDragFinished = { dragFinishedCount++ },
                    onSave = {},
                    onDismissEditor = {}
                )
            }
        }

        val firstActions = composeRule.onNodeWithText(first.name)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val lastActions = composeRule.onNodeWithText(last.name)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]

        assertEquals(listOf(context.getString(R.string.move_down)), firstActions.map { it.label })
        assertEquals(listOf(context.getString(R.string.move_up)), lastActions.map { it.label })

        assertTrue(firstActions.single().action())
        assertTrue(lastActions.single().action())
        assertEquals(listOf(0 to 1, 1 to 0), moves)
        assertEquals(2, dragFinishedCount)
    }

    @Test
    fun rssReadFavoriteButtonTracksSelectedState() {
        val favorite = mutableStateOf(false)

        composeRule.setContent {
            LegadoTheme {
                ReadRssComposeBar(
                    title = "测试文章",
                    hasArticle = true,
                    isFavorite = favorite.value,
                    isSpeaking = false,
                    hasLogin = false,
                    onBack = {},
                    onRefresh = {},
                    onFavorite = { favorite.value = !favorite.value },
                    onShare = {},
                    onSpeak = {},
                    onAction = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.out_favorites))
            .assertIsNotSelected()
            .performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.in_favorites))
            .assertIsSelected()
    }
}
