package io.legado.app.ui.book.toc.rule

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class TxtTocRuleContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun managementControlsExposeRuleNameSelectionAndEnabledState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rule = TxtTocRule(id = 4L, name = "章节标题", enable = false)

        composeRule.setContent {
            LegadoTheme {
                TxtTocRuleManageContent(
                    rules = listOf(rule),
                    selectedIds = setOf(rule.id),
                    onAdd = {},
                    onImport = {},
                    onSelectionChange = { _, _ -> },
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
}
