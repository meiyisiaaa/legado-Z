package io.legado.app.ui.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.ui.ai.AiChatBottomSheet
import io.legado.app.ui.ai.AiChatContent
import io.legado.app.ui.ai.AiChatItem
import io.legado.app.ui.ai.AiChatRole
import io.legado.app.ui.book.manga.MangaLoadingContent
import io.legado.app.ui.book.manga.MangaLoadingUiState
import io.legado.app.ui.login.SourceLoginContent
import io.legado.app.ui.login.SourceLoginField
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemainingPanelAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun assistantCopyActionWorksAndUserMessageHasNoEmptyClickOrCopy() {
        val question = "Which part explains the result?"
        val answer = "The final paragraph explains it."
        val copyLabel = context.getString(R.string.copy_text)
        var copiedText: String? = null

        composeRule.setContent {
            LegadoTheme {
                AiChatContent(
                    mode = AiChatBottomSheet.MODE_QA,
                    bookmark = Bookmark(bookText = "Selected passage"),
                    items = listOf(
                        AiChatItem(1, AiChatRole.USER, question),
                        AiChatItem(2, AiChatRole.ASSISTANT, answer)
                    ),
                    loading = false,
                    saving = false,
                    lastSavedItemId = null,
                    onSend = { true },
                    onStop = {},
                    onRetry = {},
                    onClear = {},
                    onOpenSettings = {},
                    onSaveNote = {},
                    onCopy = { copiedText = it },
                    onImageLongClick = {}
                )
            }
        }

        val customActions = composeRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsActions.CustomActions] }
        val copyAction = customActions.single { it.label == copyLabel }
        assertTrue(copyAction.action())
        assertEquals(answer, copiedText)

        val userMessage = composeRule.onNodeWithText(question, useUnmergedTree = true)
            .performScrollTo()
            .fetchSemanticsNode()
        assertFalse(userMessage.config.contains(SemanticsActions.OnClick))
        assertFalse(userMessage.config.contains(SemanticsActions.CustomActions))
    }

    @Test
    fun mangaLoadingAndFailureAnnounceAndRetry() {
        var state by mutableStateOf(MangaLoadingUiState())
        var retryCount = 0
        val loadingLabel = context.getString(R.string.loading)
        val retryLabel = context.getString(R.string.retry)
        val failure = "Could not load this chapter"

        composeRule.setContent {
            LegadoTheme {
                MangaLoadingContent(state) { retryCount++ }
            }
        }

        val politeLiveRegion = SemanticsMatcher.expectValue(
            SemanticsProperties.LiveRegion,
            LiveRegionMode.Polite
        )
        composeRule.onNodeWithText(loadingLabel)
            .assertIsDisplayed()
            .assert(politeLiveRegion)

        composeRule.runOnIdle {
            state = MangaLoadingUiState(
                loading = false,
                showProgress = false,
                message = failure,
                showRetry = true
            )
        }
        composeRule.onNodeWithText(failure)
            .assertIsDisplayed()
            .assert(politeLiveRegion)
        composeRule.onNodeWithText(retryLabel).performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun sourceLoginFieldErrorIsAnnouncedAndClearedAfterInput() {
        val fieldError = "Enter an account name"
        val toolbar = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (56 * context.resources.displayMetrics.density).toInt()
            )
        }
        var field by mutableStateOf(
            SourceLoginField(
                key = "account",
                label = "Account",
                type = RowUi.Type.text,
                error = fieldError,
                isV2 = true
            )
        )
        var value by mutableStateOf("")

        composeRule.setContent {
            LegadoTheme {
                SourceLoginContent(
                    toolbar = toolbar,
                    fields = listOf(field),
                    values = mapOf(field.key to value),
                    globalError = null,
                    onValueChange = { changedField, newValue ->
                        value = newValue
                        field = changedField.copy(error = null)
                    },
                    onSelect = { _, _ -> },
                    onToggle = { _, _ -> },
                    onAction = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText(fieldError)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite
                )
            )
        composeRule.onNode(hasSetTextAction()).performTextInput("reader")
        composeRule.onNodeWithText(fieldError).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals("reader", value)
            assertNull(field.error)
        }
    }
}
