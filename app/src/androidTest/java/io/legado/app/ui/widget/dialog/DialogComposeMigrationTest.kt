package io.legado.app.ui.widget.dialog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialogContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class DialogComposeMigrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun urlOptionMethodSuggestionIsReturnedWithTheEditedForm() {
        var saved: UrlOptionValues? = null
        var values by mutableStateOf(UrlOptionValues())
        composeRule.setContent {
            LegadoTheme {
                UrlOptionDialogContent(
                    charsets = listOf("UTF-8"),
                    values = values,
                    onValuesChange = { values = it },
                    onDismiss = {},
                    onSave = { saved = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("method suggestions").performClick()
        composeRule.onNodeWithText("POST").performClick()
        composeRule.onNodeWithText(context.getString(R.string.ok)).performClick()

        assertEquals("POST", saved?.method)
    }

    @Test
    fun plainTextDialogContentIsSelectableComposeText() {
        composeRule.setContent {
            LegadoTheme {
                TextDialogContent(
                    title = "Source details",
                    content = "Plain body text",
                    mode = TextDialog.Mode.TEXT.name,
                    badgeSeconds = null,
                    onClose = {},
                    onEdit = {},
                    onImageLongClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Plain body text").assertIsDisplayed()
    }

    @Test
    fun numberPickerKeepsConfiguredRangeAndReturnsTheSelectedValue() {
        var picker: android.widget.NumberPicker? = null
        var selected: Int? = null
        composeRule.setContent {
            LegadoTheme {
                NumberPickerDialogContent(
                    title = "Chapter",
                    minValue = 2,
                    maxValue = 9,
                    initialValue = 4,
                    decimalMode = false,
                    customButtonText = null,
                    onPickerReady = { picker = it },
                    onCustom = {},
                    onCancel = {},
                    onConfirm = { selected = picker?.value }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNotNull(picker)
            assertEquals(2, picker?.minValue)
            assertEquals(9, picker?.maxValue)
            picker?.value = 7
        }
        composeRule.onNodeWithText(context.getString(R.string.ok)).performClick()

        assertEquals(7, selected)
    }
}
