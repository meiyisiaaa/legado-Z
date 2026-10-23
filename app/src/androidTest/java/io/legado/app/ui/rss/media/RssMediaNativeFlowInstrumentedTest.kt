package io.legado.app.ui.rss.media

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.model.rss.media.RssMediaDownloads
import io.legado.app.model.rss.media.RssMediaFixtureServer
import io.legado.app.service.RssMediaDownloadService
import io.legado.app.ui.about.AboutActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual fullscreen production dialog, real network queue and actual album output. */
class RssMediaNativeFlowInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<AboutActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: RssMediaFixtureServer
    private val ids = mutableSetOf<String>()

    @Before fun setUp() { server = RssMediaFixtureServer(instrumentation.context).apply { start() } }

    @After fun tearDown() = runBlocking {
        compose.activityRule.scenario.onActivity { activity ->
            activity.supportFragmentManager.fragments.filterIsInstance<RssMediaDialog>().forEach { it.dismissNow() }
        }
        ids.forEach { id ->
            appDb.rssMediaDownloadDao.get(id)?.outputUri?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
        }
        context.stopService(Intent(context, RssMediaDownloadService::class.java))
        withTimeout(10_000) {
            while (androidx.media3.datasource.cache.SimpleCache.isCacheFolderLocked(File(context.filesDir, "rss-media-cache"))) delay(100)
        }
        if (ids.isNotEmpty()) {
            val cache = io.legado.app.model.rss.media.RssMediaCache()
            try {
                val index = androidx.media3.exoplayer.offline.DefaultDownloadIndex(cache.database)
                ids.forEach { id ->
                    index.removeDownload(id)
                    cache.cache.keys.filter { it.startsWith("$id:") }.forEach(cache.cache::removeResource)
                    appDb.openHelper.writableDatabase.execSQL("DELETE FROM rssMediaDownloadTasks WHERE id = ?", arrayOf(id))
                }
            } finally { cache.release() }
        }
        server.stop()
    }

    @Test fun publicTasksEntryOpensTheActualTaskPanel() {
        compose.activityRule.scenario.onActivity { activity ->
            RssMediaUi.showTasks(activity)
            val panel = activity.supportFragmentManager.findFragmentByTag("rssMediaTasks")
            assertTrue(panel is RssMediaDialog)
            assertNotNull(panel?.view)
        }
        compose.onNodeWithText(context.getString(R.string.rss_media_tasks)).assertIsDisplayed()
        assertDialogFillsWidth()
    }

    @Test fun userSelectsHlsAndReceivesAnActualAlbumMp4() {
        val article = RssArticle(origin = server.baseUrl, link = "${server.baseUrl}/article", title = "林间来信 · 留住一段山野")
        val candidate = RssMediaCandidate(article, "${server.baseUrl}/hls/index.m3u8", "video",
            mimeType = "application/vnd.apple.mpegurl", thumbnail = "${server.baseUrl}/image.png", label = "溪流片段 · HLS")
        ids += candidate.id
        compose.activityRule.scenario.onActivity { RssMediaUi.show(it, listOf(article), captured = listOf(candidate)) }
        var fragmentState = "show requested"
        try {
            compose.waitUntil(20_000) {
                var shown = false
                compose.activityRule.scenario.onActivity { activity ->
                    val manager = activity.supportFragmentManager
                    val fragment = manager.findFragmentByTag("rssMedia")
                    shown = fragment?.view != null
                    fragmentState = "activity=${activity.lifecycle.currentState}, saved=${manager.isStateSaved}, fragment=${fragment?.lifecycle?.currentState}, view=${fragment?.view != null}"
                }
                shown
            }
        } catch (e: Throwable) {
            throw AssertionError("Media panel did not open: $fragmentState; requests=${server.requests.map { it.path }}; logs=${AppLog.logs.filter { it.second.contains("rssMedia") }.map { it.second + ": " + it.third?.stackTraceToString() }}", e)
        }
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 0)).assertIsNotEnabled()
        compose.onNodeWithText("溪流片段 · HLS").performClick()
        record("native-selected")
        compose.onNodeWithText(context.getString(R.string.rss_media_download_count, 1)).performClick()
        compose.waitUntil(50_000) {
            appDb.rssMediaDownloadDao.get(candidate.id)?.status in listOf(
                RssMediaDownloadTask.COMPLETED, RssMediaDownloadTask.FAILED, RssMediaDownloadTask.EXPORT_FAILED)
        }
        val result = appDb.rssMediaDownloadDao.get(candidate.id)!!
        assertEquals("Actual queue failed: ${result.error}", RssMediaDownloadTask.COMPLETED, result.status)
        val uri = Uri.parse(result.outputUri)
        assertEquals("video/mp4", context.contentResolver.getType(uri))
        assertTrue(context.contentResolver.openAssetFileDescriptor(uri, "r")!!.use { it.length > 0 })
        compose.onNodeWithText(context.getString(R.string.open)).assertIsDisplayed()
        record("native-completed")
    }

    @Test fun rejectedLiveMediaKeepsRetryAndOriginalPageReachable() = runBlocking {
        val article = RssArticle(origin = server.baseUrl, link = "${server.baseUrl}/article", title = "直播样本 · 下载边界")
        val candidate = RssMediaCandidate(article, "${server.baseUrl}/live.m3u8", "video",
            mimeType = "application/vnd.apple.mpegurl")
        ids += candidate.id
        RssMediaDownloads.enqueue(listOf(candidate))
        compose.activityRule.scenario.onActivity { RssMediaUi.showTasks(it) }
        compose.waitUntil(35_000) {
            appDb.rssMediaDownloadDao.get(candidate.id)?.status == RssMediaDownloadTask.FAILED
        }
        val task = appDb.rssMediaDownloadDao.get(candidate.id)!!
        assertTrue(task.error, task.error.orEmpty().contains("直播"))
        assertNull(task.outputUri)
        compose.onNodeWithText(context.getString(R.string.retry)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rss_media_open_article)).assertIsDisplayed()
        record("native-failed")
    }

    private fun record(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        assertDialogFillsWidth()
        instrumentation.uiAutomation.waitForIdle(600, 3_000)
        val prefix = InstrumentationRegistry.getArguments().getString("visualPrefix") ?: "regular"
        val directory = File(context.getExternalFilesDir(null), "rss-media-visual").apply { mkdirs() }
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$prefix-$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
    }

    private fun assertDialogFillsWidth() {
        compose.activityRule.scenario.onActivity { activity ->
            val view = requireNotNull(activity.supportFragmentManager.fragments.filterIsInstance<RssMediaDialog>().first().view)
            val position = IntArray(2)
            view.getLocationOnScreen(position)
            assertEquals("Media page must start at the window edge", 0, position[0])
            assertEquals("Media page must use the available phone width", activity.resources.displayMetrics.widthPixels, view.width)
        }
    }
}
