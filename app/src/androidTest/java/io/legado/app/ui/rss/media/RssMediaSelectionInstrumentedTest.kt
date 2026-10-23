package io.legado.app.ui.rss.media

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RssMediaSelectionInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun failedTaskKeepsRetryAndOriginalArticleActions() {
        var retries = 0
        var opens = 0
        compose.setContent { LegadoTheme { RssMediaTaskRecoveryActions({ retries++ }, { opens++ }) } }
        compose.onNodeWithText(context.getString(R.string.retry)).performClick()
        compose.onNodeWithText(context.getString(R.string.rss_media_open_article)).performClick()
        assertEquals(1, retries)
        assertEquals(1, opens)
    }

    @Test fun sourceCredentialsStayWithinPageSchemeHostAndPort() {
        val headers = mapOf("Authorization" to "secret", "Cookie" to "session=secret", "X-Key" to "secret",
            "Referer" to "https://source.test/article", "User-Agent" to "browser", "Accept" to "*/*")
        val page = "https://source.test/article"
        assertEquals(headers, RssMediaSniffer.sourceHeadersForRequest(headers, page, "https://source.test:443/media"))
        val publicHeaders = headers.filterKeys { it in listOf("Referer", "User-Agent", "Accept") }
        listOf("https://third-party.test/media", "http://source.test/media", "https://source.test:8443/media")
            .forEach { target -> assertEquals(publicHeaders, RssMediaSniffer.sourceHeadersForRequest(headers, page, target)) }
    }

    @Test fun actualMediaHeadersSurviveCaptureAndPrivateSnapshotWithoutStaleTransportState() {
        val article = RssArticle(origin = "https://source.test", title = "Player", link = "https://source.test/article")
        val url = "https://media.test/player.mp4"
        val headers = mapOf("Authorization" to "Bearer target-token", "X-Player-Key" to "target-key",
            "Referer" to article.link, "User-Agent" to "player-agent", "Cookie" to "stale-session=1",
            "Range" to "bytes=100-200", "Host" to "media.test", "If-None-Match" to "old-tag")
        var captured = emptyList<RssMediaCandidate>()
        RssMediaSniffer.captureRequest(article, url, headers) { captured = it }
        val expected = headers.filterKeys { it in setOf("Authorization", "X-Player-Key", "Referer", "User-Agent") }
        assertEquals(url, captured.single().url)
        assertEquals(expected, captured.single().headers)
        val snapshot = mediaSelectionSnapshot(listOf(article), null, null, captured)
        assertEquals(expected, snapshot.candidates.single().restore(snapshot.articles)?.headers)
        assertEquals(mapOf("Referer" to article.link, "User-Agent" to "player-agent"),
            RssMediaSniffer.sourceHeadersForRequest(expected, url, "https://other.test/segment"))
    }

    @Test fun savedSnapshotStoresArticleIdentityOnceAndOmitsLargeBody() {
        val article = RssArticle(origin = "https://example.test", title = "Gallery", link = "https://example.test/article",
            description = "private-large-body".repeat(20_000), content = "large-content".repeat(20_000))
        val media = (1..100).map { RssMediaCandidate(article, "https://example.test/$it.mp4", "video") }
        val snapshot = mediaSelectionSnapshot(listOf(article), null, null, media)
        val json = io.legado.app.utils.GSON.toJson(snapshot)
        assertEquals(100, snapshot.candidates.size)
        assertEquals(null, snapshot.articles.single().description)
        assertEquals(null, snapshot.articles.single().content)
        assertFalse(json.contains("private-large-body"))
        assertTrue(json.length < 60_000)
        assertEquals(article.link, snapshot.candidates.first().restore(snapshot.articles)?.article?.link)
    }

    @Test fun candidatesStartUnselectedAndSubmitOnlyExplicitSelection() {
        val article = RssArticle(origin = "https://example.test", title = "Gallery", link = "https://example.test/article")
        val video = RssMediaCandidate(article, "https://example.test/one.mp4", "video", label = "First video")
        val other = RssMediaCandidate(article, "https://example.test/two.mp4", "video", label = "Second video")
        var submitted = emptyList<RssMediaCandidate>()
        compose.setContent { LegadoTheme {
            RssMediaSelectionContent(listOf(video, other), false, false, null, {}, {}, {}, { submitted = it })
        } }
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 0)).assertIsNotEnabled()
        compose.onNodeWithText("First video").performClick()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 1)).assertIsEnabled().performClick()
        assertEquals(listOf(video), submitted)
    }

    @Test fun selectAllCanBeClearedWithoutSubmitting() {
        val article = RssArticle(origin = "https://example.test", title = "Gallery", link = "https://example.test/article")
        val video = RssMediaCandidate(article, "https://example.test/one.mp4", "video", label = "First video")
        compose.setContent { LegadoTheme {
            RssMediaSelectionContent(listOf(video), false, false, null, {}, {}, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.select_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 1)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.rss_media_deselect_all)).performClick()
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 0)).assertIsNotEnabled()
    }
}
