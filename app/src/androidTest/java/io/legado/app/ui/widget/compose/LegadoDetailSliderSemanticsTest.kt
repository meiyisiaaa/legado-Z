package io.legado.app.ui.widget.compose

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LegadoDetailSliderSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun incrementAndDecrementButtonsExposeTheirBoundaryDisabledState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val value = mutableIntStateOf(0)

        composeRule.setContent {
            LegadoTheme {
                LegadoDetailSlider(
                    title = "测试值",
                    value = value.intValue,
                    max = 1,
                    onValueChange = { value.intValue = it },
                    onValueChangeFinished = {}
                )
            }
        }

        val decrement = composeRule.onNodeWithContentDescription(context.getString(R.string.reduce))
        val increment = composeRule.onNodeWithContentDescription(context.getString(R.string.plus))
        decrement.assertIsNotEnabled()
        increment.assertIsEnabled()
        increment.performClick()
        composeRule.runOnIdle { assertEquals(1, value.intValue) }
        decrement.assertIsEnabled()
        increment.assertIsNotEnabled()
    }
}
