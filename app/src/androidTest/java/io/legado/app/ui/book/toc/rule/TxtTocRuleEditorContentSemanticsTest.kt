package io.legado.app.ui.book.toc.rule

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class TxtTocRuleEditorContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingAndReadFailureHaveAccessibleFeedback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loading = mutableStateOf(true)
        val error = mutableStateOf<String?>(null)
        composeRule.setContent {
            LegadoTheme {
                TxtTocRuleEditorContent(
                    rule = null,
                    loading = loading.value,
                    error = error.value,
                    onRuleChange = {},
                    onSave = {},
                    onCopy = {},
                    onPaste = {},
                    onClose = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assertIsDisplayed()

        composeRule.runOnIdle {
            loading.value = false
            error.value = "目录规则读取失败"
        }

        composeRule.onNodeWithText("目录规则读取失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)
        )
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_save)).assertIsNotEnabled()
    }
}
