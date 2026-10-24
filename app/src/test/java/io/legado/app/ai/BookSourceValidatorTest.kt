package io.legado.app.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.ai.source.BookSourceValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BookSourceValidator] 单测。
 *
 * 核心是守住 ERROR / WARNING 的分界（架构文档 Q6）：
 * 判据是「导入之后书源还能不能用」，而不是「AI 写得漂不漂亮」。
 * 分界一旦松动，要么用户被无谓地拦住，要么导入一堆废书源 —— 两头都很糟。
 */
class BookSourceValidatorTest {

    private fun parse(json: String): JsonObject =
        JsonParser.parseString(json).asJsonObject

    private fun validJson(): String = """
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
    fun `完整书源零问题`() {
        val result = BookSourceValidator.validate(parse(validJson()))

        assertTrue("不应有任何问题，实际：${result.issues}", result.isEmpty())
        assertFalse(result.hasError())
    }

    @Test
    fun `单文件JS书源不要求声明式核心规则`() {
        val result = BookSourceValidator.validate(
            parse(
                """{"bookSourceUrl":"https://example.com","bookSourceName":"单文件源","mainJs":"function search(){}"}"""
            )
        )

        assertFalse(result.hasError())
        assertFalse(result.warnings().any {
            it.messageRes == R.string.ai_issue_core_rules_missing ||
                    it.messageRes == R.string.ai_issue_search_url_missing
        })
    }

    @Test
    fun `缺少bookSourceUrl是ERROR`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceName":"x","ruleSearch":{"bookList":"a","name":"b","bookUrl":"c"},"searchUrl":"https://a.com"}""")
        )

        assertTrue(result.hasError())
        val issue = result.errors().first { it.field == "bookSourceUrl" }
        assertEquals(R.string.ai_issue_source_url_missing, issue.messageRes)
    }

    @Test
    fun `非http协议的bookSourceUrl是ERROR`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"ftp://example.com","bookSourceName":"x"}""")
        )

        val issue = result.errors().first { it.field == "bookSourceUrl" }
        assertEquals(R.string.ai_issue_source_url_invalid, issue.messageRes)
        assertEquals("ftp://example.com", issue.detail)
    }

    @Test
    fun `缺少bookSourceName是ERROR`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://example.com"}""")
        )

        assertTrue(result.errors().any { it.messageRes == R.string.ai_issue_source_name_missing })
    }

    @Test
    fun `越界的bookSourceType是ERROR`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","bookSourceType":9}""")
        )

        assertTrue(result.errors().any { it.messageRes == R.string.ai_issue_source_type_invalid })
    }

    @Test
    fun `合法范围内的bookSourceType不报错`() {
        BookSourceSchemaRange.forEach { type ->
            val result = BookSourceValidator.validate(
                parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","bookSourceType":$type}""")
            )
            assertFalse(
                "type=$type 应合法",
                result.errors().any { it.messageRes == R.string.ai_issue_source_type_invalid }
            )
        }
    }

    @Test
    fun `未知顶层字段只给WARNING不阻断导入`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","ruleSearch":{"bookList":"a","name":"b","bookUrl":"c"},"searchUrl":"https://a.com/s","magicField":1}""")
        )

        assertFalse("未知字段不该阻断导入", result.hasError())
        val warning = result.warnings().first { it.field == "magicField" }
        assertEquals(R.string.ai_issue_unknown_field, warning.messageRes)
    }

    @Test
    fun `规则字段不是对象时是ERROR`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","ruleSearch":"我是字符串"}""")
        )

        val issue = result.errors().first { it.field == "ruleSearch" }
        assertEquals(R.string.ai_issue_rule_not_object, issue.messageRes)
    }

    @Test
    fun `规则内未知子字段只给WARNING`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","searchUrl":"https://a.com/s","ruleSearch":{"bookList":"a","name":"b","bookUrl":"c","yyRule":"d"}}""")
        )

        assertFalse(result.hasError())
        assertTrue(result.warnings().any { it.field == "ruleSearch.yyRule" })
    }

    @Test
    fun `缺少关键规则子字段给WARNING`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","searchUrl":"https://a.com/s","ruleSearch":{"bookList":"a"}}""")
        )

        assertFalse(result.hasError())
        assertTrue(result.warnings().any { it.field == "ruleSearch.name" })
        assertTrue(result.warnings().any { it.field == "ruleSearch.bookUrl" })
    }

    @Test
    fun `三大核心规则全缺给WARNING而非ERROR`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x"}""")
        )

        assertFalse("纯发现页书源是合法存在，不能硬阻断", result.hasError())
        assertTrue(
            result.warnings().any { it.messageRes == R.string.ai_issue_core_rules_missing }
        )
    }

    @Test
    fun `有搜索规则却没有searchUrl给WARNING`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","ruleSearch":{"bookList":"a","name":"b","bookUrl":"c"}}""")
        )

        assertFalse(result.hasError())
        assertTrue(
            result.warnings().any { it.messageRes == R.string.ai_issue_search_url_missing }
        )
    }

    @Test
    fun `规则值不是原始类型给WARNING`() {
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"https://a.com","bookSourceName":"x","searchUrl":"https://a.com/s","ruleSearch":{"bookList":["a","b"],"name":"b","bookUrl":"c"}}""")
        )

        assertFalse(result.hasError())
        assertTrue(
            result.warnings().any {
                it.field == "ruleSearch.bookList" &&
                        it.messageRes == R.string.ai_issue_rule_value_not_primitive
            }
        )
    }

    @Test
    fun `问题detail不会超长`() {
        val longUrl = "ftp://" + "a".repeat(500)
        val result = BookSourceValidator.validate(
            parse("""{"bookSourceUrl":"$longUrl","bookSourceName":"x"}""")
        )

        val issue = result.errors().first { it.field == "bookSourceUrl" }
        assertTrue(
            "detail 必须被裁剪，实际长度 ${issue.detail.length}",
            issue.detail.length <= BookSourceValidator.MAX_DETAIL_CHARS + 1
        )
    }

    @Test
    fun `一次返回全部问题而不是遇错即停`() {
        val result = BookSourceValidator.validate(parse("""{"bookSourceType":99}"""))

        // 缺 url、缺 name、type 非法 —— 三条 ERROR 必须一次性给全
        assertTrue("应一次给出至少 3 条 ERROR，实际 ${result.errors()}", result.errors().size >= 3)
    }

    private companion object {
        val BookSourceSchemaRange = 0..4
    }
}
