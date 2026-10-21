package io.legado.app.ui.book.cache

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
import io.legado.app.data.entities.Book
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CacheScreenSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyCacheLoadingAndFailureExposeAccessibleFeedbackAndRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loading = mutableStateOf(true)
        val loadError = mutableStateOf<String?>(null)
        val books = mutableStateOf(emptyList<Book>())
        var retryCount = 0

        composeRule.setContent {
            LegadoTheme {
                CacheScreen(
                    books = books.value,
                    cacheChapters = emptyMap(),
                    revision = 0,
                    isLoading = loading.value,
                    onDownload = {},
                    onExport = {},
                    exportProgress = { null },
                    exportMessage = { null },
                    loadError = loadError.value,
                    onRetry = { retryCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.runOnIdle {
            loading.value = false
            loadError.value = "读取失败"
        }
        composeRule.onNodeWithText("读取失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.runOnIdle {
            books.value = listOf(Book(bookUrl = "ui-test://cached", name = "已缓存书籍"))
            loading.value = true
            loadError.value = null
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.runOnIdle {
            loading.value = true
            loadError.value = "读取失败"
        }
        composeRule.onNodeWithText("读取失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.onNodeWithText(context.getString(R.string.retry)).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }
}
