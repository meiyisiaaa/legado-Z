package io.legado.app.help.http

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class BackstageWebViewLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cancelledHtmlCallbacksCannotChangeTheNextBorrowersPage() = runBlocking {
        assertCancelledCallbacksAreIsolated(sniffer = false)
    }

    @Test
    fun cancelledSnifferCallbacksCannotChangeTheNextBorrowersPage() = runBlocking {
        assertCancelledCallbacksAreIsolated(sniffer = true)
    }

    private suspend fun assertCancelledCallbacksAreIsolated(sniffer: Boolean) {
        val request = BackstageWebView(
            url = OLD_URL,
            html = OLD_HTML,
            javaScript = STALE_SCRIPT,
            sourceRegex = if (sniffer) "(?!)" else null,
            delayTime = 30_000,
            timeout = TIMEOUT,
        )
        val parent = Job()
        val result = CoroutineScope(parent + Dispatchers.Default).async { request.getStrResponse() }
        var borrowed: PooledWebView? = null
        try {
            val original = awaitLease(request)
            awaitMainCondition("Original page did not load") {
                original.realWebView.title == "obsolete" && original.realWebView.progress == 100
            }
            val oldClient = withContext(Dispatchers.Main) {
                original.realWebView.webViewClient.also {
                    it.onPageFinished(original.realWebView, OLD_URL)
                }
            }
            val oldTask = withContext(Dispatchers.Main) {
                when {
                    sniffer -> {
                        val type = oldClient.javaClass.declaredClasses
                            .single { it.simpleName == "LoadJsRunnable" }
                        type.declaredConstructors.single().apply { isAccessible = true }
                            .newInstance(oldClient, original.realWebView, STALE_SCRIPT) as Runnable
                    }
                    else -> oldClient.javaClass.getDeclaredField("runnable")
                        .apply { isAccessible = true }.get(oldClient) as Runnable
                }
            }

            withContext(Dispatchers.Main) { oldTask.run() }
            assertEquals("1", evaluate(original.realWebView, "window.staleRuns"))

            result.cancelAndJoin()
            awaitMainCondition("Cancelled request retained its WebView lease") {
                leaseOf(request) == null && !original.isInUse
            }
            val next = withContext(Dispatchers.Main) {
                WebViewPool.acquire(context).also {
                    borrowed = it
                    assertSame("The cancelled request's WebView should be the next pooled borrow", original, it)
                    it.realWebView.onResume()
                }
            }
            loadBorrowerPage(next.realWebView)
            assertEquals("0", evaluate(next.realWebView, "window.staleRuns"))
            assertEquals("\"borrower\"", evaluate(next.realWebView, "window.result"))

            withContext(Dispatchers.Main) {
                // Simulate late WebViewClient delivery as well as a retained delayed Runnable.
                oldClient.onPageFinished(next.realWebView, OLD_URL)
                oldTask.run()
            }
            assertEquals(
                "A cancelled ${if (sniffer) "sniffer" else "HTML"} callback modified the reused WebView",
                "0",
                evaluate(next.realWebView, "window.staleRuns"),
            )
            assertEquals("\"borrower\"", evaluate(next.realWebView, "window.result"))
            assertTrue(withContext(Dispatchers.Main) { next.isInUse })
            assertTrue(result.isCancelled)
        } finally {
            result.cancelAndJoin()
            parent.cancel()
            withContext(Dispatchers.Main) {
                disposeForTest(request)
                borrowed?.let { WebViewPool.release(it) }
            }
        }
    }

    private suspend fun loadBorrowerPage(webView: WebView) {
        val loaded = CompletableDeferred<Unit>()
        withContext(Dispatchers.Main) {
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (view.title == "borrower") loaded.complete(Unit)
                }
            }
            webView.loadDataWithBaseURL(
                "https://backstage-lifecycle.invalid/borrower",
                """<html><head><title>borrower</title></head><body><script>
                    window.staleRuns = 0; window.result = 'borrower';
                    </script></body></html>""".trimIndent(),
                "text/html", "UTF-8", null
            )
        }
        withTimeout(TIMEOUT) { loaded.await() }
    }

    private suspend fun evaluate(webView: WebView, script: String): String = withTimeout(TIMEOUT) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                webView.evaluateJavascript(script) {
                    if (continuation.isActive) continuation.resume(it)
                }
            }
        }
    }

    private suspend fun awaitLease(request: BackstageWebView): PooledWebView = withTimeout(TIMEOUT) {
        var lease: PooledWebView? = null
        while (lease == null) {
            lease = withContext(Dispatchers.Main) { leaseOf(request) }
            if (lease == null) delay(10)
        }
        checkNotNull(lease)
    }

    private suspend fun awaitMainCondition(message: String, condition: () -> Boolean) {
        val matched = withTimeoutOrNull(TIMEOUT) {
            while (!withContext(Dispatchers.Main) { condition() }) delay(10)
            true
        }
        assertTrue(message, matched == true)
    }

    private fun leaseOf(request: BackstageWebView): PooledWebView? =
        BackstageWebView::class.java.getDeclaredField("pooledWebView").apply { isAccessible = true }
            .get(request) as PooledWebView?

    private fun disposeForTest(request: BackstageWebView) {
        BackstageWebView::class.java.getDeclaredField("mHandler").apply { isAccessible = true }
            .get(request).let { (it as android.os.Handler).removeCallbacksAndMessages(null) }
        BackstageWebView::class.java.getDeclaredMethod("destroy").apply { isAccessible = true }
            .invoke(request)
    }

    companion object {
        private const val TIMEOUT = 15_000L
        private const val OLD_URL = "https://backstage-lifecycle.invalid/obsolete"
        private const val OLD_HTML = """
            <html><head><title>obsolete</title></head><body><script>
                window.staleRuns = 0; window.result = 'obsolete';
            </script></body></html>
        """
        private const val STALE_SCRIPT = "window.staleRuns = (window.staleRuns || 0) + 1; void(0);"
    }
}
