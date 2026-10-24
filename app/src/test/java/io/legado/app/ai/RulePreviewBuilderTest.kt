package io.legado.app.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.ai.source.RulePreviewBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RulePreviewBuilder] 单测。
 *
 * 预览是用户唯一会认真看的东西，所以两件事必须稳：
 * 分组/字段**顺序固定**（否则两次生成没法肉眼 diff），以及**不静默吞字段**
 * （预览里没有、导入后却有，或者反过来，都会变成很难查的 bug 报告）。
 */
class RulePreviewBuilderTest {

    private fun parse(json: String): JsonObject =
        JsonParser.parseString(json).asJsonObject

    private fun fullJson(): String = """
        {
          "bookSourceUrl": "https://example.com",
          "bookSourceName": "示例书源",
          "bookSourceType": 0,
          "searchUrl": "https://example.com/s?q={{key}}",
          "ruleSearch": {
            "bookList": ".book-list li",
            "name": "a@text",
            "bookUrl": "a@href"
          },
          "ruleToc": {
            "chapterList": ".chapter-list li",
            "chapterName": "a@text",
            "chapterUrl": "a@href"
          },
          "ruleContent": {
            "content": "#content@html"
          }
        }
    """.trimIndent()

    @Test
    fun `空对象返回空预览`() {
        val preview = RulePreviewBuilder.build(parse("{}"))

        assertTrue(preview.isEmpty())
        assertTrue(preview.groups.isEmpty())
    }

    @Test
    fun `分组顺序固定为基础_搜索_目录_正文`() {
        val preview = RulePreviewBuilder.build(parse(fullJson()))

        val titles = preview.groups.map { it.titleRes }
        assertEquals(
            listOf(
                R.string.ai_preview_group_base,
                R.string.ai_preview_group_search,
                R.string.ai_preview_group_toc,
                R.string.ai_preview_group_content
            ),
            titles
        )
    }

    @Test
    fun `空组被丢弃不占屏幕`() {
        val preview = RulePreviewBuilder.build(parse(fullJson()))

        assertFalse(
            "没有 ruleExplore，不该出现发现分组",
            preview.groups.any { it.titleRes == R.string.ai_preview_group_explore }
        )
        assertFalse(
            "没有 ruleBookInfo，不该出现详情分组",
            preview.groups.any { it.titleRes == R.string.ai_preview_group_book_info }
        )
    }

    @Test
    fun `基础组只列出有值的字段`() {
        val preview = RulePreviewBuilder.build(parse(fullJson()))

        val base = preview.groups.first { it.titleRes == R.string.ai_preview_group_base }
        assertEquals(
            listOf("bookSourceUrl", "bookSourceName", "bookSourceType", "searchUrl"),
            base.entries.map { it.key }
        )
        assertEquals("https://example.com", base.entries.first().value)
    }

    @Test
    fun `规则组字段顺序跟随schema而非json书写顺序`() {
        // 故意把 bookUrl 写在最前面，输出仍应是 schema 顺序 bookList -> name -> bookUrl
        val json = """
            {
              "bookSourceUrl": "https://a.com",
              "bookSourceName": "x",
              "ruleSearch": { "bookUrl": "a@href", "name": "a@text", "bookList": ".list li" }
            }
        """.trimIndent()

        val preview = RulePreviewBuilder.build(parse(json))
        val search = preview.groups.first { it.titleRes == R.string.ai_preview_group_search }

        assertEquals(listOf("bookList", "name", "bookUrl"), search.entries.map { it.key })
    }

    @Test
    fun `schema之外的多余字段仍会展示在组尾`() {
        val json = """
            {
              "bookSourceUrl": "https://a.com",
              "bookSourceName": "x",
              "ruleSearch": { "bookList": ".list li", "zzUnknown": "会被导入丢弃但必须让用户看见" }
            }
        """.trimIndent()

        val preview = RulePreviewBuilder.build(parse(json))
        val search = preview.groups.first { it.titleRes == R.string.ai_preview_group_search }

        assertEquals(listOf("bookList", "zzUnknown"), search.entries.map { it.key })
    }

    @Test
    fun `过长的值被截断`() {
        val longValue = "x".repeat(500)
        val json = """
            {
              "bookSourceUrl": "https://a.com",
              "bookSourceName": "x",
              "ruleContent": { "content": "$longValue" }
            }
        """.trimIndent()

        val preview = RulePreviewBuilder.build(parse(json))
        val entry = preview.groups
            .first { it.titleRes == R.string.ai_preview_group_content }
            .entries
            .first()

        assertEquals(RulePreviewBuilder.MAX_VALUE_CHARS + 1, entry.value.length)
        assertTrue(entry.value.endsWith("…"))
    }

    @Test
    fun `换行被压成单行`() {
        val json = """
            {
              "bookSourceUrl": "https://a.com",
              "bookSourceName": "x",
              "exploreUrl": "分类一::https://a.com/1\n分类二::https://a.com/2"
            }
        """.trimIndent()

        val preview = RulePreviewBuilder.build(parse(json))
        val entry = preview.groups
            .first { it.titleRes == R.string.ai_preview_group_base }
            .entries
            .first { it.key == "exploreUrl" }

        assertFalse(entry.value.contains("\n"))
        assertTrue(entry.value.contains("分类一"))
        assertTrue(entry.value.contains("分类二"))
    }

    @Test
    fun `非对象的规则字段不会让预览崩溃`() {
        val json = """{"bookSourceUrl":"https://a.com","bookSourceName":"x","ruleSearch":"字符串"}"""

        val preview = RulePreviewBuilder.build(parse(json))

        assertFalse(
            preview.groups.any { it.titleRes == R.string.ai_preview_group_search }
        )
        assertTrue(preview.groups.any { it.titleRes == R.string.ai_preview_group_base })
    }
}
