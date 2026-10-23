package io.legado.app.model.rss

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.model.rss.media.RssMediaResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssParserMediaTest {
    @Test
    fun defaultParserRetainsMultipleEnclosuresAndMediaContentAcrossFavourites() {
        val xml = """
            <rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
              <title>Media post</title><link>https://site.example/post</link>
              <enclosure url="/photo.jpg" type="image/jpeg" length="12"/>
              <enclosure url="/video.mp4" type="video/mp4" length="34"/>
              <media:content url="https://cdn.example/stream.mpd" type="application/dash+xml" fileSize="56" medium="video"/>
            </item><item><title>Text post</title><link>https://site.example/text</link></item></channel></rss>
        """.trimIndent()
        val articles = RssParserDefault.parseXML("sort", xml, "https://feed.example/rss").first
        assertEquals(2, articles.size)
        val restored = articles.first().toStar().toRssArticle()
        val media = RssMediaResolver.extract(restored, "", restored.link)
        assertEquals(3, media.size)
        assertEquals(12L, media.single { it.kind == "image" }.size)
        assertEquals(2, media.count { it.kind == "video" })
        assertTrue(media.any { it.url == "https://cdn.example/stream.mpd" && it.size == 56L })
        assertEquals("[]", articles.last().enclosureJson)
    }
}
