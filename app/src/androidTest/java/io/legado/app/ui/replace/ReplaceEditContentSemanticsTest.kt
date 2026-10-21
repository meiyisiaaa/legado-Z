package io.legado.app.ui.replace

import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.replace.edit.ReplaceEditContent
import io.legado.app.ui.replace.edit.ReplaceEditDraft
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class ReplaceEditContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optionsCheckboxesExposeTheirLabelsAndCheckedState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides ContextThemeWrapper(context, R.style.AppTheme_Light)) {
              LegadoTheme {
                ReplaceEditContent(
                    draft = ReplaceEditDraft(isRegex = true, scopeTitle = false, scopeContent = true),
                    onDraftChange = {},
                    onHelp = {}
                )
              }
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.use_regex)).assertIsOn()
        composeRule.onNodeWithContentDescription(context.getString(R.string.scope_title)).assertIsOff()
        composeRule.onNodeWithContentDescription(context.getString(R.string.scope_content)).assertIsOn()
    }
}
