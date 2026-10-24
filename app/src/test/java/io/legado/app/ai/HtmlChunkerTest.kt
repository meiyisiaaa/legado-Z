package io.legado.app.ai

import io.legado.app.ai.html.HtmlChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HtmlChunker] 单测。
 *
 * 重点守两条线：
 * 1. **长度硬保证**：无论输入多离谱，输出都不能超过 maxChars（否则 prompt 会被端点截断）；
 * 2. **结构合法**：截断后必须补全闭合标签，不能吐出半截标签。
 */
class HtmlChunkerTest {

    @Test
    fun `空白输入返回空串`() {
        assertEquals("", HtmlChunker.chunk(""))
        assertEquals("", HtmlChunker.chunk("   \n\t "))
    }

    @Test
    fun `去掉script与style噪音节点`() {
        val html = """
            <html><head><style>.a{color:red}</style></head>
            <body>
              <script>var a = 1;</script>
              <noscript>请开启脚本</noscript>
              <div class="book-list"><a href="/b/1">书名一</a></div>
            </body></html>
        """.trimIndent()

        val result = HtmlChunker.chunk(html)

        assertFalse("script 必须被删除", result.contains("var a = 1"))
        assertFalse("style 必须被删除", result.contains("color:red"))
        assertFalse("noscript 必须被删除", result.contains("请开启脚本"))
        assertTrue("正文必须保留", result.contains("书名一"))
        assertTrue("class 是推断选择器的关键，必须保留", result.contains("book-list"))
        assertTrue("href 必须保留", result.contains("/b/1"))
    }

    @Test
    fun `删除style与on事件属性但保留class与id`() {
        val html = """<div id="main" class="wrap" style="color:red" onclick="go()">x</div>"""

        val result = HtmlChunker.chunk(html)

        assertFalse(result.contains("style="))
        assertFalse(result.contains("onclick"))
        assertTrue(result.contains("id=\"main\""))
        assertTrue(result.contains("class=\"wrap\""))
    }

    @Test
    fun `注释被删除`() {
        val html = "<div><!-- 这是注释，纯浪费 token -->内容</div>"

        val result = HtmlChunker.chunk(html)

        assertFalse(result.contains("这是注释"))
        assertTrue(result.contains("内容"))
    }

    @Test
    fun `残缺标签会被二次解析补全`() {
        val result = HtmlChunker.chunk("<div><p>abc")

        assertTrue("Jsoup 应补全 p 闭合标签", result.contains("</p>"))
        assertTrue("Jsoup 应补全 div 闭合标签", result.contains("</div>"))
    }

    @Test
    fun `超长输入被截断且不超过上限`() {
        val item = """<div class="item"><a href="/book/1">一本很长名字的书籍标题</a></div>"""
        val html = "<html><body>" + item.repeat(2000) + "</body></html>"
        val limit = 1000

        val result = HtmlChunker.chunk(html, limit)

        assertTrue("长度必须收敛到上限内，实际 ${result.length}", result.length <= limit)
        assertTrue("截断后不应残留半截开标签", !Regex("<[a-zA-Z]*$").containsMatchIn(result))
    }

    @Test
    fun `低于下限的maxChars会被抬到下限`() {
        val html = "<div>" + "内容".repeat(5000) + "</div>"

        val result = HtmlChunker.chunk(html, 10)

        assertTrue(result.isNotEmpty())
        assertTrue(
            "小于 MIN_MAX_CHARS 时按下限处理，实际 ${result.length}",
            result.length <= HtmlChunker.MIN_MAX_CHARS
        )
    }

    @Test
    fun `未超限时原样返回清洗结果`() {
        val html = "<div class=\"a\">短内容</div>"

        val result = HtmlChunker.chunk(html, 10000)

        assertTrue(result.contains("短内容"))
        assertTrue(result.length < 100)
    }

    @Test
    fun `多余空白被压缩`() {
        val html = "<div>a</div>\n\n\n\n<div>b</div>"

        val result = HtmlChunker.chunk(html)

        assertFalse("不应出现连续空行", result.contains("\n\n"))
    }

    @Test
    fun `token估算中文按一字一token`() {
        assertEquals(0, HtmlChunker.estimateTokens(""))
        assertEquals(2, HtmlChunker.estimateTokens("中文"))
        assertEquals(1, HtmlChunker.estimateTokens("abcd"))
        assertEquals(2, HtmlChunker.estimateTokens("abcde"))
    }

    @Test
    fun `token估算支持集合求和`() {
        val total = HtmlChunker.estimateTokens(listOf("中文", "abcd"))

        assertEquals(3, total)
    }
}
