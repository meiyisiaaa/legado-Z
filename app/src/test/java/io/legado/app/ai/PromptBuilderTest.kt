package io.legado.app.ai

import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.prompt.PromptBuilder
import io.legado.app.ai.prompt.PromptTemplates
import io.legado.app.ai.source.BookSourceSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PromptBuilder] 单测。
 *
 * 最重要的一组断言是**隐私红线**：prompt 里绝不能出现 apiKey / endpoint。
 * [PromptBuilder] 的方法签名里压根没有 `AiChatOptions`，本测试是对这条设计约束的回归保护 ——
 * 哪天有人"顺手"把配置传进来拼进 prompt，这里立刻红灯。
 */
class PromptBuilderTest {

    private val secretKey = "sk-super-secret-key-0123456789"
    private val secretEndpoint = "https://my-private-proxy.internal/v1"

    @Test
    fun `生成模式产出system与user两条消息`() {
        val messages = PromptBuilder.buildGenerate(
            GenerateInput(url = "https://example.com", intent = "做搜索规则"),
            "<div>片段</div>"
        )

        assertEquals(2, messages.size)
        assertEquals(ChatMessage.ROLE_SYSTEM, messages[0].role)
        assertEquals(ChatMessage.ROLE_USER, messages[1].role)
    }

    @Test
    fun `生成模式user消息包含意图_地址_HTML`() {
        val messages = PromptBuilder.buildGenerate(
            GenerateInput(url = "https://example.com/page", intent = "做搜索规则"),
            "<div class=\"list\">片段</div>"
        )
        val user = messages[1].content

        assertTrue(user.contains(PromptTemplates.USER_INTENT_LABEL))
        assertTrue(user.contains("做搜索规则"))
        assertTrue(user.contains(PromptTemplates.USER_PAGE_URL_LABEL))
        assertTrue(user.contains("https://example.com/page"))
        assertTrue(user.contains(PromptTemplates.USER_HTML_LABEL))
        assertTrue(user.contains("<div class=\"list\">片段</div>"))
        assertTrue(user.contains(PromptTemplates.USER_GENERATE_TAIL))
    }

    @Test
    fun `没有地址时不输出地址区块`() {
        val messages = PromptBuilder.buildGenerate(
            GenerateInput(intent = "只有意图"),
            "<div>片段</div>"
        )

        assertFalse(messages[1].content.contains(PromptTemplates.USER_PAGE_URL_LABEL))
    }

    @Test
    fun `空内容用占位符而不是留空标签块`() {
        val messages = PromptBuilder.buildGenerate(GenerateInput(intent = ""), "")

        assertTrue(messages[1].content.contains(PromptTemplates.PLACEHOLDER_NONE))
    }

    @Test
    fun `system消息带字段速查表`() {
        val system = PromptBuilder.buildGenerate(GenerateInput(), "").first().content

        assertTrue(system.contains(PromptTemplates.CHEATSHEET_TITLE))
        assertTrue(system.contains(BookSourceSchema.BOOK_SOURCE_URL))
        assertTrue(system.contains(BookSourceSchema.RULE_TOC))
    }

    @Test
    fun `字段速查表与schema同源不漂移`() {
        val cheatsheet = PromptTemplates.RULE_FIELD_CHEATSHEET

        BookSourceSchema.TOP_LEVEL_FIELDS.forEach {
            assertTrue("速查表缺少顶层字段 $it", cheatsheet.contains(it))
        }
        BookSourceSchema.RULE_OBJECT_FIELDS.forEach { (rule, fields) ->
            assertTrue("速查表缺少规则 $rule", cheatsheet.contains(rule))
            fields.forEach { field ->
                assertTrue("速查表缺少 $rule.$field", cheatsheet.contains(field))
            }
        }
    }

    @Test
    fun `调试模式包含书源JSON_症状_样例HTML`() {
        val messages = PromptBuilder.buildDebug(
            DebugInput(
                sourceJson = """{"bookSourceUrl":"https://a.com"}""",
                symptom = "目录为空"
            ),
            "<ul><li>章节</li></ul>"
        )
        val user = messages[1].content

        assertEquals(2, messages.size)
        assertTrue(user.contains(PromptTemplates.USER_SOURCE_JSON_LABEL))
        assertTrue(user.contains("bookSourceUrl"))
        assertTrue(user.contains(PromptTemplates.USER_SYMPTOM_LABEL))
        assertTrue(user.contains("目录为空"))
        assertTrue(user.contains(PromptTemplates.USER_SAMPLE_HTML_LABEL))
        assertTrue(user.contains("<li>章节</li>"))
    }

    @Test
    fun `修复模式回喂原始回复与问题清单`() {
        val validation = ValidationResult(
            listOf(
                ValidationIssue(
                    field = "bookSourceUrl",
                    level = IssueLevel.ERROR,
                    messageRes = 1,
                    detail = "缺失"
                )
            )
        )

        val messages = PromptBuilder.buildRepair("这不是 JSON", validation)
        val user = messages[1].content

        assertTrue(user.contains(PromptTemplates.USER_RAW_REPLY_LABEL))
        assertTrue(user.contains("这不是 JSON"))
        assertTrue(user.contains(PromptTemplates.USER_ISSUES_LABEL))
        assertTrue(user.contains("bookSourceUrl"))
        assertTrue(user.contains("ERROR"))
        assertTrue(user.contains("缺失"))
    }

    @Test
    fun `修复模式不带速查表以省token`() {
        val system = PromptBuilder.buildRepair("x", ValidationResult.EMPTY).first().content

        assertFalse(system.contains(PromptTemplates.CHEATSHEET_TITLE))
    }

    @Test
    fun `任何模式的prompt都不含apiKey与endpoint`() {
        val generate = PromptBuilder.buildGenerate(
            GenerateInput(url = "https://example.com", intent = "意图"),
            "<div>片段</div>"
        )
        val debug = PromptBuilder.buildDebug(
            DebugInput(sourceJson = "{}", symptom = "症状"),
            "<div>片段</div>"
        )
        val repair = PromptBuilder.buildRepair("坏文本", ValidationResult.EMPTY)

        (generate + debug + repair).forEach { message ->
            assertFalse(
                "prompt 泄漏了 apiKey：${message.role}",
                message.content.contains(secretKey)
            )
            assertFalse(
                "prompt 泄漏了 endpoint：${message.role}",
                message.content.contains(secretEndpoint)
            )
            assertFalse(
                "prompt 出现了 Authorization 头",
                message.content.contains("Authorization")
            )
        }
    }
}
