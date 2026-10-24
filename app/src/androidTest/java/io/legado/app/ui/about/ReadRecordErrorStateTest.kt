package io.legado.app.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReadRecordErrorStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadFailureShowsErrorAndRetryAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var retried = false
        compose.setContent {
            LegadoTheme {
                ReadRecordContent(
                    records = emptyList(),
                    totalReadingTime = "0秒",
                    loading = false,
                    loadError = true,
                    onRetry = { retried = true },
                    onClearAll = {},
                    onOpen = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.error_get_data)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}
