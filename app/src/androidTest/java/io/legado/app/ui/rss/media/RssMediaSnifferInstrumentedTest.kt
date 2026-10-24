package io.legado.app.ui.rss.media

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.doOnLayout
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import fi.iki.elonen.NanoHTTPD
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.model.rss.media.RssMediaFixtureServer
import io.legado.app.lib.cronet.CronetInterceptor
import io.legado.app.ui.about.AboutActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
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

    @Test fun crossOriginRedirectKeepsEachOriginsCredentialsAndResponseCookiesSeparate() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requests = ConcurrentLinkedQueue<Pair<String, Map<String, String>>>()
        val cookieName = "rssMediaRedirect${System.nanoTime()}"
        val server = object : NanoHTTPD("127.0.0.1", 0) {
            override fun serve(session: IHTTPSession): Response {
                requests += session.uri to session.headers.toMap()
                return if (session.uri == "/redirect.mp4" || session.uri == "/same-origin.mp4") {
                    newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply {
                        addHeader("Location", if (session.uri == "/redirect.mp4") "/same-origin.mp4"
                            else "http://localhost:$listeningPort/target.mp4")
                    }
                } else newFixedLengthResponse(Response.Status.OK, "video/mp4", "fixture").apply {
                    addHeader("Set-Cookie", "$cookieName=target-updated; Path=/")
                }
            }
        }.apply { start() }
        val source = "http://127.0.0.1:${server.listeningPort}"
        val target = "http://localhost:${server.listeningPort}"
        val cookies = CookieManager.getInstance()
        try {
            val seeded = CountDownLatch(2)
            instrumentation.runOnMainSync {
                cookies.setCookie(source, "$cookieName=source-original; Path=/") { seeded.countDown() }
                cookies.setCookie(target, "$cookieName=target-original; Path=/") { seeded.countDown() }
            }
            assertTrue("Both origins must have their own session", seeded.await(10, TimeUnit.SECONDS))
            val request = Request.Builder().url("$source/redirect.mp4")
                .header("Cookie", "$cookieName=source-original")
                .header("X-Secret", "public-fixture-secret")
                .header("Range", "bytes=0-100").build()
            val client = RssMediaSniffer.mediaClient(request)
            assertFalse("Cronet must not bypass per-hop credential checks", client.interceptors.any { it is CronetInterceptor })
            client.newCall(request).execute().use {
                assertTrue(it.isSuccessful)
            }
            val initial = requests.first { it.first == "/redirect.mp4" }.second
            val sameOrigin = requests.first { it.first == "/same-origin.mp4" }.second
            val redirected = requests.first { it.first == "/target.mp4" }.second
            assertTrue(initial["cookie"].orEmpty().contains("$cookieName=source-original"))
            assertEquals("public-fixture-secret", initial["x-secret"])
            assertTrue(sameOrigin["cookie"].orEmpty().contains("$cookieName=source-original"))
            assertEquals("public-fixture-secret", sameOrigin["x-secret"])
            assertEquals("bytes=0-100", sameOrigin["range"])
            assertFalse(redirected.containsKey("x-secret"))
            assertTrue(redirected["cookie"].orEmpty().contains("$cookieName=target-original"))
            assertFalse(redirected["cookie"].orEmpty().contains("source-original"))
            assertEquals("bytes=0-100", redirected["range"])
            withTimeout(10_000) {
                while (!cookies.getCookie(target).orEmpty().contains("$cookieName=target-updated")) delay(50)
            }
            assertTrue(cookies.getCookie(source).orEmpty().contains("$cookieName=source-original"))
            assertFalse(cookies.getCookie(source).orEmpty().contains("target-updated"))
        } finally {
            instrumentation.runOnMainSync {
                listOf(source, target).forEach { cookies.setCookie(it, "$cookieName=; Max-Age=0; Path=/") }
            }
            server.stop()
        }
    }

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
