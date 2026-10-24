package io.legado.app.model.rss.media

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.iki.elonen.NanoHTTPD
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.service.RssMediaDownloadService
import io.legado.app.ui.about.AboutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RssMediaProgressInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val dao get() = appDb.rssMediaDownloadDao
    private val ids = mutableSetOf<String>()
    private val published = mutableSetOf<Uri>()
    private lateinit var activity: ActivityScenario<AboutActivity>
    private var server: NanoHTTPD? = null

    @Before fun setUp() = runBlocking {
        activity = ActivityScenario.launch(AboutActivity::class.java)
        context.stopService(Intent(context, RssMediaDownloadService::class.java))
        awaitServiceStopped()
    }

    @After fun tearDown() = runBlocking {
        context.stopService(Intent(context, RssMediaDownloadService::class.java))
        awaitServiceStopped()
        withContext(Dispatchers.IO) {
            val tasks = ids.mapNotNull(dao::get)
            tasks.mapNotNull { it.outputUri?.let(Uri::parse) }.forEach(published::add)
            published.forEach { context.contentResolver.delete(it, null, null) }
            val cache = RssMediaCache()
            try {
                val index = androidx.media3.exoplayer.offline.DefaultDownloadIndex(cache.database)
                tasks.forEach { task ->
                    RssMediaFiles.clearPartial(task)
                    cache.cache.keys.filter { it.startsWith("${task.id}:") }.forEach(cache.cache::removeResource)
                    index.removeDownload(task.id)
                    appDb.openHelper.writableDatabase.execSQL("DELETE FROM rssMediaDownloadTasks WHERE id = ?", arrayOf(task.id))
                }
            } finally {
                cache.release()
            }
        }
        server?.stop()
        activity.close()
    }

    @Test fun pollingCannotOverwriteExportOrTerminalProgress() = runBlocking {
        withContext(Dispatchers.IO) {
            val task = task()
            dao.upsert(task)
            dao.setProgress(task.id, 16, 25)
            assertEquals(16L, dao.get(task.id)!!.bytesDownloaded)
            assertEquals(1, dao.beginExport(task.id, 25, 25))
            dao.setProgress(task.id, 16, -1)
            assertProgress(task.id, RssMediaDownloadTask.EXPORTING, 25)
            assertEquals(1, dao.completeExport(task.id, "content://fixture/output", 30))
            dao.setProgress(task.id, 16, -1)
            assertProgress(task.id, RssMediaDownloadTask.COMPLETED, 30)
            listOf(RssMediaDownloadTask.PAUSED, RssMediaDownloadTask.CANCELLED,
                RssMediaDownloadTask.FAILED, RssMediaDownloadTask.EXPORT_FAILED).forEach { status ->
                dao.upsert(task.copy(status = status, bytesDownloaded = 25, totalBytes = 25))
                dao.setProgress(task.id, 16, -1)
                assertProgress(task.id, status, 25)
                assertEquals(0, dao.beginExport(task.id, 99, 99))
                assertEquals(0, dao.completeExport(task.id, "content://fixture/rejected", 99))
                assertProgress(task.id, status, 25)
            }
        }
    }

    @Test fun completionUsesPublishedSizeIncludingAnExistingDuplicate() = runBlocking {
        withContext(Dispatchers.IO) {
            val task = task().copy(status = RssMediaDownloadTask.EXPORTING,
                bytesDownloaded = 16, totalBytes = -1)
            dao.upsert(task)
            val payload = instrumentation.context.assets.open("rss-media-fixture/image.png").use { it.readBytes() }
            val existing = RssMediaFiles.save(task, "image/png") { it.write(payload) }
            published += existing
            val reused = RssMediaFiles.save(task, "image/png", commit = { uri ->
                dao.completeExport(task.id, uri.toString(), RssMediaFiles.sizeOf(uri.toString())!!) > 0
            }) { error("An existing published file must be reused") }
            assertEquals(existing, reused)
            assertProgress(task.id, RssMediaDownloadTask.COMPLETED, payload.size.toLong())
            assertEquals(existing.toString(), dao.get(task.id)!!.outputUri)
            dao.setProgress(task.id, 16, -1)
            assertProgress(task.id, RssMediaDownloadTask.COMPLETED, payload.size.toLong())
        }
    }

    @Test fun observingRepairsHistoricalCompletedFileSizesBeforePublishingTasks() = runBlocking {
        val task = withContext(Dispatchers.IO) {
            val task = task()
            val uri = RssMediaFiles.save(task, "image/png") { output ->
                instrumentation.context.assets.open("rss-media-fixture/image.png").use { it.copyTo(output) }
            }
            published += uri
            task.copy(status = RssMediaDownloadTask.COMPLETED, outputUri = uri.toString(),
                bytesDownloaded = 1, totalBytes = -1).also(dao::upsert)
        }
        val observer = launch { RssMediaDownloads.observeTasks() }
        try {
            val expected = withContext(Dispatchers.IO) { RssMediaFiles.sizeOf(task.outputUri)!! }
            withTimeout(10_000) {
                RssMediaDownloads.tasks.first { tasks -> tasks.any {
                    it.id == task.id && it.bytesDownloaded == expected && it.totalBytes == expected
                } }
            }
            withContext(Dispatchers.IO) {
                assertProgress(task.id, RssMediaDownloadTask.COMPLETED, expected)
                dao.correctCompletedSize(task.id, "content://fixture/obsolete", 99)
                assertProgress(task.id, RssMediaDownloadTask.COMPLETED, expected)
            }
        } finally {
            observer.cancel()
            observer.join()
        }
    }

    @Test fun fastKnownAndUnknownLengthDownloadsPersistTheCompleteFileSize() = runBlocking {
        val payload = instrumentation.context.assets.open("rss-media-fixture/image.png").use { it.readBytes() }
        val origin = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response = if (session.uri == "/chunked.png") {
                newChunkedResponse(Response.Status.OK, "image/png", ByteArrayInputStream(payload))
            } else {
                newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(payload), payload.size.toLong())
            }
        }.also { server = it; it.start() }
        val baseUrl = "http://127.0.0.1:${origin.listeningPort}"
        val candidates = listOf("/fixed.png", "/chunked.png").map { path ->
            RssMediaCandidate(RssArticle(origin = baseUrl, link = "$baseUrl/article", title = "字节进度真实验证"),
                baseUrl + path, "image", "image/png").also { ids += it.id }
        }
        RssMediaDownloads.enqueue(candidates)
        withTimeout(35_000) {
            candidates.forEach { candidate ->
                while (true) {
                    val task = withContext(Dispatchers.IO) { dao.get(candidate.id) }
                    if (task?.status == RssMediaDownloadTask.COMPLETED) {
                        withContext(Dispatchers.IO) {
                            assertEquals(payload.size.toLong(), RssMediaFiles.sizeOf(task.outputUri))
                            assertProgress(task.id, RssMediaDownloadTask.COMPLETED, payload.size.toLong())
                        }
                        break
                    }
                    if (task?.status in listOf(RssMediaDownloadTask.FAILED, RssMediaDownloadTask.EXPORT_FAILED)) {
                        fail("Download reached ${task?.status}: ${task?.error}")
                    }
                    delay(50)
                }
            }
        }
    }

    @Test fun adaptiveCompletionUsesTheRemuxedFileSize() = runBlocking {
        val origin = RssMediaFixtureServer(instrumentation.context).also { server = it; it.start() }
        val candidate = RssMediaCandidate(
            RssArticle(origin = origin.baseUrl, link = "${origin.baseUrl}/article", title = "字节进度真实验证"),
            "${origin.baseUrl}/hls/index.m3u8", "video", "application/vnd.apple.mpegurl")
            .also { ids += it.id }
        val networkPayloadBytes = instrumentation.context.assets.list("rss-media-fixture/hls")!!
            .filter { it.endsWith(".ts") }.sumOf { name ->
                instrumentation.context.assets.open("rss-media-fixture/hls/$name").use { it.readBytes().size.toLong() }
            }
        RssMediaDownloads.enqueue(listOf(candidate))
        withTimeout(35_000) {
            while (true) {
                val task = withContext(Dispatchers.IO) { dao.get(candidate.id) }
                if (task?.status == RssMediaDownloadTask.COMPLETED) {
                    withContext(Dispatchers.IO) {
                        val outputBytes = RssMediaFiles.sizeOf(task.outputUri)!!
                        assertTrue("Remuxing must exercise a different output and network payload size", outputBytes != networkPayloadBytes)
                        assertProgress(task.id, RssMediaDownloadTask.COMPLETED, outputBytes)
                    }
                    break
                }
                if (task?.status in listOf(RssMediaDownloadTask.FAILED, RssMediaDownloadTask.EXPORT_FAILED)) {
                    fail("Download reached ${task?.status}: ${task?.error}")
                }
                delay(50)
            }
        }
    }

    private fun task() = RssMediaDownloadTask(id = "progress-${UUID.randomUUID()}", origin = "fixture",
        link = "fixture/article", title = "字节进度真实验证", url = "https://example.org/image.png", kind = "image")
        .also { ids += it.id }

    private fun assertProgress(id: String, status: String, bytes: Long) {
        val actual = dao.get(id)!!
        assertEquals(status, actual.status)
        assertEquals(bytes, actual.bytesDownloaded)
        assertEquals(bytes, actual.totalBytes)
    }

    private suspend fun awaitServiceStopped() = withTimeout(10_000) {
        while (androidx.media3.datasource.cache.SimpleCache.isCacheFolderLocked(
                java.io.File(context.filesDir, "rss-media-cache"))) delay(100)
    }
}
