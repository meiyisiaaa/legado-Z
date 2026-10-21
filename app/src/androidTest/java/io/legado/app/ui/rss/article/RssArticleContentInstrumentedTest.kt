package io.legado.app.ui.rss.article

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RssArticleContentInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun articleClickDispatchesSelectedArticle() {
        val article = RssArticle(
            origin = "ui-test://feed",
            sort = "1",
            title = "测试文章",
            link = "ui-test://feed/article/1"
        )
        var selected: RssArticle? = null

        composeRule.setContent {
            LegadoTheme {
                RssArticleListContent(
                    articles = listOf(article),
                    articleStyle = 0,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = false,
                    error = null,
                    onRefresh = {},
                    onLoadMore = {},
                    onRead = { selected = it },
                    preload = false
                )
            }
        }

        composeRule.onNodeWithText(article.title).performClick()

        assertEquals(article, selected)
    }

    @Test
    fun emptyStateRefreshDispatchesCallback() {
        var refreshCount = 0

        composeRule.setContent {
            LegadoTheme {
                RssArticleListContent(
                    articles = emptyList(),
                    articleStyle = 0,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = false,
                    error = null,
                    onRefresh = { refreshCount++ },
                    onLoadMore = {},
                    onRead = {},
                    preload = false
                )
            }
        }

        composeRule.onNodeWithText("暂无文章").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.refresh)).performClick()

        assertEquals(1, refreshCount)
    }

    @Test
    fun loadErrorRetryDispatchesRefreshCallback() {
        var refreshCount = 0

        composeRule.setContent {
            LegadoTheme {
                RssArticleListContent(
                    articles = emptyList(),
                    articleStyle = 0,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = false,
                    error = "网络错误",
                    onRefresh = { refreshCount++ },
                    onLoadMore = {},
                    onRead = {},
                    preload = false
                )
            }
        }

        composeRule.onNodeWithText("网络错误").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()

        assertEquals(1, refreshCount)
    }
}
