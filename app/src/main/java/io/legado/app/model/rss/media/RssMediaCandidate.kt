package io.legado.app.model.rss.media

import io.legado.app.data.entities.RssArticle
import io.legado.app.utils.MD5Utils

data class RssMediaCandidate(
    val article: RssArticle,
    val url: String,
    val kind: String,
    val mimeType: String? = null,
    val thumbnail: String? = null,
    val size: Long? = null,
    val label: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val requestUrl: String = url
) {
    val id: String get() = MD5Utils.md5Encode(article.origin + "\n" + article.link + "\n" + url)
}

/** Feed metadata survives refresh, favourites and backup without a second article identity. */
data class RssMediaAttachment(
    val url: String,
    val mimeType: String? = null,
    val size: Long? = null,
    val label: String? = null,
    val thumbnail: String? = null,
    val kind: String? = null
)
