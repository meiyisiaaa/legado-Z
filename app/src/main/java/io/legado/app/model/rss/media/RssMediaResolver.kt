package io.legado.app.model.rss.media

import com.google.gson.JsonObject
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssSource
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.rss.Rss
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URI
import java.util.Locale

object RssMediaResolver {
    suspend fun resolve(
        article: RssArticle,
        html: String? = null,
        baseUrl: String? = null
    ): List<RssMediaCandidate> = withContext(Dispatchers.IO) {
        val source = appDb.rssSourceDao.getByKey(article.origin)
        var base = baseUrl ?: absoluteUrl(article.origin, article.link)
            ?: NetworkUtils.getAbsoluteURL(article.origin, article.link)
        val pages = mutableListOf(html.orEmpty())
        var linkedMedia: RssMediaCandidate? = null
        var excludedArticleUrl: String? = null
        if (html == null) {
            val rule = source?.ruleContent?.takeIf { it.isNotBlank() }
            if (rule != null) {
                pages += Rss.getContentAwait(article, rule, source)
            } else if (article.type == 0) {
                excludedArticleUrl = absoluteUrl(article.origin, article.link)
                val request = analyzeForTarget(article.link, article.origin, source, article)
                request.getResponseAwait().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${response.message}")
                    base = response.request.url.toString()
                    val mime = response.body.contentType()?.let { "${it.type}/${it.subtype}" }
                        ?.lowercase(Locale.ROOT)
                    val kind = mediaKind(base, mime)
                    if (kind != null) {
                        linkedMedia = RssMediaCandidate(
                            article, base, kind, mime,
                            thumbnail = absoluteUrl(base, article.image),
                            size = response.body.contentLength().takeIf { it >= 0 },
                            headers = capturedHeaders(RssMediaCandidate(article, request.url, kind,
                                headers = request.getGlideUrl().headers.toMap()), base)
                        )
                    } else if (mime == null || mime in setOf("text/html", "application/xhtml+xml")) {
                        pages += response.body.string()
                    }
                }
            }
        }
        (listOfNotNull(linkedMedia) + extract(article, pages.joinToString("\n"), base)
            .filter { it.url != excludedArticleUrl })
            .map { prepare(it, source, base) }.distinctBy { it.url }
    }

    suspend fun prepare(candidate: RssMediaCandidate): RssMediaCandidate = withContext(Dispatchers.IO) {
        val source = appDb.rssSourceDao.getByKey(candidate.article.origin)
        prepare(candidate, source, candidate.article.link)
    }

    private suspend fun prepare(
        candidate: RssMediaCandidate, source: RssSource?, baseUrl: String
    ): RssMediaCandidate {
        currentCoroutineContext().ensureActive()
        val analyzed = analyzeForTarget(candidate.requestUrl, baseUrl, source, candidate.article)
        val url = absoluteUrl(baseUrl, analyzed.url) ?: throw IOException("媒体地址无效")
        analyzed.headerMap.putAll(capturedHeaders(candidate, url))
        val glideUrl = analyzed.getGlideUrl()
        return candidate.copy(url = url, headers = glideUrl.headers.toMap())
    }

    internal suspend fun analyzeForTarget(
        requestUrl: String, baseUrl: String, source: RssSource?, article: RssArticle
    ): AnalyzeUrl {
        val context = currentCoroutineContext()
        val analyzed = AnalyzeUrl(requestUrl, baseUrl = baseUrl, source = source,
            ruleData = article, coroutineContext = context)
        val target = absoluteUrl(baseUrl, analyzed.url) ?: throw IOException("媒体地址无效")
        if (source == null || sameOrigin(source.sourceUrl, target)) return analyzed
        val headers = sourceHeaders(source.sourceUrl, target, analyzed.headerMap, analyzed.ruleUrl)
        val options = targetOptions(analyzed.ruleUrl)
        // The first AnalyzeUrl already evaluated the URL JS with its original source context.
        return AnalyzeUrl(target + (options?.let { ",$it" } ?: ""), baseUrl = target,
            ruleData = article, coroutineContext = context, headerMapF = headers)
    }

    internal fun sourceHeaders(sourceUrl: String, targetUrl: String,
        headers: Map<String, String>, ruleUrl: String): Map<String, String> {
        if (sameOrigin(sourceUrl, targetUrl)) return headers
        val explicit = GSON.fromJsonObject<AnalyzeUrl.UrlOption>(urlOptions(ruleUrl).removePrefix(",").trim())
            .getOrNull()?.getHeaderMap()?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty()
        return headers.filterKeys { it.lowercase(Locale.ROOT) in publicHeaders } + explicit
    }

    internal fun targetOptions(ruleUrl: String): JsonObject? =
        GSON.fromJsonObject<JsonObject>(urlOptions(ruleUrl).removePrefix(",").trim()).getOrNull()
            ?.apply { remove("js") }

    private fun sameOrigin(first: String, second: String): Boolean =
        runCatching { RssMediaCache.sameCredentialOrigin(first.removeSuffix(urlOptions(first)), second) }.getOrDefault(false)

    private val publicHeaders = setOf("referer", "user-agent", "accept", "accept-language")

    internal fun capturedHeaders(candidate: RssMediaCandidate, targetUrl: String): Map<String, String> =
        if (sameOrigin(candidate.url, targetUrl)) candidate.headers
        else candidate.headers.filterKeys {
            it.lowercase(Locale.ROOT) in publicHeaders
        }

    fun extract(article: RssArticle, html: String, baseUrl: String): List<RssMediaCandidate> {
        val candidates = linkedMapOf<String, RssMediaCandidate>()
        fun add(
            value: String?, kind: String? = null, mime: String? = null,
            thumbnail: String? = null, size: Long? = null, label: String? = null
        ) {
            val url = absoluteUrl(baseUrl, value) ?: return
            val normalizedMime = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotEmpty() }
            val resolvedKind = mediaKind(url, normalizedMime, kind) ?: return
            val item = RssMediaCandidate(
                article, url, resolvedKind, normalizedMime,
                absoluteUrl(baseUrl, thumbnail), size?.takeIf { it >= 0 }, label,
                requestUrl = url + urlOptions(value)
            )
            // Feed metadata is more informative than a repeated HTML src.
            candidates.putIfAbsent(url, item)
        }
        GSON.fromJsonObject<List<RssMediaAttachment?>>(article.enclosureJson).getOrNull()
            ?.filterNotNull()?.forEach { add(it.url, it.kind, it.mimeType, it.thumbnail, it.size, it.label) }
        if (!html.contains('<')) {
            add(article.link, when (article.type) { 1 -> "image"; 2 -> "video"; else -> null })
        }
        listOf(article.description, article.content, html).filterNotNull().distinct().forEach { page ->
            val document = Jsoup.parse(page, baseUrl)
            document.select("img").forEach { element ->
                listOf("src", "data-src", "data-original").forEach {
                    add(element.attr(it), "image", label = element.attr("alt").ifBlank { null })
                }
                listOf("srcset", "data-srcset").forEach { attribute ->
                    srcsetUrls(element.attr(attribute)).forEach { add(it, "image") }
                }
            }
            document.select("video, video source").forEach { element ->
                val video = if (element.tagName() == "video") element else element.parent()
                listOf("src", "data-src").forEach {
                    add(element.attr(it), "video", element.attr("type"), video?.attr("poster"))
                }
            }
            document.select("a[href], source[srcset]").forEach { element ->
                if (element.tagName() == "a") {
                    add(element.attr("href"), mime = element.attr("type"), label = element.text())
                } else if (element.parent()?.tagName() == "picture") {
                    srcsetUrls(element.attr("srcset")).forEach { add(it, "image", element.attr("type")) }
                }
            }
            if (!page.contains('<')) page.lineSequence().forEach {
                add(it, when (article.type) { 1 -> "image"; 2 -> "video"; else -> null })
            }
        }
        add(article.image, "image")
        return candidates.values.toList()
    }

    internal fun extractAttachments(item: String): List<RssMediaAttachment> =
        Jsoup.parse(item, "", Parser.xmlParser()).getAllElements().mapNotNull { element ->
            if (element.tagName() !in setOf("enclosure", "media:content") ||
                element.attr("url").isBlank()
            ) return@mapNotNull null
            RssMediaAttachment(
                url = element.attr("url"),
                mimeType = element.attr("type").ifBlank { null },
                size = (element.attr("length").ifBlank { element.attr("fileSize") }).toLongOrNull(),
                label = element.attr("title").ifBlank { null },
                kind = element.attr("medium").ifBlank { null }
            )
        }

    internal fun absoluteUrl(baseUrl: String, value: String?): String? {
        val raw = value?.trim()?.let { it.removeSuffix(urlOptions(it)) }
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.startsWith('#')) return null
        return runCatching {
            val url = URI(baseUrl.removeSuffix(urlOptions(baseUrl))).resolve(raw.replace(" ", "%20")).normalize()
            if (url.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !url.host.isNullOrBlank() && url.userInfo == null
            ) url.toString().substringBefore('#') else null
        }.getOrNull()
    }

    private fun urlOptions(value: String?): String = value?.let {
        Regex(",\\s*\\{").find(it)?.range?.first?.let { position -> it.substring(position) }
    }.orEmpty()

    private fun srcsetUrls(value: String): List<String> {
        val result = mutableListOf<String>()
        var position = 0
        while (position < value.length) {
            while (position < value.length && (value[position].isWhitespace() || value[position] == ',')) position++
            val start = position
            while (position < value.length && !value[position].isWhitespace()) position++
            val token = value.substring(start, position)
            if (token.isEmpty()) break
            result += token.trimEnd(',')
            if (!token.endsWith(',')) {
                while (position < value.length && value[position] != ',') position++
            }
        }
        return result
    }

    internal fun mediaKind(url: String, mime: String? = null, hint: String? = null): String? {
        if (mime?.startsWith("image/") == true) return "image"
        if (mime?.startsWith("video/") == true || mime in playlistTypes) return "video"
        if (mime != null && mime !in setOf("application/octet-stream", "binary/octet-stream")) return null
        val extension = runCatching { URI(url).path.orEmpty().substringAfterLast('.').lowercase(Locale.ROOT) }
            .getOrDefault("")
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "heic", "heif" -> "image"
            "mp4", "m4v", "webm", "mkv", "mov", "avi", "ts", "m3u8", "mpd" -> "video"
            "html", "htm", "xhtml" -> null
            else -> hint?.takeIf { it == "image" || it == "video" }
        }
    }

    private val playlistTypes = setOf(
        "application/vnd.apple.mpegurl", "application/x-mpegurl", "application/dash+xml",
        "audio/mpegurl", "audio/x-mpegurl"
    )
}
