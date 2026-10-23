package io.legado.app.ui.rss.article

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssStar
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Fixed local images exercise the production loader without network or database writes. */
class RssArticleVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext.let { target ->
        val dark = AppConfig.isNightTheme && !AppConfig.isEInkMode
        val localizedContext = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        ContextThemeWrapper(localizedContext, if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
    }
    private val articles get() = listOf(
        RssArticle(origin = "visual://letters", sort = "1", link = "visual://letters/1",
            title = "沿着海岸线，寻找一座仍在写信的灯塔", pubDate = "2026-10-23",
            image = "android.resource://${context.packageName}/${R.drawable.image_rss_article}"),
        RssArticle(origin = "visual://letters", sort = "1", link = "visual://letters/2",
            title = "在漫长的旅途中寻找一座没有名字的图书馆，以及那些未曾寄出的信",
            pubDate = "2026-10-22", read = true),
        RssArticle(origin = "visual://letters", sort = "1", link = "visual://letters/3",
            title = "山野观察手记", image = "android.resource://${context.packageName}/${R.drawable.image_rss_article}"),
        RssArticle(origin = "visual://letters", sort = "1", link = "visual://letters/4",
            title = "纸间拾光", pubDate = "2026-10-20")
    )

    private fun render(title: Int = R.string.rss, width: Dp = 10_000.dp,
                       fontScale: Float? = null, content: @Composable () -> Unit) {
        val localizedContext = context
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localizedContext,
                LocalDensity provides Density(density.density, fontScale
                    ?: arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                LegadoTheme {
                    assertTrue("正文与surface对比度需至少 4.5:1", ColorUtils.calculateContrast(
                        MaterialTheme.colorScheme.onSurface.toArgb(), MaterialTheme.colorScheme.surface.toArgb()) >= 4.5)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = width).fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)) {
                            LegadoTopAppBar(title = { Text(localizedContext.getString(title)) })
                            Box(Modifier.weight(1f)) { content() }
                        }
                    }
                }
            }
        }
    }

    private fun record(state: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(3_500, 10_000)
        val directory = File(context.getExternalFilesDir(null), "rss-articles-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun allFiveStylesKeepArticleClick() {
        val entries = articles
        var style by mutableStateOf(0)
        var selected: RssArticle? = null
        render {
            RssArticleListContent(entries, style, false, false, false, null,
                {}, {}, { selected = it }, false)
        }
        for (nextStyle in 0..4) {
            compose.runOnIdle { style = nextStyle; selected = null }
            compose.onNodeWithText(entries.first().title).assertIsDisplayed().performClick()
            assertEquals(entries.first(), selected)
            record("style-$nextStyle")
        }
    }

    @Test fun narrowArticleWithLargeType() {
        val entries = articles
        render(width = 320.dp, fontScale = 2f) {
            RssArticleListContent(entries, 0, false, false, false, null,
                {}, {}, {}, false)
        }
        compose.onNodeWithText(entries.first().title).assertIsDisplayed()
        record("narrow-large-type")
    }

    @Test fun favoritesKeepReadAndLongPressDelete() {
        val entries = articles.map { it.toStar() }
        var selected: RssStar? = null
        var deleted: RssStar? = null
        render(title = R.string.favorites) {
            RssFavoriteListContent(entries, { selected = it }, { deleted = it })
        }
        compose.onNodeWithText(entries.first().title).performClick()
        assertEquals(entries.first(), selected)
        compose.onNodeWithText(entries.first().title)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        assertEquals(entries.first(), deleted)
        record("favorites")
    }

    @Test fun emptyFavorites() {
        render(title = R.string.favorites) { RssFavoriteListContent(emptyList(), {}, {}) }
        compose.onNodeWithText(context.getString(R.string.rss_favorites_empty_title)).assertIsDisplayed()
        record("favorites-empty")
    }

    @Test fun paginationRetryForcesLoad() {
        val entries = articles.take(1)
        val calls = mutableListOf<Boolean>()
        render {
            RssArticleListContent(entries, 0, false, false, false, "网络错误",
                {}, { calls += it }, {}, false)
        }
        compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().performClick()
        assertEquals(listOf(true), calls)
        record("pagination-error")
    }
}
