package io.legado.app.ui.book.toc.rule

import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

class TxtTocRuleContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun managementControlsExposeRuleNameSelectionAndEnabledState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rule = TxtTocRule(id = 4L, name = "章节标题", enable = false)
        val selected = mutableStateOf(setOf(rule.id))

        composeRule.setContent {
            LegadoTheme {
                TxtTocRuleManageContent(
                    rules = listOf(rule),
                    selectedIds = selected.value,
                    onAdd = {},
                    onImport = {},
                    onSelectionChange = { id, checked ->
                        selected.value = if (checked) selected.value + id else selected.value - id
                    },
                    onSelectAll = {},
                    onReverseSelection = {},
                    onToggleEnabled = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onDeleteSelected = {},
                    onEnableSelected = {},
                    onDisableSelected = {},
                    onExportSelected = {},
                    onMove = { _, _ -> },
                    onDragFinished = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(rule.name).assertIsOn()
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.enable)}")
            .assertIsOff()
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.edit)}")
            .assertExists()
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.more_menu)}")
            .assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.select_all))
            .assertIsOn()
        composeRule.onNodeWithContentDescription(rule.name).performClick().assertIsOff()
        composeRule.onNodeWithContentDescription(context.getString(R.string.select_all))
            .assertIsOff()
    }

    @Test
    fun pickerRadioButtonNamesAndReportsSelectedRule() {
        val rule = TxtTocRule(id = 4L, name = "章节标题")

        composeRule.setContent {
            LegadoTheme {
                TxtTocRulePickerContent(
                    rules = listOf(rule),
                    selectedName = rule.name,
                    onSelectedNameChange = {},
                    onAdd = {},
                    onImport = {},
                    onToggleEnabled = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onMove = { _, _ -> },
                    onDragFinished = {},
                    onCancel = {},
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(rule.name).assertIsSelected()
    }

    @Test
    fun managementSwipeAndLazyLoadingReachLastRule() {
        assertLastRuleReachable(picker = false)
    }

    @Test
    fun pickerSwipeAndLazyLoadingReachLastRule() {
        assertLastRuleReachable(picker = true)
    }

    @Test
    fun managementLongPressReordersPastInitialViewport() {
        assertEdgeDragReachesBeyondViewport(picker = false)
    }

    @Test
    fun pickerLongPressReordersPastInitialViewport() {
        assertEdgeDragReachesBeyondViewport(picker = true)
    }

    @Test
    fun sameSizeListUsesLatestDragCallbacks() {
        val rules = testRules()
        val updated = mutableStateOf(false)
        var oldMoves = 0
        var newMoves = 0
        composeRule.setContent {
            LegadoTheme {
                RuleListFixture(rules, onMove = if (updated.value) {
                    { _, _ -> newMoves++ }
                } else {
                    { _, _ -> oldMoves++ }
                })
            }
        }
        composeRule.runOnIdle { updated.value = true }
        val list = composeRule.onNode(hasScrollToIndexAction())
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val dragX = bounds.width * 0.35f
        val start = Offset(dragX, composeRule.onNodeWithText(rules[0].name).fetchSemanticsNode().boundsInRoot.center.y - bounds.top)
        val target = Offset(dragX, composeRule.onNodeWithText(rules[1].name).fetchSemanticsNode().boundsInRoot.center.y - bounds.top)
        list.performTouchInput {
            down(start)
            moveTo(start, delayMillis = ViewConfiguration.getLongPressTimeout().toLong() + 100)
            moveTo(target, delayMillis = 100)
            up()
        }
        composeRule.runOnIdle {
            assertTrue("The current callback should receive the drag", newMoves > 0)
            assertTrue("The replaced callback must stay unused", oldMoves == 0)
        }
    }

    @Test
    fun managementSelectionDragIncludesCrossedRowsWithoutReordering() {
        val rules = testRules()
        val selected = mutableStateListOf<Long>()
        var moved = 0
        var persisted = 0
        composeRule.setContent {
            LegadoTheme {
                RuleListFixture(
                    rules, selectedIds = selected.toSet(),
                    onSelectionChange = { id, checked ->
                        if (checked && id !in selected) selected.add(id)
                        else if (!checked) selected.remove(id)
                    },
                    onMove = { _, _ -> moved++ }, onDragFinished = { persisted++ }
                )
            }
        }
        val list = composeRule.onNode(hasScrollToIndexAction())
        val origin = list.fetchSemanticsNode().boundsInRoot.topLeft
        val start = composeRule.onNodeWithContentDescription(rules[0].name).fetchSemanticsNode().boundsInRoot.center - origin
        val target = composeRule.onNodeWithContentDescription(rules[2].name).fetchSemanticsNode().boundsInRoot.center - origin
        list.performTouchInput {
            down(start)
            moveTo(start, delayMillis = ViewConfiguration.getLongPressTimeout().toLong() + 100)
            moveTo(target, delayMillis = 100)
            up()
        }
        rules.take(3).forEach { composeRule.onNodeWithContentDescription(it.name).assertIsOn() }
        composeRule.runOnIdle {
            assertEquals("Release keeps all drag-selected rows checked", rules.take(3).map { it.id }.toSet(), selected.toSet())
            assertTrue("Selection dragging keeps rule order", moved == 0 && persisted == 0)
        }
    }

    @Test
    fun sameFrameRepeatedDragMovesOnceAndKeepsViewportAnchor() {
        assertSameFrameDrag(1, listOf(2L, 1L, 3L, 4L))
    }

    @Test
    fun sameFrameDifferentDragTargetsKeepFinalOrder() {
        assertSameFrameDrag(2, listOf(2L, 3L, 1L, 4L))
    }

    private fun assertSameFrameDrag(finalTargetIndex: Int, expectedIds: List<Long>) {
        val rules = mutableStateListOf<TxtTocRule>().apply { addAll(testRules().take(4)) }
        val firstName = rules[0].name
        val secondName = rules[1].name
        var moved = 0
        composeRule.setContent {
            LegadoTheme {
                RuleListFixture(rules.toList(), onMove = { from, to ->
                    moved++
                    rules.add(to, rules.removeAt(from))
                })
            }
        }
        val list = composeRule.onNode(hasScrollToIndexAction())
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val firstBounds = composeRule.onNodeWithText(firstName).fetchSemanticsNode().boundsInRoot
        val dragX = bounds.width * 0.35f
        val start = Offset(dragX, firstBounds.center.y - bounds.top)
        val target = Offset(dragX, composeRule.onNodeWithText(secondName).fetchSemanticsNode().boundsInRoot.center.y - bounds.top)
        val finalTarget = Offset(dragX, composeRule.onNodeWithText(rules[finalTargetIndex].name).fetchSemanticsNode().boundsInRoot.center.y - bounds.top)
        composeRule.mainClock.autoAdvance = false
        try {
            list.performTouchInput {
                down(start)
                moveTo(start, delayMillis = ViewConfiguration.getLongPressTimeout().toLong() + 100)
                moveTo(target, delayMillis = 1)
                moveTo(finalTarget + Offset(0f, 1f), delayMillis = 0)
                up()
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        composeRule.runOnIdle {
            assertEquals("Events before layout should preserve the final pointer target", expectedIds, rules.map { it.id })
            assertEquals("Only changed targets require reordering", finalTargetIndex, moved)
        }
        val newFirstBounds = composeRule.onNodeWithText(secondName).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Reordering the first visible item should preserve the viewport", firstBounds.top, newFirstBounds.top, 1f)
    }

    private fun assertLastRuleReachable(picker: Boolean) {
        val rules = testRules()
        composeRule.setContent { LegadoTheme { RuleListFixture(rules, picker) } }
        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performTouchInput { swipeUp() }
        composeRule.onNodeWithText(rules.first().name).assertIsNotDisplayed()
        list.performScrollToIndex(rules.lastIndex)
        composeRule.onNodeWithText(rules.last().name).assertIsDisplayed()
    }

    private fun assertEdgeDragReachesBeyondViewport(picker: Boolean) {
        val rules = mutableStateListOf<TxtTocRule>().apply { addAll(testRules()) }
        val dragged = rules.first()
        var finished = 0
        composeRule.setContent {
            LegadoTheme {
                RuleListFixture(rules.toList(), picker, onMove = { from, to ->
                    rules.add(to, rules.removeAt(from))
                }, onDragFinished = { finished++ })
            }
        }
        val list = composeRule.onNode(hasScrollToIndexAction())
        val bounds = list.fetchSemanticsNode().boundsInRoot
        val lastInitiallyVisible = composeRule.onAllNodesWithText("规则", substring = true)
            .fetchSemanticsNodes().size - 1
        val start = Offset(bounds.width * 0.35f, composeRule.onNodeWithText(dragged.name).fetchSemanticsNode().boundsInRoot.center.y - bounds.top)
        list.performTouchInput {
            down(start)
            moveTo(start, delayMillis = ViewConfiguration.getLongPressTimeout().toLong() + 100)
            moveTo(Offset(start.x, bounds.height - 2), delayMillis = 100)
            advanceEventTime(2_000)
            up()
        }
        composeRule.runOnIdle {
            val draggedIndex = rules.indexOf(dragged)
            assertTrue("Held edge drag: index=$draggedIndex, initialVisibleCount=${lastInitiallyVisible + 1}, picker=$picker, finished=$finished, bounds=$bounds, start=$start", draggedIndex > lastInitiallyVisible)
            assertTrue("Order is persisted once on release", finished == 1)
        }
    }

    private fun testRules() = List(120) {
        TxtTocRule(id = it.toLong() + 1, name = "规则${it.toString().padStart(3, '0')}", rule = "第${it}章")
    }
}

@Composable
private fun RuleListFixture(
    rules: List<TxtTocRule>,
    picker: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onSelectionChange: (Long, Boolean) -> Unit = { _, _ -> },
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onDragFinished: () -> Unit = {}
) {
    if (picker) {
        TxtTocRulePickerContent(
            rules = rules, selectedName = null, onSelectedNameChange = {}, onAdd = {}, onImport = {},
            onToggleEnabled = { _, _ -> }, onEdit = {}, onDelete = {}, onMove = onMove,
            onDragFinished = onDragFinished, onCancel = {}, onConfirm = {}
        )
    } else {
        TxtTocRuleManageContent(
            rules = rules, selectedIds = selectedIds, onAdd = {}, onImport = {}, onSelectionChange = onSelectionChange,
            onSelectAll = {}, onReverseSelection = {}, onToggleEnabled = { _, _ -> }, onEdit = {}, onDelete = {},
            onDeleteSelected = {}, onEnableSelected = {}, onDisableSelected = {}, onExportSelected = {},
            onMove = onMove, onDragFinished = onDragFinished, onBack = {}
        )
    }
}
