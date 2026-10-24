package io.legado.app.model.rss.media

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.data.appDb
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.Executors

/** Exercises the real Media3 cache, Transformer, codecs and MediaStore, with the origin offline at export. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RssMediaExportInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: RssMediaFixtureServer
    private lateinit var cache: RssMediaCache
    private val executor = Executors.newFixedThreadPool(2)
    private val published = mutableListOf<Uri>()

    @Before fun setUp() = runBlocking {
        server = RssMediaFixtureServer(instrumentation.context).apply { start() }
        cache = withContext(Dispatchers.IO) { RssMediaCache() }
    }

    @After fun tearDown() {
        server.stop()
        published.forEach { context.contentResolver.delete(it, null, null) }
        cache.release()
        executor.shutdownNow()
    }

    @Test fun pngPublishesOriginalImageOffline() = runBlocking {
        val uri = downloadThenExport("/image.png", "image/png", "image")
        val bitmap = context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertNotNull(bitmap)
        assertEquals(480, bitmap!!.width)
        assertEquals(320, bitmap.height)
        bitmap.recycle()
    }

    @Test fun chineseArticleTitlesStayWithinTheFilesystemByteLimit() {
        val task = task("/image.png", "image/png", "image").copy(title = "书".repeat(150))
        val fileName = RssMediaFiles.fileName(task, "image/png")
        assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(fileName.endsWith(".png"))
    }

    @Test fun progressiveMp4PublishesPlayableVideoOffline() = runBlocking {
        assertVideo(downloadThenExport("/sample.mp4", "video/mp4"))
    }

    @Test fun transportStreamHlsExportsWithAudioOffline() = runBlocking {
        assertVideo(downloadThenExport("/hls/index.m3u8", MimeTypes.APPLICATION_M3U8))
    }

    @Test fun fragmentedMp4HlsExportsWithAudioOffline() = runBlocking {
        assertVideo(downloadThenExport("/fmp4/index.m3u8", MimeTypes.APPLICATION_M3U8))
    }

    @Test fun separateHlsAudioAndVideoAreCombinedOffline() = runBlocking {
        assertVideo(downloadThenExport("/master.m3u8", MimeTypes.APPLICATION_M3U8))
    }

    @Test fun aes128HlsDownloadsItsKeyAndExportsOffline() = runBlocking {
        assertVideo(downloadThenExport("/aes/index.m3u8", MimeTypes.APPLICATION_M3U8))
        assertTrue(server.requests.any { it.path == "/aes/key.bin" })
    }

    @Test fun dashExportsBothTracksOffline() = runBlocking {
        assertVideo(downloadThenExport("/dash/index.mpd", MimeTypes.APPLICATION_MPD))
    }

    @Test fun authenticatedSegmentsReceiveRequiredHeaders() = runBlocking {
        assertVideo(downloadThenExport("/protected/hls/index.m3u8", MimeTypes.APPLICATION_M3U8,
            headers = mapOf("X-Fixture-Token" to "public-test-token", "Referer" to "${server.baseUrl}/article")))
        val segments = server.requests.filter { it.path.endsWith(".ts") }
        assertTrue(segments.isNotEmpty())
        assertTrue(segments.all { it.headers["x-fixture-token"] == "public-test-token" })
    }

    @Test fun cancelledGalleryWriteLeavesNoPendingMedia() = runBlocking {
        val task = task("/image.png", "image/png", "image")
        val name = RssMediaFiles.fileName(task, "image/png")
        try {
            RssMediaFiles.save(task, "image/png") { output ->
                output.write(byteArrayOf(1, 2, 3))
                throw java.io.IOException("ENOSPC (controlled fixture)")
            }
            fail("Expected the controlled storage failure")
        } catch (expected: java.io.IOException) {
            assertEquals("ENOSPC (controlled fixture)", expected.message)
        }
        context.contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(android.provider.MediaStore.MediaColumns._ID),
            "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)!!.use {
            assertEquals("A failed write must not leave a gallery item", 0, it.count)
        }
    }

    @Test fun cancellationAfterPublishingRemovesOnlyTheNewFile() = runBlocking {
        withContext(Dispatchers.IO) {
            val task = task("/image.png", "image/png", "image").copy(status = RssMediaDownloadTask.EXPORTING)
            appDb.rssMediaDownloadDao.upsert(task)
            var rejected: Uri? = null
            try {
                try {
                    RssMediaFiles.save(task, "image/png", commit = { uri ->
                        rejected = uri
                        assertTrue("The synchronization point must follow publication", RssMediaFiles.exists(uri.toString()))
                        appDb.rssMediaDownloadDao.requestStatus(task.id, RssMediaDownloadTask.CANCELLED, RssMediaDownloadTask.EXPORTING)
                        appDb.rssMediaDownloadDao.completeExport(task.id, uri.toString(), RssMediaFiles.sizeOf(uri.toString())!!) > 0
                    }) { output ->
                        instrumentation.context.assets.open("rss-media-fixture/image.png").use { it.copyTo(output) }
                    }
                    fail("Cancellation must reject ownership of the new file")
                } catch (_: CancellationException) {
                    assertNotNull(rejected)
                    assertFalse(RssMediaFiles.exists(rejected.toString()))
                    assertNull(appDb.rssMediaDownloadDao.get(task.id)!!.outputUri)
                }
                val existing = RssMediaFiles.save(task, "image/png") { output ->
                    instrumentation.context.assets.open("rss-media-fixture/image.png").use { it.copyTo(output) }
                }
                published += existing
                try {
                    RssMediaFiles.save(task, "image/png", commit = { false }) { error("Existing files must be reused") }
                    fail("The cancelled task must reject binding an existing duplicate")
                } catch (_: CancellationException) {
                    assertTrue("An existing duplicate must be preserved", RssMediaFiles.exists(existing.toString()))
                }
            } finally {
                appDb.openHelper.writableDatabase.execSQL("DELETE FROM rssMediaDownloadTasks WHERE id = ?", arrayOf(task.id))
            }
        }
    }

    @Test fun cancellationInsideCommitPreservesOnlyAcceptedGalleryFiles() = runBlocking {
        withContext(Dispatchers.IO) {
            listOf(true, false).forEach { accepted ->
                val task = task("/image.png", "image/png", "image").copy(status = RssMediaDownloadTask.EXPORTING)
                appDb.rssMediaDownloadDao.upsert(task)
                var result: Uri? = null
                try {
                    val writer = launch {
                        val caller = currentCoroutineContext().job
                        RssMediaFiles.save(task, "image/png", commit = { uri ->
                            result = uri
                            caller.cancel()
                            if (accepted) appDb.rssMediaDownloadDao.completeExport(task.id, uri.toString(), RssMediaFiles.sizeOf(uri.toString())!!) > 0
                            else {
                                appDb.rssMediaDownloadDao.requestStatus(task.id, RssMediaDownloadTask.CANCELLED, RssMediaDownloadTask.EXPORTING)
                                false
                            }
                        }) { output ->
                            instrumentation.context.assets.open("rss-media-fixture/image.png").use { it.copyTo(output) }
                        }
                    }
                    writer.join()
                    assertNotNull(result)
                    if (accepted) published += result!!
                    assertEquals("Only a committed file must survive caller cancellation", accepted,
                        RssMediaFiles.exists(result.toString()))
                    assertEquals(if (accepted) RssMediaDownloadTask.COMPLETED else RssMediaDownloadTask.CANCELLED,
                        appDb.rssMediaDownloadDao.get(task.id)!!.status)
                } finally {
                    appDb.openHelper.writableDatabase.execSQL("DELETE FROM rssMediaDownloadTasks WHERE id = ?", arrayOf(task.id))
                }
            }
        }
    }

    private fun task(path: String, mime: String, kind: String = "video", headers: Map<String, String> = emptyMap()) =
        RssMediaDownloadTask(id = "fixture-${UUID.randomUUID()}", origin = server.baseUrl,
            link = "${server.baseUrl}/article", title = "订阅媒体验证", url = server.baseUrl + path,
            kind = kind, mimeType = mime, headersJson = GSON.toJson(headers))

    private suspend fun downloadThenExport(path: String, mime: String, kind: String = "video",
                                          headers: Map<String, String> = emptyMap()): Uri = withContext(Dispatchers.IO) {
        val task = task(path, mime, kind, headers)
        val request = DownloadRequest.Builder(task.id, Uri.parse(task.url)).setMimeType(mime).build()
        val downloader = DefaultDownloaderFactory(cache.factory(task), executor).createDownloader(request)
        try {
            downloader.download { _, _, _ -> }
            assertTrue("The fixture must actually be downloaded", server.requests.isNotEmpty())
            val networkRequests = server.requests.size
            server.stop()
            val uri = Uri.parse(RssMediaExporter(cache).export(task, request))
            published += uri
            assertEquals("Export must use cached bytes only", networkRequests, server.requests.size)
            assertTrue(RssMediaFiles.exists(uri.toString()))
            uri
        } finally {
            downloader.remove()
        }
    }

    private fun assertVideo(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Expected the complete six-second clip, got $duration ms", duration in 5_500L..6_500L)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val frame = retriever.getFrameAtTime(3_000_000)
            assertNotNull("The published file must decode an actual frame", frame)
            assertEquals(320, frame!!.width)
            assertEquals(180, frame.height)
            frame.recycle()
        } finally { retriever.release() }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val videoTrack = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")
            }
            assertTrue((0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")
            })
            extractor.selectTrack(videoTrack)
            extractor.seekTo(4_000_000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            assertTrue("The MP4 must support seeking", extractor.sampleTime >= 2_000_000)
            assertTrue(extractor.readSampleData(ByteBuffer.allocate(512 * 1024), 0) > 0)
        } finally { extractor.release() }
    }
}
