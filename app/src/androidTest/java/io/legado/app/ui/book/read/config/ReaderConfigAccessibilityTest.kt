package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderConfigAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sliderAnnouncesItsLabelAndValueAndAcceptsAccessibilityAdjustment() {
        var value by mutableFloatStateOf(12f)
        compose.setContent {
            LegadoTheme {
                ReaderConfigSlider(
                    title = "页边距", value = value, valueRange = 0f..40f,
                    valueText = "${value.toInt()} dp", onValueChange = { value = it }
                )
            }
        }
        compose.onNodeWithContentDescription("页边距")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "12 dp"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }
        compose.onNodeWithContentDescription("页边距")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "24 dp"))
        compose.runOnIdle { assertEquals(24f, value, 0f) }
    }

    @Test fun choiceExposesTheSelectedOptionAndDispatchesAnotherOption() {
        var selected: Int? = null
        compose.setContent {
            LegadoTheme {
                ReaderConfigChoiceDialog(
                    title = "字体粗细", options = listOf("普通", "加粗"), selectedIndex = 1,
                    onSelect = { selected = it }, onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("加粗").assertIsSelected()
        compose.onNodeWithText("普通").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(0, selected) }
    }
}
