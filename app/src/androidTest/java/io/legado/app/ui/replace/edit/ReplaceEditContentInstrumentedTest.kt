package io.legado.app.ui.replace.edit

import android.graphics.Color
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.legado.app.R
import io.legado.app.data.entities.HttpTTS
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.view.ThemeEditText
import io.legado.app.ui.book.read.config.HttpTtsEditContent
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.text.TextInputLayout
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReplaceEditContentInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allNativeFieldsConstructAndEditingNameUpdatesDraft() {
        val initialDraft = ReplaceEditDraft(
            name = "initial-name",
            group = "initial-group",
            pattern = "initial-pattern",
            replacement = "initial-replacement",
            scope = "initial-scope",
            excludeScope = "initial-exclude",
            timeout = "3210"
        )
        val draft = mutableStateOf(initialDraft)
        composeRule.setContent {
            val baseContext = LocalContext.current
            val appContext = remember(baseContext) {
                ContextThemeWrapper(baseContext, R.style.AppTheme_Light)
            }
            CompositionLocalProvider(LocalContext provides appContext) {
                LegadoTheme {
                    ReplaceEditContent(
                        draft = draft.value,
                        onDraftChange = { draft.value = it },
                        onHelp = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        listOf(
            initialDraft.name,
            initialDraft.group,
            initialDraft.pattern,
            initialDraft.replacement,
            initialDraft.scope,
            initialDraft.excludeScope,
            initialDraft.timeout
        ).forEach { value ->
            onView(allOf(isAssignableFrom(ThemeEditText::class.java), withText(value)))
                .check(matches(withText(value)))
        }

        val updatedName = "updated-name"
        onView(allOf(isAssignableFrom(ThemeEditText::class.java), withText(initialDraft.name)))
            .perform(click(), replaceText(updatedName))
        composeRule.runOnIdle {
            assertEquals(updatedName, draft.value.name)
        }
    }

    @Test
    fun httpTtsCodeFieldConstructsNativeEditor() {
        val url = "https://tts.example.invalid/stream?text={text}"
        composeRule.setContent {
            val baseContext = LocalContext.current
            val appContext = remember(baseContext) {
                ContextThemeWrapper(baseContext, R.style.AppTheme_Light)
            }
            CompositionLocalProvider(LocalContext provides appContext) {
                LegadoTheme {
                    HttpTtsEditContent(
                        draft = HttpTTS(name = "TTS fixture", url = url),
                        menuExpanded = false,
                        unsavedDialog = false,
                        loginHeader = null,
                        showLoginHeader = false,
                        onDraftChange = {},
                        onMenuExpandedChange = {},
                        onMenuAction = {},
                        onUnsavedDialogChange = {},
                        onDiscard = {},
                        onLoginHeaderDismiss = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val urlEditor = onView(allOf(isAssignableFrom(CodeView::class.java), withText(url)))
        urlEditor.perform(click())
        urlEditor.check(matches(withText(url)))
        urlEditor.check { view, _ ->
            val editor = view as CodeView
            assertTrue(editor.hasFocus())
            var parent = editor.parent
            while (parent != null && parent !is TextInputLayout) {
                parent = parent.parent
            }
            val layout = requireNotNull(parent as? TextInputLayout)
            assertEquals("URL", layout.hint?.toString())

            val accentColor = ThemeStore.accentColor(editor.context)
            val focusedState = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused)
            val hintColors = requireNotNull(layout.defaultHintTextColor)
            val underlineColors = requireNotNull(editor.supportBackgroundTintList)
            assertEquals(accentColor, hintColors.getColorForState(focusedState, Color.TRANSPARENT))
            assertEquals(accentColor, underlineColors.getColorForState(focusedState, Color.TRANSPARENT))
        }
    }
}
