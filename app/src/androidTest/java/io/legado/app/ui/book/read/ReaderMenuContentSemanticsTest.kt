package io.legado.app.ui.book.read

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderMenuContentSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun automaticBrightnessDescriptionReportsItsEnabledState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val enabledDescription = context.getString(
            R.string.brightness_auto,
            context.getString(R.string.enabled)
        )
        val disabledDescription = context.getString(
            R.string.brightness_auto,
            context.getString(R.string.disabled)
        )
        var brightnessAuto by mutableStateOf(true)
        var toggledAutoMode: Boolean? = null

        compose.setContent {
            LegadoTheme {
                ReadMenuContent(
                    state = ReadMenuUiState(
                        brightnessAuto = brightnessAuto,
                        brightnessEnabled = !brightnessAuto,
                        animate = false
                    ),
                    visible = true,
                    onAction = { action, _ ->
                        if (action == ReaderMenuAction.ToggleBrightnessAuto) {
                            brightnessAuto = !brightnessAuto
                            toggledAutoMode = brightnessAuto
                        }
                    },
                    onBrightnessChange = {},
                    onBrightnessFinished = {},
                    onClosed = {},
                    onHelp = {},
                    onOpenUrlPreference = {}
                )
            }
        }

        compose.onNodeWithContentDescription(enabledDescription).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(disabledDescription).assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, toggledAutoMode) }
    }
}
