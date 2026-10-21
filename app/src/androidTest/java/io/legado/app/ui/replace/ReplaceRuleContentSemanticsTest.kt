package io.legado.app.ui.replace

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class ReplaceRuleContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ruleControlsExposeRuleNameAndToggleState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rule = ReplaceRule(id = 17L, name = "清理规则", isEnabled = false)

        composeRule.setContent {
            LegadoTheme {
                ReplaceRuleContent(
                    rules = listOf(rule),
                    selectedIds = listOf(rule.id),
                    onSelect = { _, _ -> },
                    onToggle = { _, _ -> },
                    onEdit = {},
                    onDelete = {},
                    onTop = {},
                    onBottom = {},
                    onMove = { _, _ -> },
                    onAdd = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(rule.getDisplayNameGroup()).assertIsOn()
        composeRule.onNodeWithContentDescription("${rule.getDisplayNameGroup()}, ${context.getString(R.string.enable)}")
            .assertIsOff()
        composeRule.onNodeWithContentDescription("${rule.getDisplayNameGroup()}, ${context.getString(R.string.edit)}").assertExists()
        composeRule.onNodeWithContentDescription("${rule.getDisplayNameGroup()}, ${context.getString(R.string.more_menu)}").assertExists()
    }
}
