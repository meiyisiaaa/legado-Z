package io.legado.app.model.rss.media

import io.legado.app.data.entities.RssArticle
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssMediaResolverTest {
    private val article = RssArticle(origin = "https://feed.example/rss", link = "https://site.example/posts/1")

    @Test
    fun `cross origin media retains target options without inheriting source credentials`() {
        val inherited = mapOf("Authorization" to "source", "Cookie" to "source=private",
            "X-Source-Key" to "private", "User-Agent" to "fixture", "Referer" to article.link)
        val rule = """https://media.example/v.mp4,{"headers":{"Authorization":"target","Cookie":"target=explicit","X-Target-Key":"public-fixture"}}"""
        assertEquals(inherited, RssMediaResolver.sourceHeaders(article.origin,
            "https://feed.example/media.mp4", inherited, rule))
        val cross = RssMediaResolver.sourceHeaders(article.origin, "https://media.example/v.mp4", inherited, rule)
        assertEquals("target", cross["Authorization"])
        assertEquals("target=explicit", cross["Cookie"])
        assertEquals("public-fixture", cross["X-Target-Key"])
        assertFalse(cross.containsKey("X-Source-Key"))
        assertEquals("fixture", cross["User-Agent"])
        assertEquals(mapOf("User-Agent" to "fixture", "Referer" to article.link),
            RssMediaResolver.sourceHeaders(article.origin, "https://media.example/v.mp4", inherited, "https://media.example/v.mp4"))
    }

    @Test
    fun `cross origin request keeps URL options after source URL JS was already evaluated`() {
        val options = RssMediaResolver.targetOptions("""https://media.example/v.mp4,{"method":"POST","body":"fixture=1","headers":{"X-Target":"fixture"},"js":"source.getKey()","charset":"UTF-8","retry":2}""")!!
        assertFalse(options.has("js"))
        assertEquals("POST", options.get("method").asString)
        assertEquals("fixture=1", options.get("body").asString)
        assertEquals("fixture", options.getAsJsonObject("headers").get("X-Target").asString)
        assertEquals(2, options.get("retry").asInt)
    }

    @Test
    fun `captured credentials stay on their actual request origin after URL rules`() {
        val candidate = RssMediaCandidate(article, "https://media.example/clip.mp4", "video",
            headers = mapOf("Authorization" to "fixture", "X-Token" to "fixture", "Referer" to article.link))
        assertEquals(candidate.headers, RssMediaResolver.capturedHeaders(candidate, "https://media.example/rewritten.mp4"))
        listOf("http://media.example/clip.mp4", "https://media.example:8443/clip.mp4", "https://other.example/clip.mp4").forEach {
            assertEquals(mapOf("Referer" to article.link), RssMediaResolver.capturedHeaders(candidate, it))
        }
    }

    @Test
    fun `description content and supplied DOM are combined without duplicate URLs`() {
        val input = article.copy(
            description = """<img data-src="/a.jpg"><img src="/same.png">""",
            content = """<video poster="/poster.jpg"><source src="../clip.m3u8" type="application/x-mpegURL"></video>"""
        )
        val result = RssMediaResolver.extract(input, """
            <img src="/same.png" srcset="/small.webp 1x, /large.webp 2x" data-original="/original.jpg">
            <a href="/manifest.mpd">DASH</a><a href="/page">page</a>
        """.trimIndent(), input.link)
        assertEquals(4, result.size)
        assertEquals(0, result.count { it.url.endsWith("/same.png") })
        assertTrue(result.any { it.url.endsWith("/original.jpg") })
        assertEquals("video", result.single { it.url.endsWith("/clip.m3u8") }.kind)
        assertEquals("https://site.example/poster.jpg", result.single { it.url.endsWith("/clip.m3u8") }.thumbnail)
        assertTrue(result.any { it.url.endsWith("/manifest.mpd") && it.kind == "video" })
        assertFalse(result.any { it.url.endsWith("/page") })
    }

    @Test
    fun `feed metadata survives favourites and is retained when HTML repeats media`() {
        val attachments = listOf(RssMediaAttachment("/movie", "video/mp4", 8192, "Episode", "/cover.jpg"))
        val input = article.copy(enclosureJson = GSON.toJson(attachments))
        val restored = input.toStar().toRssArticle()
        val result = RssMediaResolver.extract(restored, """<video src="/movie"></video>""", input.link)
        assertEquals(input.enclosureJson, restored.enclosureJson)
        assertEquals(1, result.size)
        assertEquals(8192L, result.single().size)
        assertEquals("video/mp4", result.single().mimeType)
        assertEquals("Episode", result.single().label)
    }

    @Test
    fun `non HTTP schemes HTML types and data srcset candidates are excluded`() {
        val input = article.copy(enclosureJson = GSON.toJson(listOf(
            RssMediaAttachment("/fake.mp4", "text/html"),
            RssMediaAttachment("/audio.mp3", "audio/mpeg")
        )))
        val result = RssMediaResolver.extract(input, """
            <img src="javascript:alert(1)"><img src="blob:https://site.example/id">
            <img srcset="data:image/png;base64,AAAA 1x, /real.png 2x">
            <video src="/login.mp4" type="text/html"></video>
            <a href="/landing.html" type="text/html">open</a>
        """.trimIndent(), input.link)
        assertEquals(listOf("https://site.example/real.png"), result.map { it.url })
    }

    @Test
    fun `URL options are preserved separately from the canonical URL`() {
        val input = article.copy(type = 2, content = """../clip.mp4,{"headers":{"Referer":"https://site.example"}}""")
        val result = RssMediaResolver.extract(input, "", input.link)
        val clip = result.single { it.url.endsWith("/clip.mp4") }
        assertEquals("https://site.example/clip.mp4", clip.url)
        assertTrue(clip.requestUrl.endsWith(""",{"headers":{"Referer":"https://site.example"}}"""))
    }

    @Test
    fun `stable IDs distinguish origin and link boundaries`() {
        val first = RssMediaCandidate(RssArticle(origin = "ab", link = "c"), "https://x/a.jpg", "image")
        val second = RssMediaCandidate(RssArticle(origin = "a", link = "bc"), "https://x/a.jpg", "image")
        assertNotEquals(first.id, second.id)
        assertEquals(first.id, first.copy(label = "different title").id)
    }

    @Test
    fun `rule parser attachment extraction retains MIME and declared size`() {
        val items = RssMediaResolver.extractAttachments("""
            <item><enclosure url="/a.jpg" type="image/jpeg" length="32"/>
            <media:content url="/v.mpd" type="application/dash+xml" fileSize="64" medium="video"/></item>
        """.trimIndent())
        assertEquals(2, items.size)
        assertEquals(32L, items.first().size)
        assertEquals("application/dash+xml", items.last().mimeType)
        assertEquals(64L, items.last().size)
    }
}
