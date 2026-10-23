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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URI
import java.util.Locale

internal data class RssMediaResolution(val candidates: List<RssMediaCandidate>, val excludedImageUrls: Set<String>)

object RssMediaResolver {
    internal suspend fun resolve(
        article: RssArticle,
        html: String? = null,
        baseUrl: String? = null
    ): RssMediaResolution = withContext(Dispatchers.IO) {
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
        val content = pages.joinToString("\n")
        val candidates = (listOfNotNull(linkedMedia) + extract(article, content, base)
            .filter { it.url != excludedArticleUrl })
            .map { prepare(it, source, base) }.distinctBy { it.url }
        RssMediaResolution(candidates, imageUrlsToExclude(article, content, base))
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
        val excludedImages = mutableSetOf<String>()
        fun add(
            value: String?, kind: String? = null, mime: String? = null,
            thumbnail: String? = null, size: Long? = null, label: String? = null
        ) {
            val url = absoluteUrl(baseUrl, value) ?: return
            val normalizedMime = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotEmpty() }
            val resolvedKind = mediaKind(url, normalizedMime, kind) ?: return
            if (resolvedKind == "image" && isAuxiliaryImageUrl(url)) return
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
        if (!html.contains('<') && isPlainMediaUrl(article.link)) {
            add(article.link, when (article.type) { 1 -> "image"; 2 -> "video"; else -> null })
        }
        mediaPages(article, html).forEach { page ->
            val document = Jsoup.parse(page, baseUrl)
            selectImages(document, baseUrl).forEach { image ->
                excludedImages += image.excluded
                add(image.value, "image", image.mime, label = image.label)
            }
            document.select("video, video source").forEach { element ->
                val video = if (element.tagName() == "video") element else element.parent()
                listOf("src", "data-src").forEach {
                    add(element.attr(it), "video", element.attr("type"), video?.attr("poster"))
                }
            }
            document.select("a[href]").forEach { element ->
                val target = absoluteUrl(baseUrl, element.attr("href"))
                if (target != null && mediaKind(target) == "image" && isAuxiliaryImage(element)) {
                    excludedImages += target
                } else add(element.attr("href"), mime = element.attr("type"), label = element.text())
            }
            document.select("meta[property=og:image], meta[property=og:image:url], meta[property=og:image:secure_url], meta[name=twitter:image]")
                .forEach { add(it.attr("content"), "image") }
            if (!page.contains('<')) page.lineSequence().filter(::isPlainMediaUrl).forEach {
                add(it, when (article.type) { 1 -> "image"; 2 -> "video"; else -> null })
            }
        }
        add(article.image, "image")
        return candidates.values.filter { it.kind != "image" || it.url !in excludedImages }
    }

    private fun mediaPages(article: RssArticle, html: String): List<String> =
        listOf(article.description, article.content, html).filterNotNull().distinct()

    /** DOM alternatives also exclude previews already observed by the network sniffer. */
    internal fun imageUrlsToExclude(article: RssArticle, html: String, baseUrl: String): Set<String> =
        mediaPages(article, html).flatMap { page ->
            val document = Jsoup.parse(page, baseUrl)
            selectImages(document, baseUrl).flatMap { it.excluded } + document.select("a[href]")
                .filter { isAuxiliaryImage(it) }.mapNotNull { absoluteUrl(baseUrl, it.attr("href")) }
        }.toSet()

    private data class ImageSelection(
        val value: String?, val excluded: Set<String>, val label: String?, val mime: String? = null
    )

    private data class ImageSource(val value: String, val pixels: Double = 0.0, val mime: String? = null)

    private fun selectImages(document: Document, baseUrl: String): List<ImageSelection> =
        document.select("img, picture:not(:has(img))").map { image ->
            val picture = if (image.tagName() == "picture") image else image.parent()?.takeIf { it.tagName() == "picture" }
            val elements = listOf(image) + picture?.select("source").orEmpty()
            val width = image.attr("width").removeSuffix("px").toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
            val sets = elements.flatMap { element ->
                listOf("data-srcset", "srcset").flatMap { attribute ->
                    srcsetSources(element.attr(attribute), width).map { it.copy(mime = element.attr("type").ifBlank { null }) }
                }
            }
            val originals = elements.flatMap { element ->
                listOf("data-original", "data-original-src", "data-orig-file", "data-large_image",
                    "data-full-src", "data-full-url").map { ImageSource(element.attr(it)) }
            }
            val linked = image.parent()?.takeIf { it.tagName() == "a" }?.attr("href")
                ?.takeIf { absoluteUrl(baseUrl, it)?.let { url -> mediaKind(url) == "image" } == true }
                ?.let { listOf(ImageSource(it)) }.orEmpty()
            val fallbacks = elements.flatMap { element -> listOf("data-src", "src").map { ImageSource(element.attr(it)) } }
            fun valid(sources: List<ImageSource>) = sources.filter {
                absoluteUrl(baseUrl, it.value)?.let { url -> !isAuxiliaryImageUrl(url) } == true
            }
            val alternatives = originals + linked + sets + fallbacks
            val urls = alternatives.mapNotNull { absoluteUrl(baseUrl, it.value) }.toSet()
            val selected = if (isAuxiliaryImage(image, valid(originals + linked + sets).isNotEmpty())) null else
                valid(originals).firstOrNull() ?: valid(linked).firstOrNull()
                ?: valid(sets).maxByOrNull { it.pixels } ?: valid(fallbacks).firstOrNull()
            ImageSelection(selected?.value, urls - setOfNotNull(absoluteUrl(baseUrl, selected?.value)),
                image.attr("alt").ifBlank { null }, selected?.mime)
        }

