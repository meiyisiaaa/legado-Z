package io.legado.app.model.rss.media

import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.service.rssMediaNotificationProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssMediaSignaturesTest {
    @Test fun preparationFailuresUseSpecificSafeReasons() {
        assertEquals("实时直播流暂不支持保存，请选择已结束的点播媒体",
            rssMediaPreparationError(androidx.media3.exoplayer.offline.DownloadHelper.LiveContentUnsupportedException()))
        assertEquals("DRM 加密保护媒体暂不支持保存",
            rssMediaPreparationError(UnsupportedRssMediaException("DRM 加密保护媒体暂不支持保存")))
        val external = java.io.IOException("https://private.example/media?token=secret")
        assertEquals("媒体读取失败，请检查网络或登录状态后重试", rssMediaPreparationError(external))
        try {
            RssMediaSignatures.responseMime("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"".toByteArray(), null, "video")
            org.junit.Assert.fail("Expected the explicit SAMPLE-AES reason")
        } catch (error: UnsupportedRssMediaException) {
            assertEquals("SAMPLE-AES 加密媒体暂不支持保存", rssMediaPreparationError(error))
        }
    }
    @Test fun hiddenPlaylistIsDetectedFromSmallResponsePrefix() {
        val task = RssMediaDownloadTask("id", "origin", "link", "title", "https://example.test/hls/playlist", "video")
        assertEquals(true, RssMediaCache.requiresProbe(task))
        assertEquals(false, RssMediaCache.requiresProbe(task.copy(url = "https://example.test/a.mp4")))
        assertEquals("application/x-mpegurl", RssMediaSignatures.responseMime("#EXTM3U\n#EXT-X-VERSION:3".toByteArray(), null, "video"))
        assertEquals("application/dash+xml", RssMediaSignatures.responseMime("<?xml version=\"1.0\"?><MPD xmlns=\"urn:mpeg\">".toByteArray(), "application/octet-stream", "video"))
    }
    @Test fun notificationUsesRealBytesAndKeepsExportIndeterminate() {
        val task = RssMediaDownloadTask("id", "origin", "link", "title", "https://example.test/file.mp4", "video",
            status = RssMediaDownloadTask.DOWNLOADING, bytesDownloaded = 25, totalBytes = 100)
        assertEquals(250, rssMediaNotificationProgress(listOf(task)))
        assertNull(rssMediaNotificationProgress(listOf(task.copy(totalBytes = -1))))
        assertNull(rssMediaNotificationProgress(listOf(task.copy(status = RssMediaDownloadTask.EXPORTING))))
        assertNull(rssMediaNotificationProgress(emptyList()))
    }
    @Test(expected = InvalidRssMediaContentException::class)
    fun disguisedHtmlProducesAnInvalidContentFailure() {
        RssMediaSignatures.requireMimeType("<!doctype html><form>Login</form>".toByteArray(), "video")
    }
    @Test fun retryKeepsRequestContextAndRegeneratesCredentials() {
        val previous = mapOf(
            "Cookie" to "session=expired", "authorization" to "Bearer expired",
            "Proxy-Authorization" to "old-proxy", "X-Api-Key" to "expired-key",
            "Referer" to "https://site.example/post", "Accept" to "video/*",
        )
        assertEquals(mapOf("Referer" to "https://site.example/post", "Accept" to "video/*"),
            RssMediaDownloads.retryHeaders(previous))
    }
    @Test fun hiddenPlaylistUrlsUseCaseInsensitiveMimeTypes() {
        val task = RssMediaDownloadTask("id", "origin", "link", "title", "https://example.test/play?id=1", "video")
        listOf("application/x-mpegURL", "application/x-mpegurl; charset=UTF-8", "audio/x-mpegurl").forEach {
            assertEquals("application/x-mpegURL", RssMediaCache.adaptiveMime(task.copy(mimeType = it)))
        }
        assertEquals("application/dash+xml", RssMediaCache.adaptiveMime(task.copy(mimeType = "Application/Dash+XML")))
    }
    @Test fun rejectsLoginPagesAndEmptyResponses() {
        assertNull(RssMediaSignatures.mimeType("<!doctype html><form>Login</form>".toByteArray(), "image"))
        assertNull(RssMediaSignatures.mimeType("{\"error\":\"unauthorized\"}".toByteArray(), "video"))
        assertNull(RssMediaSignatures.mimeType(byteArrayOf(), "video"))
    }

    @Test fun detectsContentWithoutTrustingUrlSuffix() {
        assertEquals("image/png", RssMediaSignatures.mimeType(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), "image"))
        assertEquals("image/webp", RssMediaSignatures.mimeType("RIFFxxxxWEBP".toByteArray(), "image"))
        assertEquals("video/mp4", RssMediaSignatures.mimeType("xxxxftypisom".toByteArray(), "video"))
        assertNull(RssMediaSignatures.mimeType("xxxxftypisom".toByteArray(), "image"))
    }

    @Test fun credentialsRequireSameSchemeHostAndPort() {
        assertEquals(true, RssMediaCache.sameCredentialOrigin("https://media.example/a", "https://media.example/b"))
        assertEquals(false, RssMediaCache.sameCredentialOrigin("https://media.example/a", "https://cdn.example/b"))
        assertEquals(false, RssMediaCache.sameCredentialOrigin("https://media.example/a", "http://media.example/b"))
        assertEquals(false, RssMediaCache.sameCredentialOrigin("https://media.example/a", "https://media.example:8443/b"))
    }
}
