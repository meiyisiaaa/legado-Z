package io.legado.app.ui.rss.article

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.runtime.mutableStateOf
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RssArticleMediaSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun backClearsSelectionAndRemovedArticlesLeaveSelectionMode() {
        val first = RssArticle(origin = "https://example.test", title = "First article", link = "https://example.test/1")
        val second = first.copy(title = "Second article", link = "https://example.test/2")
        val loaded = mutableStateOf(listOf(first, second))
        var reads = 0
        compose.setContent { LegadoTheme {
            RssArticleListContent(loaded.value, 0, false, false, false, null, {}, {},
                onRead = { reads++ }, preload = false)
        } }
        compose.onNodeWithText(first.title).performTouchInput { longClick() }
        pressBack()
        compose.onNodeWithText(first.title).performClick()
        assertEquals(1, reads)
        compose.onNodeWithText(first.title).performTouchInput { longClick() }
        compose.runOnIdle { loaded.value = listOf(second) }
        compose.onNodeWithText(second.title).performClick()
        assertEquals(2, reads)
    }

    @Test fun longPressSelectsLoadedArticlesWithoutOpeningReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = RssArticle(origin = "https://example.test", title = "First article", link = "https://example.test/1")
        val second = first.copy(title = "Second article", link = "https://example.test/2")
        var reads = 0
        var selected = emptyList<RssArticle>()
        compose.setContent { LegadoTheme {
            RssArticleListContent(listOf(first, second), 4, false, false, false, null, {}, {},
                onRead = { reads++ }, preload = false, onDownload = { selected = it })
        } }
        compose.onNodeWithText(first.title).performTouchInput { longClick() }
        compose.onNodeWithText(context.getString(R.string.select_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 2)).performClick()
        assertEquals(listOf(first, second), selected)
        assertEquals(0, reads)
    }
}
