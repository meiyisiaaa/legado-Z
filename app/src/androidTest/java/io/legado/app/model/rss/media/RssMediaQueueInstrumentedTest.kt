package io.legado.app.model.rss.media

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.service.RssMediaDownloadService
import io.legado.app.ui.about.AboutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssMediaQueueInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: RssMediaFixtureServer
    private lateinit var activity: ActivityScenario<AboutActivity>
    private val ids = mutableSetOf<String>()

    @Before fun setUp() = runBlocking {
        server = RssMediaFixtureServer(instrumentation.context).apply { start() }
        activity = ActivityScenario.launch(AboutActivity::class.java)
        context.stopService(Intent(context, RssMediaDownloadService::class.java))
        awaitServiceStopped()
        withContext(Dispatchers.IO) {
            removeFixtureTasks(appDb.rssMediaDownloadDao.all().filter {
                it.title == "队列真实验证" && Uri.parse(it.origin).host == "127.0.0.1"
            })
        }
    }

    @After fun tearDown() = runBlocking {
        context.stopService(Intent(context, RssMediaDownloadService::class.java))
        awaitServiceStopped()
        withContext(Dispatchers.IO) {
            removeFixtureTasks(ids.mapNotNull { appDb.rssMediaDownloadDao.get(it) })
        }
        activity.close()
        server.stop()
    }

    @Test fun submittedMediaCompletesAndDuplicateSubmissionReusesGalleryFile() = runBlocking {
        val candidate = candidate("/image.png", "image", "image/png")
        RssMediaDownloads.enqueue(listOf(candidate))
        val completed = waitFor(candidate.id, RssMediaDownloadTask.COMPLETED)
        assertTrue(RssMediaFiles.exists(completed.outputUri))
        RssMediaDownloads.enqueue(listOf(candidate))
        val duplicate = withContext(Dispatchers.IO) { appDb.rssMediaDownloadDao.get(candidate.id)!! }
        assertEquals(completed.outputUri, duplicate.outputUri)
        assertEquals(completed.createdAt, duplicate.createdAt)
    }

    @Test fun extensionlessHlsIsClassifiedFromTheResponseAndExported() = runBlocking {
        val candidate = candidate("/hls/playlist", "video", null)
        RssMediaDownloads.enqueue(listOf(candidate))
        val completed = waitFor(candidate.id, RssMediaDownloadTask.COMPLETED)
        assertEquals("video/mp4", context.contentResolver.getType(Uri.parse(completed.outputUri)))
        assertTrue(server.requests.any { it.path.endsWith(".ts") })
    }

    @Test fun crossOriginRedirectDropsInheritedSourceCredentialsAndStillExportsVideo() = runBlocking<Unit> {
        val candidate = candidate("/credential-redirect.mp4", "video", "video/mp4")
            .copy(headers = mapOf("X-Secret" to "public-fixture-secret"))
        RssMediaDownloads.enqueue(listOf(candidate))
        val completed = waitFor(candidate.id, RssMediaDownloadTask.COMPLETED)
        val initial = server.requests.filter { it.path == "/credential-redirect.mp4" }
        val redirected = server.requests.filter { it.path == "/credential-target.mp4" }
        assertTrue(initial.isNotEmpty())
        assertTrue(initial.all { it.headers["x-secret"] == "public-fixture-secret" })
        assertTrue(redirected.isNotEmpty())
        assertTrue(redirected.all { !it.headers.containsKey("x-secret") })
        assertTrue(redirected.all { it.headers["host"] == "localhost:${server.listeningPort}" })
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(completed.outputUri))
            val frame = retriever.getFrameAtTime(1_000_000)
            assertNotNull("The redirected download must remain playable", frame)
            frame?.recycle()
        } finally {
            retriever.release()
        }
    }

    @Test fun pausedTaskSurvivesServiceRestartAndResumes() = runBlocking {
        val candidate = candidate("/sample.mp4", "video", "video/mp4")
        RssMediaDownloads.enqueue(listOf(candidate))
        awaitServiceStarted()
        RssMediaDownloads.pause(candidate.id)
        waitFor(candidate.id, RssMediaDownloadTask.PAUSED)
        context.stopService(Intent(context, RssMediaDownloadService::class.java))
        awaitServiceStopped()
        assertEquals(RssMediaDownloadTask.PAUSED, withContext(Dispatchers.IO) { appDb.rssMediaDownloadDao.get(candidate.id)!!.status })
        RssMediaDownloads.resume(candidate.id)
        assertTrue(RssMediaFiles.exists(waitFor(candidate.id, RssMediaDownloadTask.COMPLETED).outputUri))
    }

    @Test fun immediatePauseAndResumeCompletesWithoutStoppingTheService() = runBlocking {
        val candidate = candidate("/sample.mp4", "video", "video/mp4")
        RssMediaDownloads.enqueue(listOf(candidate))
        RssMediaDownloads.pause(candidate.id)
        RssMediaDownloads.resume(candidate.id)
        val completed = waitFor(candidate.id, RssMediaDownloadTask.COMPLETED)
        assertTrue(RssMediaFiles.exists(completed.outputUri))
        assertTrue(server.requests.any { it.path == "/sample.mp4" })
    }

    @Test fun cancelledSelectionNeverPublishesAFile() = runBlocking {
        val candidate = candidate("/sample.mp4", "video", "video/mp4")
        RssMediaDownloads.enqueue(listOf(candidate))
        awaitServiceStarted()
        RssMediaDownloads.pause(candidate.id)
        RssMediaDownloads.cancel(candidate.id)
        val task = waitFor(candidate.id, RssMediaDownloadTask.CANCELLED)
        assertNull(task.outputUri)
    }

    @Test fun htmlDisguisedAsMp4FailsBeforePublishing() = runBlocking {
        val candidate = candidate("/not-a-video.mp4", "video", "video/mp4")
        RssMediaDownloads.enqueue(listOf(candidate))
        val failed = waitFor(candidate.id, RssMediaDownloadTask.FAILED)
        assertNull(failed.outputUri)
        assertFalse(failed.error.isNullOrBlank())
    }

    @Test fun expiredMediaUrlOffersFailureAndCanBeRetried() = runBlocking {
        val candidate = candidate("/expired.mp4", "video", "video/mp4")
        RssMediaDownloads.enqueue(listOf(candidate))
        val failed = waitFor(candidate.id, RssMediaDownloadTask.FAILED)
        assertNull(failed.outputUri)
        val before = server.requests.size
        RssMediaDownloads.retry(candidate.id)
        withTimeout(15_000) { while (server.requests.size <= before) delay(100) }
        assertNull(waitFor(candidate.id, RssMediaDownloadTask.FAILED).outputUri)
    }

    @Test fun livePlaylistIsRejectedWithoutCreatingAnAlbumFile() = runBlocking {
        val candidate = candidate("/live.m3u8", "video", "application/vnd.apple.mpegurl")
        RssMediaDownloads.enqueue(listOf(candidate))
        val failed = waitFor(candidate.id, RssMediaDownloadTask.FAILED)
        assertNull(failed.outputUri)
        assertFalse(failed.error.isNullOrBlank())
    }

    private fun candidate(path: String, kind: String, mime: String?): RssMediaCandidate =
        RssMediaCandidate(RssArticle(origin = server.baseUrl, link = "${server.baseUrl}/article",
            title = "队列真实验证"), server.baseUrl + path, kind, mime).also { ids += it.id }

    private suspend fun waitFor(id: String, status: String): RssMediaDownloadTask = withTimeout(35_000) {
        while (true) {
            val task = withContext(Dispatchers.IO) { appDb.rssMediaDownloadDao.get(id) }
            if (task?.status == status) return@withTimeout task
            if (status == RssMediaDownloadTask.COMPLETED && task?.status in listOf(RssMediaDownloadTask.FAILED, RssMediaDownloadTask.EXPORT_FAILED)) {
                fail("Task reached ${task?.status}: ${task?.error}")
            }
            delay(100)
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }

    private suspend fun awaitServiceStopped() = withTimeout(10_000) {
        while (androidx.media3.datasource.cache.SimpleCache.isCacheFolderLocked(java.io.File(context.filesDir, "rss-media-cache"))) delay(100)
    }

    private suspend fun awaitServiceStarted() = withTimeout(10_000) {
        // The engine creates its cache only after onStartCommand has entered the foreground.
        while (!androidx.media3.datasource.cache.SimpleCache.isCacheFolderLocked(java.io.File(context.filesDir, "rss-media-cache"))) delay(100)
    }

    private fun removeFixtureTasks(tasks: List<RssMediaDownloadTask>) {
        if (tasks.isEmpty()) return
        val cache = RssMediaCache()
        try {
            val index = androidx.media3.exoplayer.offline.DefaultDownloadIndex(cache.database)
            tasks.forEach { task ->
                task.outputUri?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
                RssMediaFiles.clearPartial(task)
                cache.cache.keys.filter { it.startsWith("${task.id}:") }.forEach(cache.cache::removeResource)
                index.removeDownload(task.id)
                appDb.openHelper.writableDatabase.execSQL("DELETE FROM rssMediaDownloadTasks WHERE id = ?", arrayOf(task.id))
            }
        } finally { cache.release() }
    }
}
