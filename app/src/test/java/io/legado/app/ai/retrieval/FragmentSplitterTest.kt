package io.legado.app.ai.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FragmentSplitter] 的行为回归。
 *
 * 最关键的不变式是**「片段必须是原文的精确连续子串」**：
 * 一旦 charStart 与 text 对不上，UI 的「跳转原文」和引用标注就会指到错误位置，
 * 而这种错误在人工验收时极难发现，只能靠断言守住。
 */
class FragmentSplitterTest {

    @Test
    fun `空白正文返回空列表`() {
        assertEquals(emptyList<TextFragment>(), FragmentSplitter.split(0, "第一章", ""))
        assertEquals(emptyList<TextFragment>(), FragmentSplitter.split(0, "第一章", "  \n\n  "))
    }

    @Test
    fun `短段落合并为一个片段`() {
        val text = "第一段。\n第二段。\n第三段。"
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 400)
        assertEquals(1, fragments.size)
        assertEquals(0, fragments[0].charStart)
        assertEquals(text, fragments[0].text)
    }

    @Test
    fun `段落总长超上限时按段落边界分组`() {
        val para = "甲".repeat(60)
        val text = (1..5).joinToString("\n") { para }
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 130)

        assertTrue("应切出多个片段，实际 ${fragments.size}", fragments.size > 1)
        fragments.forEach {
            assertTrue("片段超出上限：${it.length}", it.length <= 130)
        }
        assertAllAreExactSubstrings(text, fragments)
    }

    @Test
    fun `超长单段落按定长窗口切分且带重叠`() {
        val text = "乙".repeat(1000)
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 400, overlap = 80)

        assertTrue("应切出多个窗口，实际 ${fragments.size}", fragments.size >= 3)
        assertEquals(0, fragments[0].charStart)
        // 步长 = maxChars - overlap = 320
        assertEquals(320, fragments[1].charStart)
        assertEquals(640, fragments[2].charStart)
        fragments.forEach { assertTrue(it.length <= 400) }
        assertAllAreExactSubstrings(text, fragments)
    }

    @Test
    fun `窗口重叠保证跨界关键词仍可被完整召回`() {
        // 关键词落在 [398, 402)，恰好被第一个窗口 [0, 400) 从中间劈开
        val head = "丙".repeat(398)
        val text = head + "无双剑法" + "丁".repeat(600)
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 400, overlap = 80)

        assertTrue("第一个窗口本就不该包含完整关键词", !fragments[0].text.contains("无双剑法"))
        assertTrue(
            "重叠失效：没有任何片段完整包含跨界关键词",
            fragments.any { it.text.contains("无双剑法") }
        )
    }

    @Test
    fun `末尾窗口不会越界且不产生空片段`() {
        val text = "戊".repeat(405)
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 400, overlap = 80)

        assertTrue(fragments.isNotEmpty())
        assertTrue("末片段越界", fragments.last().charEnd <= text.length)
        assertTrue("产生了空片段", fragments.none { it.text.isBlank() })
    }

    @Test
    fun `片段携带章节下标与标题`() {
        val fragments = FragmentSplitter.split(7, "第八章 归途", "回家的路很长。")
        assertEquals(1, fragments.size)
        assertEquals(7, fragments[0].chapterIndex)
        assertEquals("第八章 归途", fragments[0].chapterTitle)
    }

    @Test
    fun `兼容CRLF换行且不产生空段`() {
        val text = "第一段。\r\n\r\n第二段。"
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 400)
        assertEquals(1, fragments.size)
        assertTrue(fragments[0].text.contains("第一段。"))
        assertTrue(fragments[0].text.contains("第二段。"))
        assertAllAreExactSubstrings(text, fragments)
    }

    @Test
    fun `maxChars被钳制到下限避免片段爆炸`() {
        val text = "己".repeat(200)
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 1, overlap = 0)
        // 钳制到 MIN_MAX_CHARS = 40 → 200 / 40 = 5 片
        assertEquals(5, fragments.size)
        assertAllAreExactSubstrings(text, fragments)
    }

    @Test
    fun `overlap不小于maxChars时不会死循环`() {
        val text = "庚".repeat(300)
        val fragments = FragmentSplitter.split(0, "第一章", text, maxChars = 100, overlap = 999)
        // overlap 被钳到 maxChars / 2 = 50，步长 50 → 起点 0/50/.../250
        assertTrue("片段数异常：${fragments.size}", fragments.size in 2..8)
        assertAllAreExactSubstrings(text, fragments)
    }

    /** 核心不变式：每个片段都必须能用 charStart 在原文中原样取回。 */
    private fun assertAllAreExactSubstrings(text: String, fragments: List<TextFragment>) {
        fragments.forEach { fragment ->
            assertEquals(
                "片段 charStart=${fragment.charStart} 与原文对不上",
                text.substring(fragment.charStart, fragment.charEnd),
                fragment.text
            )
        }
    }
}
