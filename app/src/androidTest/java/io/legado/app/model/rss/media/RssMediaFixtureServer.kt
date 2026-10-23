package io.legado.app.model.rss.media

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue

/** Local, deterministic origin for the real downloader and WebView integration tests. */
internal class RssMediaFixtureServer(private val assetsContext: Context) : NanoHTTPD("127.0.0.1", 0) {
    data class Request(val path: String, val headers: Map<String, String>)
    val requests = ConcurrentLinkedQueue<Request>()
    val baseUrl get() = "http://127.0.0.1:$listeningPort"

    override fun serve(session: IHTTPSession): Response {
        requests.add(Request(session.uri, session.headers.toMap()))
        val protected = session.uri.startsWith("/protected/")
        if (protected && (session.headers["x-fixture-token"] != "public-test-token" ||
                    session.headers["referer"] != "$baseUrl/article")) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Missing fixture authentication")
        }
        if (session.uri == "/expired.mp4") {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Expired fixture URL")
        }
        if (session.uri == "/redirect.mp4") {
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply {
                addHeader("Location", "$baseUrl/sample.mp4")
            }
        }
        if (session.uri == "/credential-redirect.mp4") {
            return newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply {
                addHeader("Location", "http://localhost:$listeningPort/credential-target.mp4")
            }
        }
        when (session.uri) {
            "/article" -> return html("""
                <html><head><title>林间来信 · 媒体样本</title></head><body>
                <h1>沿着光，留住一段山野</h1>
                <img src="/image.png?first=1" alt="矿绿色画幅">
                <img data-src="/image.png?second=1" srcset="/image.png?small=1 240w, /image.png?large=1 480w">
                <video controls poster="/image.png"><source src="/sample.mp4" type="video/mp4"></video>
                <a href="/hls/index.m3u8">分片视频</a></body></html>
            """.trimIndent())
            "/dynamic" -> return html("""
                <html><head><title>动态播放器样本</title></head><body>
                <h1>动态播放器样本</h1><div id="player"></div>
                <script>setTimeout(()=>{const f=document.createElement('iframe');
                f.src='/player'; f.width='320';f.height='230';document.querySelector('#player').appendChild(f)},150)</script>
                </body></html>
            """.trimIndent())
            "/player" -> return html("""
                <html><body><video id="video" controls width="300"></video>
                <script>fetch('/sample.mp4?player=blob').then(r=>r.blob()).then(b=>{
                document.querySelector('video').src=URL.createObjectURL(b)})</script></body></html>
            """.trimIndent())
            "/dynamic-no-extension" -> return html("""
                <html><body><h1>无后缀媒体请求</h1><video id="video" controls></video>
                <script>fetch('/stream?id=opaque').then(r=>r.blob()).then(b=>{
                document.querySelector('video').src=URL.createObjectURL(b)})</script></body></html>
            """.trimIndent())
            "/feed.xml" -> return newFixedLengthResponse(Response.Status.OK, "application/rss+xml", """
                <?xml version="1.0" encoding="UTF-8"?><rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                <channel><title>林间来信 · 媒体测试</title><link>$baseUrl</link><description>公开测试素材</description>
                <item><title>沿着光，留住一段山野</title><link>$baseUrl/article</link>
                <description><![CDATA[<img src="$baseUrl/image.png">]]></description>
                <enclosure url="$baseUrl/sample.mp4" type="video/mp4"/>
                <media:content url="$baseUrl/hls/index.m3u8" type="application/vnd.apple.mpegurl"/>
                </item><item><title>动态播放器样本</title><link>$baseUrl/dynamic</link></item>
                </channel></rss>
            """.trimIndent())
            "/not-a-video.mp4" -> return html("<html><body>Login required</body></html>")
            "/live.m3u8" -> return newFixedLengthResponse(Response.Status.OK, "application/vnd.apple.mpegurl", """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:2
                #EXT-X-MEDIA-SEQUENCE:1
                #EXTINF:2,
                hls/segment00.ts
            """.trimIndent())
        }
        val path = when (session.uri) {
            "/credential-target.mp4" -> "sample.mp4"
            "/stream" -> "sample.mp4"
            "/hls/playlist" -> "hls/index.m3u8"
            else -> session.uri.removePrefix("/protected/").removePrefix("/")
        }
        if (path.contains("..")) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Invalid fixture path")
        val bytes = try {
            assetsContext.assets.open("rss-media-fixture/$path").use { it.readBytes() }
        } catch (_: java.io.IOException) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Fixture not found")
        }
        val mime = when (path.substringAfterLast('.')) {
            "mp4", "m4s" -> "video/mp4"
            "m3u8" -> "application/vnd.apple.mpegurl"
            "mpd" -> "application/dash+xml"
            "ts" -> "video/mp2t"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }
        val range = session.headers["range"]?.removePrefix("bytes=")?.substringBefore(',')
        val start = range?.substringBefore('-')?.toIntOrNull() ?: 0
        val end = range?.substringAfter('-')?.toIntOrNull()?.coerceAtMost(bytes.lastIndex) ?: bytes.lastIndex
        if (start !in bytes.indices || end < start) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "Invalid range").apply {
                addHeader("Content-Range", "bytes */${bytes.size}")
            }
        }
        return newFixedLengthResponse(
            if (range == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT,
            mime, ByteArrayInputStream(bytes, start, end - start + 1), (end - start + 1).toLong()
        ).apply {
            addHeader("Accept-Ranges", "bytes")
            if (range != null) addHeader("Content-Range", "bytes $start-$end/${bytes.size}")
            addHeader("Cache-Control", "no-store")
        }
    }

    private fun html(body: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
}
