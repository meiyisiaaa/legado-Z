package io.legado.app.model.rss.media

import io.legado.app.data.entities.RssArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssMediaImageSelectionTest {
    private val article = RssArticle(origin = "https://feed.example/rss", link = "https://site.example/posts/1")

    @Test
    fun `original replaces lazy preview srcset and repeated feed cover without changing signed URL`() {
        val original = "https://cdn.example/photo.jpg?width=2400&token=fixture%2Bvalue&expires=9999"
        val input = article.copy(image = "/preview.jpg", description = """<img src="/preview.jpg">""")
        val html = """<img width="1" height="1" src="/preview.jpg" data-src="/lazy.jpg" srcset="/small.jpg 400w, /large.jpg 1200w"
            data-srcset="/retina.jpg 3x" data-original="$original" alt="Article photo">"""
        assertEquals(listOf(original), RssMediaResolver.extract(input, html, input.link).map { it.url })
        assertEquals(setOf("preview.jpg", "lazy.jpg", "small.jpg", "large.jpg", "retina.jpg"),
            RssMediaResolver.imageUrlsToExclude(input, html, input.link).map { it.substringAfterLast('/') }.toSet())
    }

    @Test
    fun `WordPress and explicit full image attributes replace previews with their signed originals`() {
        listOf("data-orig-file", "data-large_image", "data-full-src", "data-full-url").forEach { attribute ->
            val original = "https://cdn.example/original.jpg?auth=fixture%2Fkey&width=2048"
            val html = """<img src="/preview.jpg" srcset="/preview-large.jpg 1200w" $attribute="$original">"""
            assertEquals(attribute, listOf(original),
                RssMediaResolver.extract(article, html, article.link).map { it.url })
            assertEquals(attribute, setOf("https://site.example/preview.jpg", "https://site.example/preview-large.jpg"),
                RssMediaResolver.imageUrlsToExclude(article, html, article.link))
        }
    }

    @Test
    fun `picture and srcset choose highest supplied resolution and retain separate article images`() {
        val html = """
            <picture><source type="image/avif" srcset="/photo-800.avif 800w, /photo-2400.avif 2400w">
                <img src="/photo-400.jpg" srcset="/photo-1200.jpg 1200w"></picture>
            <img width="600" src="/second.jpg" data-srcset="/second-1.jpg 1x, /second-3.jpg 3x, /second-2.jpg 2x">
            <img width="40" height="40" src="/comic-panel.png">
            <img width="1200" height="1" src="/wide-strip.png">
            <meta property="og:image" content="/og-original.jpg">
        """.trimIndent()
        val result = RssMediaResolver.extract(article, html, article.link)
        assertEquals(setOf("photo-2400.avif", "second-3.jpg", "comic-panel.png", "wide-strip.png", "og-original.jpg"),
            result.map { it.url.substringAfterLast('/') }.toSet())
        assertEquals("image/avif", result.single { it.url.endsWith(".avif") }.mimeType)
    }

    @Test
    fun `semantic auxiliary assets and tracking pixels are removed without a small image threshold`() {
        val html = """
            <a class="site-logo" href="/brand.png"><img src="/brand.png"></a>
            <div class="author-avatar"><img src="/person.jpg"></div>
            <nav><a href="/next.png"><img src="/next.png"></a></nav>
            <button><img src="/play.png"></button>
            <img src="/transparent-tracker.gif" width="1" height="1">
            <img src="/icons/share.svg"><img src="/favicon.ico"><img src="/pixel.gif">
            <img src="/photos/logo-design-case.jpg" width="48" height="48">
            <img src="/chapter/01.jpg" width="80" height="120">
        """.trimIndent()
        assertEquals(setOf("logo-design-case.jpg", "01.jpg"),
            RssMediaResolver.extract(article, html, article.link).map { it.url.substringAfterLast('/') }.toSet())
        val excluded = RssMediaResolver.imageUrlsToExclude(article, html, article.link)
        assertTrue("https://site.example/brand.png" in excluded)
        assertTrue("https://site.example/next.png" in excluded)
        assertTrue("https://site.example/person.jpg" in excluded)
    }

    @Test
    fun `site chrome with opaque image URLs is excluded while article and main header images remain`() {
        val html = """
            <header><img src="/media/a37"></header>
            <footer><img src="/media/b28"></footer>
            <aside><img src="/media/c19"></aside>
            <div role="banner"><img src="/media/d10"></div>
            <div role="contentinfo"><img src="/media/e42"></div>
            <div role="complementary"><img src="/media/f53"></div>
            <article><header><img src="/media/article-hero"></header></article>
            <main><header><img src="/media/main-hero"></header></main>
        """.trimIndent()
        assertEquals(setOf("https://site.example/media/article-hero", "https://site.example/media/main-hero"),
            RssMediaResolver.extract(article, html, article.link).map { it.url }.toSet())
        assertEquals(setOf("a37", "b28", "c19", "d10", "e42", "f53"),
            RssMediaResolver.imageUrlsToExclude(article, html, article.link).map { it.substringAfterLast('/') }.toSet())
    }

    @Test
    fun `invalid originals and malformed srcset fall back to real relative source`() {
        val result = RssMediaResolver.extract(article, """
            <img data-original="javascript:alert(1)" src="../real.jpg"
                srcset="/negative.jpg -2x, /invalid.jpg manyw, data:image/png;base64,AAAA 3x">
            <picture><source srcset="/alone-small.webp 100w, /alone-large.webp 900w"></picture>
            <img data-original="data:image/png;base64,AAAA" data-src="/lazy-real.jpg" src="/spacer.gif">
        """.trimIndent(), article.link)
        assertEquals(setOf("https://site.example/real.jpg", "https://site.example/alone-large.webp", "https://site.example/lazy-real.jpg"),
            result.map { it.url }.toSet())
    }

    @Test
    fun `plain prose and a chromecast sentence never become video URLs`() {
        val input = article.copy(type = 2, description = "chromecast.\n这是一段正文介绍\n" + "word ".repeat(2000),
            content = """../clip.mp4?token=fixture%2Fvalue,{"headers":{"Authorization":"fixture token"}}""")
        val result = RssMediaResolver.extract(input, "", input.link)
        assertEquals(2, result.size)
        assertTrue(result.any { it.url == "https://site.example/clip.mp4?token=fixture%2Fvalue" })
        assertFalse(result.any { it.url.contains("chromecast") || it.url.contains("word") || it.url.contains("%20") })
    }

    @Test
    fun `type one links and public Google feed images remain downloadable`() {
        val image = "https://lh3.googleusercontent.com/proxy/fixture?auth=public%2Fimage&w=1800"
        val input = article.copy(type = 1, link = "https://cdn.example/download?id=7&token=fixture", image = image)
        assertEquals(setOf(input.link, image), RssMediaResolver.extract(input, "", input.link).map { it.url }.toSet())
        assertFalse(RssMediaResolver.isAuxiliaryImageUrl(image))
        assertTrue(RssMediaResolver.isAuxiliaryImageUrl("https://cdn.example/avatars/user.jpg?auth=fixture"))
        assertFalse(RssMediaResolver.isAuxiliaryImageUrl("https://cdn.example/photos/logo-design-case.jpg"))
    }
}
