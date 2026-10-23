package io.legado.app.model.rss.media

import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Process
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Host tools/rss-media/serve-fixtures.py must survive the target app's actual force-stop. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RssMediaRecoveryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val base get() = arguments.getString("rssMediaHost") ?: "http://10.0.2.2:8765"
    private val token get() = arguments.getString("rssMediaRun") ?: "public-recovery-fixture"
    private val preferences get() = context.getSharedPreferences("rss-media-recovery-test", 0)
    private lateinit var activity: ActivityScenario<AboutActivity>
    private val ids = mutableSetOf<String>()
    private val cases = mutableSetOf<String>()
    private var preservePausedTask = false

    @Before fun setUp() {
        assumeTrue("Host recovery tests require an explicit rssMediaRun argument",
            !arguments.getString("rssMediaRun").isNullOrBlank())
        activity = ActivityScenario.launch(AboutActivity::class.java)
    }

    /** Run this method, force-stop the package from adb, then run resumePersistedTask. */
    @Test fun preparePausedTask() = runBlocking {
        val case = "$token-process"
        control(case, "reset=1&offline=0&drop=0")
        val candidate = candidate("/slow/sample.mp4", case)
        assertNull("Use a new rssMediaRun or finish the existing two-phase test", task(candidate.id))
        RssMediaDownloads.enqueue(listOf(candidate))
        val downloading = waitFor(candidate.id) {
            it.status == RssMediaDownloadTask.DOWNLOADING && it.bytesDownloaded >= 8192 &&
                (it.totalBytes < 0 || it.bytesDownloaded < it.totalBytes)
        }
        RssMediaDownloads.pause(candidate.id)
        val paused = waitFor(candidate.id) { it.status == RssMediaDownloadTask.PAUSED }
        assertNull(paused.outputUri)
        assertTrue(preferences.edit().putString("$token-id", candidate.id)
            .putInt("$token-pid", Process.myPid()).putLong("$token-bytes", downloading.bytesDownloaded).commit())
        preservePausedTask = true
    }

    @Test fun resumePersistedTask() = runBlocking {
        val candidate = candidate("/slow/sample.mp4", "$token-process")
        assertEquals("Run preparePausedTask first", candidate.id, preferences.getString("$token-id", null))
        assertNotEquals("Host must force-stop the target process between both phases",
            preferences.getInt("$token-pid", Process.myPid()), Process.myPid())
        val persisted = task(candidate.id)!!
        assertEquals(RssMediaDownloadTask.PAUSED, persisted.status)
        assertTrue(preferences.getLong("$token-bytes", 0) > 0)
        assertTrue(persisted.bytesDownloaded > 0)
        control("$token-process", "offline=0&drop=0")
        RssMediaDownloads.resume(candidate.id)
        val completed = waitFor(candidate.id) { it.status == RssMediaDownloadTask.COMPLETED }
        assertPlayable(completed)
        assertTrue("Process recovery must resume the cached prefix using Range",
            stats("$token-process").getInt("nonzero_ranges") > 0)
    }

    @Test fun interruptedConnectionRecoversAfterExplicitRetry() = runBlocking {
        val case = "$token-offline"
        control(case, "reset=1&offline=0&drop=1")
        val candidate = candidate("/flaky/sample.mp4", case)
        RssMediaDownloads.enqueue(listOf(candidate))
        val failed = waitFor(candidate.id, allowFailure = true) { it.status == RssMediaDownloadTask.FAILED }
        assertNull(failed.outputUri)
        assertEquals(1, stats(case).getInt("drops"))
        assertTrue(stats(case).getBoolean("offline"))
        control(case, "offline=0")
        RssMediaDownloads.retry(candidate.id)
        assertPlayable(waitFor(candidate.id) { it.status == RssMediaDownloadTask.COMPLETED })
        assertTrue("Retry must resume downloaded bytes", stats(case).getInt("nonzero_ranges") > 0)
    }

    @Test fun redirectedMediaPublishesPlayableFile() = runBlocking {
        val case = "$token-redirect"
        control(case, "reset=1&offline=0&drop=0")
        val candidate = candidate("/redirect/sample.mp4", case)
        RssMediaDownloads.enqueue(listOf(candidate))
        assertPlayable(waitFor(candidate.id) { it.status == RssMediaDownloadTask.COMPLETED })
        assertTrue(stats(case).getInt("redirects") > 0)
    }

    @After fun tearDown() = runBlocking {
        if (!::activity.isInitialized) return@runBlocking
        try {
            if (!preservePausedTask) {
                context.stopService(Intent(context, RssMediaDownloadService::class.java))
                withTimeout(15_000) {
                    while (SimpleCache.isCacheFolderLocked(File(context.filesDir, "rss-media-cache"))) delay(100)
                }
                withContext(Dispatchers.IO) {
                    val cache = RssMediaCache()
                    try {
                        val index = DefaultDownloadIndex(cache.database)
                        ids.forEach { id ->
                            appDb.rssMediaDownloadDao.get(id)?.let { task ->
                                task.outputUri?.let { context.contentResolver.delete(Uri.parse(it), null, null) }
                                RssMediaFiles.clearPartial(task)
                            }
                            index.removeDownload(id)
                            cache.cache.keys.filter { it.startsWith("$id:") }.forEach { cache.cache.removeResource(it) }
                            appDb.openHelper.writableDatabase.execSQL("DELETE FROM rssMediaDownloadTasks WHERE id = ?", arrayOf(id))
                        }
                    } finally {
                        cache.release()
                    }
                }
                if (preferences.getString("$token-id", null)?.let { it in ids } == true) {
                    preferences.edit().remove("$token-id").remove("$token-pid").remove("$token-bytes").commit()
                }
                cases.forEach { control(it, "offline=0&drop=0") }
            }
        } finally {
            activity.close()
        }
    }

    private fun candidate(path: String, case: String): RssMediaCandidate {
        cases += case
        val url = "$base$path?case=${Uri.encode(case)}"
        return RssMediaCandidate(RssArticle(origin = "$base/public-recovery/$token",
            link = "$base/article", title = "Public recovery fixture"), url, "video", "video/mp4")
            .also { ids += it.id }
    }

    private suspend fun task(id: String) = withContext(Dispatchers.IO) { appDb.rssMediaDownloadDao.get(id) }

    private suspend fun waitFor(id: String, allowFailure: Boolean = false,
        predicate: (RssMediaDownloadTask) -> Boolean): RssMediaDownloadTask = withTimeout(60_000) {
        while (true) {
            task(id)?.let {
                if (predicate(it)) return@withTimeout it
                if (!allowFailure && it.status in listOf(RssMediaDownloadTask.FAILED, RssMediaDownloadTask.EXPORT_FAILED)) {
                    fail("Task failed: ${it.status}: ${it.error}")
                }
            }
            delay(100)
        }
        @Suppress("UNREACHABLE_CODE") error("Unreachable")
    }

    private suspend fun assertPlayable(task: RssMediaDownloadTask) = withContext(Dispatchers.IO) {
        assertTrue(RssMediaFiles.exists(task.outputUri))
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(task.outputUri), null)
            val types = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
            assertTrue(types.any { it.startsWith("video/") })
            assertTrue(types.any { it.startsWith("audio/") })
            assertTrue((0 until extractor.trackCount).any {
                extractor.getTrackFormat(it).let { format -> format.containsKey(MediaFormat.KEY_DURATION) && format.getLong(MediaFormat.KEY_DURATION) > 0 }
            })
        } finally {
            extractor.release()
        }
    }

    private suspend fun control(case: String, options: String) = request("/__control?case=${Uri.encode(case)}&$options")
    private suspend fun stats(case: String) = request("/__stats?case=${Uri.encode(case)}")
    private suspend fun request(path: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        try {
            check(connection.responseCode == 200) { "Start tools/rss-media/serve-fixtures.py: HTTP ${connection.responseCode}" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
