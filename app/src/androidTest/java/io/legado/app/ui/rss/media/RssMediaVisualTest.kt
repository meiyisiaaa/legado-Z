package io.legado.app.ui.rss.media

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.help.config.AppConfig
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Production selection UI with deterministic local images and no database/network writes. */
class RssMediaVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = instrumentation.targetContext.let { target ->
        val dark = AppConfig.isNightTheme && !AppConfig.isEInkMode
        val localized = target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
        ContextThemeWrapper(localized, if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
    }
    private val article get() = RssArticle(origin = "visual://media", sort = "1",
        title = "沿着漫长的海岸线寻找仍在写信的灯塔，以及那些未曾寄出的信与旅途中收藏的风景",
        link = "https://example.test/letters/1")
    private val candidates get(): List<RssMediaCandidate> {
        val entry = article
        val localImage = "android.resource://${context.packageName}/${R.drawable.image_rss_article}"
        return listOf(
            RssMediaCandidate(entry, "https://example.test/coast.jpg", "image", "image/jpeg",
                thumbnail = localImage, size = 2_400_000, label = "海岸线的黄昏，以及山与海之间缓缓驶过的列车"),
            RssMediaCandidate(entry, "https://example.test/lighthouse.mp4", "video", "video/mp4",
                thumbnail = localImage, size = 48_600_000, label = "灯塔的第一束光 · 1080p"),
            RssMediaCandidate(entry.copy(title = "山野观察手记", link = "https://example.test/letters/2"),
                "https://example.test/mountains.jpg", "image", "image/jpeg",
                thumbnail = localImage, label = "雨后的山野")
        )
    }

    private fun render(entries: List<RssMediaCandidate>, error: String? = null,
        onRetry: () -> Unit = {}, onSubmit: (List<RssMediaCandidate>) -> Unit = {},
        onOpen: (RssArticle) -> Unit = {}) {
        val localized = context
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized,
                LocalDensity provides Density(density.density,
                    arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                LegadoTheme {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
                        LegadoTopAppBar(title = { Text(localized.getString(R.string.rss_media_download)) },
                            navigationIcon = { IconButton(onClick = {}) {
                                Icon(painterResource(R.drawable.ic_arrow_back), localized.getString(R.string.back))
                            } }, actions = { IconButton(onClick = {}) {
                                Icon(painterResource(R.drawable.ic_download_line), localized.getString(R.string.rss_media_tasks))
                            } })
                        RssMediaSelectionContent(entries, false, false, error,
                            onRetry, {}, onOpen, onSubmit, modifier = Modifier.weight(1f),
                            articles = entries.map { it.article }.distinct().ifEmpty { listOf(article) })
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
        val directory = File(context.getExternalFilesDir(null), "rss-media-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun normalSelectionKeepsDownloadReachableWithLongTitles() {
        val entries = candidates
        var submitted = emptyList<RssMediaCandidate>()
        render(entries, onSubmit = { submitted = it })
        compose.onNodeWithText(context.getString(R.string.rss_media_open_play)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.rss_media_open_detect)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 0))
            .assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.select_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, entries.size))
            .assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(entries, submitted)
        record("normal")
    }

    @Test fun emptyStateKeepsContextualRecoveryAndDisabledDownloadVisible() {
        var opened: RssArticle? = null
        render(emptyList(), onOpen = { opened = it })
        compose.onNodeWithText(context.getString(R.string.rss_media_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rss_media_open_detect)).assertIsDisplayed().performClick()
        assertEquals(article, opened)
        compose.onNodeWithText(context.getString(R.string.rss_media_sniff)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 0))
            .assertIsDisplayed().assertIsNotEnabled()
        record("empty")
    }

    @Test fun errorStateKeepsRetryAndDownloadReachable() {
        var retries = 0
        render(candidates.take(1), error = "加载媒体失败，请检查网络后重试。", onRetry = { retries++ })
        compose.onNodeWithText(context.getString(R.string.retry)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, retries)
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 0))
            .assertIsDisplayed().assertIsNotEnabled()
        record("error")
    }
}
