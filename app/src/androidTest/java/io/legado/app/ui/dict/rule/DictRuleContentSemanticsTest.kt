package io.legado.app.ui.dict.rule

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class DictRuleContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ruleControlsExposeRuleNameAndToggleState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rule = DictRule(name = "词典 A", enabled = false)

        composeRule.setContent {
            LegadoTheme {
                DictRuleContent(
                    rules = listOf(rule),
                    selectedNames = listOf(rule.name),
                    onSelect = { _, _ -> },
                    onToggle = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onMove = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription(rule.name).assertIsOn()
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.enable)}")
            .assertIsOff()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(R.string.disabled)
                )
            )
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.edit)}").assertExists()
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.delete)}").assertExists()
        composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.more_menu)}").assertExists()
    }
}
