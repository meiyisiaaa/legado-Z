package io.legado.app.ui.rss.media

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.model.rss.media.RssMediaCandidate
import io.legado.app.model.rss.media.RssMediaCache
import io.legado.app.model.rss.media.RssMediaResolver
import io.legado.app.help.http.CookieManager
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.cronet.CronetInterceptor
import io.legado.app.utils.GSON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.URI
import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Collections
import java.util.Locale

/** One bridge-free WebView is owned by the panel and reused for every article. */
@SuppressLint("SetJavaScriptEnabled")
internal class RssMediaSniffer(context: Context, private val scope: CoroutineScope,
    private val onCandidates: (List<RssMediaCandidate>) -> Unit, private val onFinished: () -> Unit,
    private val onError: (String) -> Unit = {},
    private val onImageExclusions: (RssArticle, Set<String>) -> Unit = { _, _ -> }) {
    @Volatile private var current: RssArticle? = null
    @Volatile private var source: RssSource? = null
    @Volatile private var mainPageUrl: String? = null
    private val calls = Collections.synchronizedSet(mutableSetOf<Call>())
    private var scanJob: Job? = null
    private var pageFinished = false
    private var generation = 0
    @Volatile private var navigation = 0
    @Volatile private var running = false
    val view = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                mainPageUrl = url
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && running) onError("${error.errorCode}: ${error.description}")
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.scheme !in listOf("http", "https")

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (!running) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
                if (!allowed(url, source)) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
                current?.let { article -> captureRequest(article, url, request.requestHeaders) { values -> view.post { onCandidates(values) } } }
                if (request.method == "GET" && !request.isForMainFrame && URI(url).scheme in listOf("http", "https")) {
                    val extension = url.substringBefore('?').substringAfterLast('.').lowercase()
                    if (extension !in listOf("js", "css", "woff", "woff2", "ttf", "ico", "svg")) {
                        return interceptMediaResponse(view, request)
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!running) return
                pageFinished = true
                source?.injectJs?.takeIf { it.isNotBlank() }?.let { view.evaluateJavascript(it, null) }
                if (running) current?.let { captureDom(view, it, onCandidates, onImageExclusions) }
            }
        }
    }

    fun scan(articles: List<RssArticle>) {
        stop()
        val token = generation
        scanJob = scope.launch {
            try {
                for (article in articles) {
                    try {
                        load(article)
                        repeat(40) { if (!pageFinished) delay(200) }
                        delay(1200)
                        captureDom(view, article, onCandidates, onImageExclusions)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { onError("${article.title}: ${e.localizedMessage.orEmpty()}") }
                }
            } finally {
                if (token == generation) {
                    running = false
                    synchronized(calls) { calls.forEach { it.cancel() }; calls.clear() }
                    view.stopLoading(); view.onPause(); onFinished()
                }
            }
        }
    }

    fun open(article: RssArticle) {
        stop()
        scanJob = scope.launch {
            try { load(article) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { onError(e.localizedMessage.orEmpty()) }
        }
    }

    private suspend fun load(article: RssArticle) {
        running = false
        navigation++
        view.stopLoading()
        synchronized(calls) { calls.forEach { it.cancel() }; calls.clear() }
        current = article
        source = withContext(Dispatchers.IO) { appDb.rssSourceDao.getByKey(article.origin) }
        pageFinished = false
        view.onResume()
        val analyzed = withContext(Dispatchers.IO) {
            RssMediaResolver.analyzeForTarget(article.link, article.origin, source, article)
        }
        val url = analyzed.url
        mainPageUrl = url
        if (URI(url).scheme in listOf("http", "https")) {
            val headers = withContext(Dispatchers.IO) {
                analyzed.getGlideUrl().headers
            }
            headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value?.split(';')?.forEach {
                android.webkit.CookieManager.getInstance().setCookie(url, it.trim())
            }
            running = true
            view.loadUrl(url, headers)
        }
    }

    private fun interceptMediaResponse(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val article = current ?: return null
        val token = navigation
        val url = request.url.toString()
        return runCatching {
            val analyzed = runBlocking(Dispatchers.IO) {
                RssMediaResolver.analyzeForTarget(url, article.link, source, article)
            }
            val targetUrl = analyzed.url
            val analyzedHeaders = analyzed.getGlideUrl().headers
            val builder = Request.Builder().url(targetUrl)
            sourceHeadersForRequest(analyzedHeaders, mainPageUrl ?: article.link, targetUrl)
                .forEach { (key, value) -> builder.header(key, value) }
            sourceHeadersForRequest(request.requestHeaders, url, targetUrl).forEach { (key, value) -> builder.header(key, value) }
            android.webkit.CookieManager.getInstance().getCookie(targetUrl)?.let { builder.header("Cookie", it) }
            val mediaRequest = builder.build()
            val call = mediaClient(mediaRequest).newCall(mediaRequest)
            synchronized(calls) {
                if (!running || token != navigation) return@runCatching null
                calls += call
            }
            val response = try { call.execute() } catch (e: Exception) { calls -= call; throw e }
            CookieManager.saveResponse(response)
            val mime = response.body.contentType()?.toString()?.substringBefore(';').orEmpty()
            val kind = when {
                mime.startsWith("video/", true) || mime.contains("mpegurl", true) || mime.contains("dash+xml", true) -> "video"
                else -> null
            }
            if (kind != null) {
                val headers = mediaRequestHeaders(response.request.headers.toMap())
                val candidate = RssMediaCandidate(article, response.request.url.toString(), kind, mimeType = mime,
                    size = response.body.contentLength().takeIf { it >= 0 && !mime.contains("mpegurl", true) && !mime.contains("dash+xml", true) },
                    thumbnail = article.image, headers = headers)
                view.post { if (token == navigation && running) onCandidates(listOf(candidate)) }
            }
            if (response.code !in 200..299) { response.close(); calls -= call; return@runCatching null }
            WebResourceResponse(mime, response.body.contentType()?.charset()?.name() ?: "utf-8",
                response.code, response.message.ifBlank { "OK" }, response.headers.toMap(),
                object : FilterInputStream(response.body.byteStream()) {
                    override fun close() { try { super.close() } finally { response.close(); calls -= call } }
                })
        }.getOrNull()
    }

    fun stop() {
        running = false
        navigation++
        generation++
        scanJob?.cancel(); scanJob = null
        synchronized(calls) { calls.forEach { it.cancel() }; calls.clear() }
        view.stopLoading(); view.onPause()
    }
    fun destroy() {
        stop(); current = null
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.removeAllViews(); view.destroy()
    }

    companion object {
        internal fun mediaClient(initial: Request) = okHttpClient.newBuilder()
            // Cronet handles redirects itself and bypasses OkHttp's per-hop credential checks.
            .apply { interceptors().removeAll { it is CronetInterceptor } }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                if (!RssMediaCache.sameCredentialOrigin(initial.url.toString(), request.url.toString())) {
                    val permitted = sourceHeadersForRequest(initial.headers.toMap(), initial.url.toString(), request.url.toString())
                    initial.headers.names().filter { name ->
                        name !in permitted && name.lowercase(Locale.ROOT) !in
                            setOf("host", "content-length", "range", "accept-encoding")
                    }.forEach { builder.removeHeader(it) }
                    builder.removeHeader("Cookie").removeHeader("Authorization").removeHeader("Proxy-Authorization")
                    android.webkit.CookieManager.getInstance().getCookie(request.url.toString())
                        ?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
                }
                chain.proceed(builder.build()).also { response ->
                    response.headers("Set-Cookie").forEach {
                        android.webkit.CookieManager.getInstance().setCookie(response.request.url.toString(), it)
                    }
                }
            }.build()

        internal fun mediaRequestHeaders(headers: Map<String, String>): Map<String, String> = headers.filterKeys {
            it.lowercase(Locale.ROOT) !in setOf("cookie", "cookie2", "host", "content-length",
                "accept-encoding", "connection", "keep-alive", "proxy-connection", "proxy-authorization",
                "transfer-encoding", "te", "trailer", "upgrade", "range", "if-range", "if-none-match", "if-modified-since")
        }

        internal fun sourceHeadersForRequest(headers: Map<String, String>, pageUrl: String,
            requestUrl: String): Map<String, String> {
            val page = pageUrl.toHttpUrlOrNull()
            val target = requestUrl.toHttpUrlOrNull()
            val sameOrigin = page != null && target != null && page.scheme == target.scheme &&
                page.host == target.host && page.port == target.port
            return headers.filterKeys { sameOrigin || it.lowercase(Locale.ROOT) in
                setOf("referer", "user-agent", "accept", "accept-language") }
        }

        fun captureRequest(article: RssArticle, url: String, headers: Map<String, String> = emptyMap(),
            onCandidates: (List<RssMediaCandidate>) -> Unit) {
            val extension = url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase()
            val kind = when (extension) {
                "m3u8", "mpd", "mp4", "webm", "m4v", "mov", "mkv" -> "video"
                else -> return
            }
            if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return
            val mime = when (extension) { "m3u8" -> "application/vnd.apple.mpegurl"; "mpd" -> "application/dash+xml"; else -> null }
            // Credentials belong to this exact media origin; cookies refresh from its current session.
            onCandidates(listOf(RssMediaCandidate(article, url, kind, mimeType = mime,
                thumbnail = article.image, headers = mediaRequestHeaders(headers))))
        }

        fun captureDom(webView: WebView, article: RssArticle, onCandidates: (List<RssMediaCandidate>) -> Unit,
            onImageExclusions: (RssArticle, Set<String>) -> Unit = { _, _ -> }) {
            webView.evaluateJavascript("""(function(){return document.documentElement.outerHTML;})()""") { value ->
                val html = runCatching { GSON.fromJson(value, String::class.java) }.getOrNull() ?: return@evaluateJavascript
                val base = webView.url ?: article.link
                onImageExclusions(article, RssMediaResolver.imageUrlsToExclude(article, html, base))
                onCandidates(RssMediaResolver.extract(article, html, base))
            }
            webView.evaluateJavascript("""JSON.stringify(performance.getEntriesByType('resource').map(function(e){return e.name;}))""") { value ->
                val json = runCatching { GSON.fromJson(value, String::class.java) }.getOrNull() ?: return@evaluateJavascript
                runCatching { GSON.fromJson(json, Array<String>::class.java) }.getOrNull()?.forEach { url -> captureRequest(article, url, onCandidates = onCandidates) }
            }
        }

        private fun allowed(url: String, source: RssSource?): Boolean {
            fun matches(rule: String): Boolean = url.startsWith(rule) || runCatching { Regex(rule).matches(url) }.getOrDefault(false)
            val blacklist = source?.contentBlacklist?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            if (blacklist.isNotEmpty()) return blacklist.none(::matches)
            val whitelist = source?.contentWhitelist?.split(',')?.filter { it.isNotBlank() }.orEmpty()
            return whitelist.isEmpty() || whitelist.any(::matches)
        }
    }
}
