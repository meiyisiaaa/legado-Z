package io.legado.app.model.rss.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RssMediaTrackSelectionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var server: RssMediaFixtureServer
    private lateinit var cache: RssMediaCache
    private val executor = Executors.newFixedThreadPool(2)
    private var published: Uri? = null

    @Before fun setUp() = runBlocking {
        server = RssMediaFixtureServer(instrumentation.context).apply { start() }
        cache = withContext(Dispatchers.IO) { RssMediaCache() }
    }

    @After fun tearDown() {
        server.stop()
        published?.let { context.contentResolver.delete(it, null, null) }
        cache.release()
        executor.shutdownNow()
    }

    @Test fun highestResolutionAndDefaultAudioAreDownloadedAndRemuxedWithoutReencoding() = runBlocking {
        val task = RssMediaDownloadTask(id = "quality-${UUID.randomUUID()}", origin = server.baseUrl,
            link = "${server.baseUrl}/quality/article", title = "原视频质量验证",
            url = "${server.baseUrl}/quality/master.m3u8", kind = "video", mimeType = MimeTypes.APPLICATION_M3U8)
        val (request, formats) = prepare(task)
        val video = formats.single { MimeTypes.isVideo(it.sampleMimeType) }
        assertEquals("Resolution takes priority over the lower variant's advertised higher bitrate", 3840, video.width)
        assertEquals(2160, video.height)
        val audio = formats.single { MimeTypes.isAudio(it.sampleMimeType) }
        assertEquals("Main", audio.label)
        assertTrue(audio.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0)

        withContext(Dispatchers.IO) {
            val downloader = DefaultDownloaderFactory(cache.factory(task), executor).createDownloader(request)
            val original = File.createTempFile("rss-quality-", ".mp4", context.cacheDir)
            try {
                downloader.download { _, _, _ -> }
                val networkRequests = server.requests.size
                server.stop()
                val uri = Uri.parse(RssMediaExporter(cache).export(task, request))
                published = uri
                assertEquals("Export must remain offline", networkRequests, server.requests.size)
                original.outputStream().use { output ->
                    listOf("quality/init.mp4", "quality/segment00.m4s").forEach { path ->
                        instrumentation.context.assets.open("rss-media-fixture/$path").use { it.copyTo(output) }
                    }
                }
                val input = MediaExtractor()
                val output = MediaExtractor()
                try {
                    input.setDataSource(original.absolutePath)
                    output.setDataSource(context, uri, null)
                    val videoIndex = (0 until output.trackCount).single {
                        output.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")
                    }
                    val outputFormat = output.getTrackFormat(videoIndex)
                    assertEquals(3840, outputFormat.getInteger(MediaFormat.KEY_WIDTH))
                    assertEquals(2160, outputFormat.getInteger(MediaFormat.KEY_HEIGHT))
                    assertTrue((0 until output.trackCount).any {
                        output.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")
                    })
                    val originalSamples = videoSamples(input)
                    assertTrue(originalSamples.isNotEmpty())
                    val exportedSamples = videoSamples(output)
                    assertEquals(originalSamples.size, exportedSamples.size)
                    originalSamples.zip(exportedSamples).forEach { (before, after) ->
                        assertArrayEquals("Every compressed video sample must be preserved", before, after)
                    }
                } finally {
                    input.release()
                    output.release()
                }
            } finally {
                downloader.remove()
                original.delete()
            }
        }
    }

    private suspend fun prepare(task: RssMediaDownloadTask): Pair<DownloadRequest, List<Format>> =
        withTimeout(30_000) {
            withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine { continuation ->
                    val parameters = DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon()
                        .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, false)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                    val helper = RssMediaTrackSelection.createHelper(
                        MediaItem.Builder().setUri(task.url).setMimeType(task.mimeType).build(),
                        cache.upstream(task), parameters)
                    continuation.invokeOnCancellation {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { helper.release() }
                    }
                    helper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                            try {
                                check(tracksInfoAvailable)
                                val formats = mutableListOf<Format>()
                                repeat(helper.periodCount) { period ->
                                    val info = helper.getMappedTrackInfo(period)
                                    repeat(info.rendererCount) { renderer ->
                                        helper.getTrackSelections(period, renderer).forEach { selection ->
                                            repeat(selection.length()) { formats += selection.getFormat(it) }
                                        }
                                    }
                                }
                                val codecs = mapOf("video" to listOf(MimeTypes.VIDEO_H264),
                                    "audio" to listOf(MimeTypes.AUDIO_AAC))
                                val request = helper.getDownloadRequest(task.id, GSON.toJson(codecs).toByteArray(Charsets.UTF_8))
                                if (continuation.isActive) continuation.resume(request to formats)
                            } catch (error: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(error)
                            } finally { helper.release() }
                        }

                        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                            helper.release()
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    })
                }
            }
        }

    private fun videoSamples(extractor: MediaExtractor): List<ByteArray> {
        val index = (0 until extractor.trackCount).single {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")
        }
        extractor.selectTrack(index)
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val samples = mutableListOf<ByteArray>()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val bytes = ByteArray(size)
            buffer.position(0)
            buffer.get(bytes)
            samples += MessageDigest.getInstance("SHA-256").digest(bytes)
            extractor.advance()
        }
        return samples
    }
}
