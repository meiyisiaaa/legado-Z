package io.legado.app.ui.book.searchContent

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchContentAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuOptionsExposeCheckedStateAndDispatchToggle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var toggled = false
        compose.setContent {
            LegadoTheme {
                SearchContentScreen(
                    state = SearchContentUiState(regexEnabled = true),
                    onQueryChange = {}, onSearch = {}, onToggleReplace = { toggled = true },
                    onToggleRegex = {}, onStop = {}, onRetry = {}, onOpenResult = { _, _ -> }, onBack = {}
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.more_menu)).performClick()
        compose.onNodeWithText(context.getString(R.string.regex)).assertIsOn()
        compose.onNodeWithText(context.getString(R.string.replace)).assertIsOff().performClick()
        compose.runOnIdle { assertTrue(toggled) }
    }
}
