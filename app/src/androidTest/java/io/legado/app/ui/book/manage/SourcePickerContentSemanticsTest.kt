package io.legado.app.ui.book.manage

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SourcePickerContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingErrorAndEmptyStatesAreExposed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loading = mutableStateOf(true)
        val hasError = mutableStateOf(false)
        var retries = 0

        composeRule.setContent {
            LegadoTheme {
                SourcePickerContent(
                    sources = emptyList(),
                    query = "",
                    isLoading = loading.value,
                    hasError = hasError.value,
                    onQueryChange = {},
                    onRetry = { retries++ },
                    onChangeDelay = {},
                    onSourceClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assertExists()
        composeRule.runOnIdle {
            loading.value = false
            hasError.value = true
        }
        composeRule.onNodeWithText(context.getString(R.string.error_get_data)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.error_get_data)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)
        )
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
        composeRule.runOnIdle { hasError.value = false }
        composeRule.onNodeWithText(context.getString(R.string.empty)).assertIsDisplayed()
    }
}
