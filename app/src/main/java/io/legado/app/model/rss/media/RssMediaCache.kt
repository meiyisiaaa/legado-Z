package io.legado.app.model.rss.media

import androidx.media3.common.MimeTypes
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RssMediaCache {
    val database = StandaloneDatabaseProvider(appCtx)
    val cache = SimpleCache(File(appCtx.filesDir, "rss-media-cache"), NoOpCacheEvictor(), database)

    fun factory(task: RssMediaDownloadTask, offline: Boolean = false): CacheDataSource.Factory =
        CacheDataSource.Factory().setCache(cache)
            .setCacheKeyFactory { spec -> "${task.id}:${spec.key ?: spec.uri}" }
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
            .setUpstreamDataSourceFactory(if (offline) null else upstream(task))

    fun upstream(task: RssMediaDownloadTask): DataSource.Factory =
        OkHttpDataSource.Factory(httpClient(task)).setUserAgent(AppConfig.userAgent)

    private fun httpClient(task: RssMediaDownloadTask): OkHttpClient {
        val headers = GSON.fromJsonObject<Map<String, String>>(task.headersJson).getOrNull().orEmpty()
        val initial = task.url.toHttpUrl()
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                val sameOrigin = sameCredentialOrigin(initial.toString(), request.url.toString())
                headers.forEach { (name, value) ->
                    if (name.lowercase() !in setOf("host", "content-length", "range", "accept-encoding") &&
                        (sameOrigin || name.lowercase() in setOf("referer", "user-agent", "accept", "accept-language"))) {
                        builder.header(name, value)
                    }
                }
                if (!sameOrigin) {
                    // Redirects inherit the prior network request, including its source credentials.
                    headers.keys.filter { name ->
                        name.lowercase(Locale.ROOT) !in setOf("referer", "user-agent", "accept", "accept-language",
                            "host", "content-length", "range", "accept-encoding")
                    }.forEach { builder.removeHeader(it) }
                    builder.removeHeader("Cookie").removeHeader("Authorization").removeHeader("Proxy-Authorization")
                    android.webkit.CookieManager.getInstance().getCookie(request.url.toString())
                        ?.takeIf(String::isNotBlank)?.let { builder.header("Cookie", it) }
                }
                if (request.header("Referer") == null && headers.keys.none { it.equals("Referer", true) }) {
                    builder.header("Referer", task.link)
                }
                chain.proceed(builder.build())
            }.build()
    }

    suspend fun probeMime(task: RssMediaDownloadTask): String = suspendCancellableCoroutine { continuation ->
        val call = httpClient(task).newCall(Request.Builder().url(task.url)
            .header("User-Agent", AppConfig.userAgent).header("Range", "bytes=0-1023").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val mime = response.use {
                        if (!response.isSuccessful) throw IOException("媒体响应失败：HTTP ${response.code}")
                        val prefix = ByteArray(1024)
                        var count = 0
                        response.body.byteStream().use { input ->
                            while (count < prefix.size && continuation.isActive) {
                                val size = input.read(prefix, count, prefix.size - count)
                                if (size < 0) break
                                count += size
                            }
                        }
                        RssMediaSignatures.responseMime(prefix.copyOf(count), response.body.contentType()?.toString(), task.kind)
                    }
                    if (continuation.isActive) continuation.resume(mime)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    fun release() {
        cache.release()
        database.close()
    }

    companion object {
        fun requiresProbe(task: RssMediaDownloadTask): Boolean {
            if (adaptiveMime(task) != null) return false
            val mime = task.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            if (mime?.startsWith("image/") == true || mime?.startsWith("video/") == true) return false
            val extension = task.url.toHttpUrlOrNull()?.encodedPath?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            return extension !in setOf("mp4", "m4v", "mov", "webm", "mkv", "avi", "flv", "ogv", "ts",
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
        }

        fun isCredentialHeader(name: String) = name.equals("Cookie", true) ||
            name.equals("Authorization", true) || name.equals("Proxy-Authorization", true)

        fun sameCredentialOrigin(first: String, second: String): Boolean {
            val a = first.toHttpUrl()
            val b = second.toHttpUrl()
            return a.host == b.host && a.port == b.port && a.scheme == b.scheme
        }

        fun adaptiveMime(task: RssMediaDownloadTask): String? {
            val mime = task.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
            val path = task.url.toHttpUrlOrNull()?.encodedPath.orEmpty()
            return when {
                mime in listOf("application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/x-mpegurl", "audio/mpegurl") ||
                    path.endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                mime == MimeTypes.APPLICATION_MPD ||
                    path.endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }
    }
}