    private val auxiliarySemantic = Regex("(?:^|[^a-z0-9])(?:icons?|logos?|avatars?|favicons?|tracking|tracker|spacer|decorative|decoration|social-share|navigation|navbar|buttons?|author-photo|author-image|profile-photo|profile-image)(?:$|[^a-z0-9])")
    private val auxiliaryPath = Regex("(?:^|/)(?:icons?|favicons?|avatars?|tracking|trackers?)(?:/|$)|(?:^|/)(?:(?:site|brand|header|footer)[_-])?(?:favicon|logo|avatar|spacer|pixel|tracking[-_]pixel|transparent|blank)(?:[-_]\\d+(?:x\\d+)?)?\\.[a-z0-9]+$")

    internal fun isAuxiliaryImageUrl(url: String): Boolean = runCatching {
        auxiliaryPath.containsMatchIn(URI(url).path.orEmpty().lowercase(Locale.ROOT))
    }.getOrDefault(false)

    private fun isAuxiliaryImage(image: Element, hasFullSizeSource: Boolean = false): Boolean {
        val width = image.attr("width").removeSuffix("px").toDoubleOrNull()
        val height = image.attr("height").removeSuffix("px").toDoubleOrNull()
        if (!hasFullSizeSource && width != null && height != null && width in 0.0..2.0 && height in 0.0..2.0) return true
        val ancestry = listOf(image) + image.parents()
        if (ancestry.none { it.tagName() in setOf("article", "main") } && ancestry.any {
                it.tagName() in setOf("header", "footer", "aside") ||
                    it.attr("role") in setOf("banner", "contentinfo", "complementary")
            }) return true
        return ancestry.takeWhile { it.tagName() !in setOf("article", "main", "body", "html") }.any { element ->
            element.tagName() in setOf("nav", "button") || element.attr("role") in setOf("navigation", "button") ||
                auxiliarySemantic.containsMatchIn((element.id() + " " + element.className() + " " +
                    element.attr("role") + " " + element.attr("aria-label")).lowercase(Locale.ROOT)) ||
                (element === image && auxiliarySemantic.matches(element.attr("alt").trim().lowercase(Locale.ROOT)))
        }
    }

    private fun isPlainMediaUrl(value: String): Boolean {
        val raw = value.trim().removeSuffix(urlOptions(value.trim()))
        if (raw.isBlank() || raw.any { it.isWhitespace() || it == '<' || it == '>' }) return false
        return raw.startsWith("https://", true) || raw.startsWith("http://", true) ||
            raw.startsWith("//") || raw.startsWith('/') || raw.startsWith("./") || raw.startsWith("../") ||
            runCatching { mediaKind(URI("https://media.invalid/").resolve(raw).toString()) != null }.getOrDefault(false)
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

    private fun srcsetSources(value: String, width: Double): List<ImageSource> {
        val result = mutableListOf<ImageSource>()
        var position = 0
        while (position < value.length) {
            while (position < value.length && (value[position].isWhitespace() || value[position] == ',')) position++
            val start = position
            while (position < value.length && !value[position].isWhitespace()) position++
            val token = value.substring(start, position)
            if (token.isEmpty()) break
            val url = token.trimEnd(',')
            var descriptor = ""
            if (!token.endsWith(',')) {
                val descriptorStart = position
                while (position < value.length && value[position] != ',') position++
                descriptor = value.substring(descriptorStart, position).trim()
            }
            val pixels = when {
                descriptor.isEmpty() -> width
                descriptor.endsWith('w') -> descriptor.dropLast(1).toDoubleOrNull()
                descriptor.endsWith('x') -> descriptor.dropLast(1).toDoubleOrNull()?.times(width)
                else -> null
            }
            if (pixels != null && pixels.isFinite() && pixels > 0) result += ImageSource(url, pixels)
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
