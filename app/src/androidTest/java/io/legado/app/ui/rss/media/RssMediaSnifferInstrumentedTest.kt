package io.legado.app.ui.rss.media

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.doOnLayout
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.model.rss.media.RssMediaFixtureServer
import io.legado.app.ui.about.AboutActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RssMediaSnifferInstrumentedTest {
    @Test fun iframeBlobPlayerExposesTheUnderlyingMediaRequest() = scan("/dynamic", "/sample.mp4")

    @Test fun extensionlessFetchIsRecognizedByActualMimeType() = scan("/dynamic-no-extension", "/stream")

    private fun scan(path: String, expectedPath: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val server = RssMediaFixtureServer(instrumentation.context).apply { start() }
        val activity = ActivityScenario.launch(AboutActivity::class.java)
        val values = ConcurrentLinkedQueue<RssMediaCandidate>()
        val errors = ConcurrentLinkedQueue<String>()
        val found = CountDownLatch(1)
        lateinit var sniffer: RssMediaSniffer
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            activity.onActivity {
                sniffer = RssMediaSniffer(it, scope, onCandidates = { candidates ->
                    values.addAll(candidates)
                    if (candidates.any { media -> media.kind == "video" && uriPath(media.url) == expectedPath &&
                            media.mimeType == "video/mp4" }) found.countDown()
                }, onFinished = {}, onError = { error -> errors += error })
                sniffer.view.webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) errors += message.message()
                        return true
                    }
                }
                sniffer.view.alpha = 0f
                val root = it.findViewById<ViewGroup>(android.R.id.content)
                root.addView(sniffer.view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                sniffer.view.doOnLayout { view ->
                    assertTrue("Sniffer must execute in an attached viewport", view.isAttachedToWindow && view.width > 0 && view.height > 0)
                    sniffer.scan(listOf(RssArticle(origin = server.baseUrl, link = server.baseUrl + path, title = "动态播放器验证")))
                }
            }
            val captured = found.await(20, TimeUnit.SECONDS)
            assertTrue("Expected an underlying media request; values=${values.map { it.url }}, requests=${server.requests.map { it.path }}, errors=$errors", captured)
            assertTrue(values.none { it.url.startsWith("blob:") })
            assertTrue(values.any { it.kind == "video" && uriPath(it.url) == expectedPath && it.mimeType == "video/mp4" })
            assertTrue(server.requests.any { it.path == expectedPath })
        } finally {
            activity.onActivity { sniffer.destroy() }
            scope.cancel()
            activity.close()
            server.stop()
        }
    }

    private fun uriPath(value: String) = android.net.Uri.parse(value).path
}
