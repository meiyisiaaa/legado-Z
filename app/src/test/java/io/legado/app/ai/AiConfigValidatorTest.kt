package io.legado.app.ai

import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AiConfigValidator] 单测。
 *
 * endpoint 拼接是这类功能的头号 bug 源：用户会填 `/v1`、`/v1/`、
 * 甚至直接把 `/v1/chat/completions` 整个粘进来。三种写法必须都能用，
 * 且规范化后再拼接不能出现 `//` 或重复路径。
 */
class AiConfigValidatorTest {

    /* ---------------- normalizeEndpoint ---------------- */

    @Test
    fun `规范化去掉首尾空白`() {
        assertEquals(
            "https://api.openai.com/v1",
            AiConfigValidator.normalizeEndpoint("  https://api.openai.com/v1  ")
        )
    }

    @Test
    fun `规范化去掉尾斜杠`() {
        assertEquals(
            "https://api.openai.com/v1",
            AiConfigValidator.normalizeEndpoint("https://api.openai.com/v1/")
        )
    }

    @Test
    fun `规范化剥掉用户多粘的chat_completions后缀`() {
        assertEquals(
            "https://api.openai.com/v1",
            AiConfigValidator.normalizeEndpoint("https://api.openai.com/v1/chat/completions")
        )
        assertEquals(
            "https://api.openai.com/v1",
            AiConfigValidator.normalizeEndpoint("https://api.openai.com/v1/chat/completions/")
        )
    }

    @Test
    fun `规范化空串仍是空串`() {
        assertEquals("", AiConfigValidator.normalizeEndpoint(""))
        assertEquals("", AiConfigValidator.normalizeEndpoint("   "))
    }

    @Test
    fun `三种写法规范化后拼出同一个地址`() {
        val expected = "https://api.openai.com/v1/chat/completions"
        listOf(
            "https://api.openai.com/v1",
            "https://api.openai.com/v1/",
            "https://api.openai.com/v1/chat/completions"
        ).forEach { raw ->
            val options = AiChatOptions(endpoint = AiConfigValidator.normalizeEndpoint(raw))
            assertEquals("原始写法 $raw 拼接结果不对", expected, options.chatCompletionsUrl())
        }
    }

    /* ---------------- validate ---------------- */

    @Test
    fun `完整配置零问题`() {
        val result = AiConfigValidator.validate(AiTestFixtures.options())

        assertTrue("不该有问题，实际：${result.issues}", result.isEmpty())
    }

    @Test
    fun `endpoint为空是ERROR`() {
        val result = AiConfigValidator.validate(AiTestFixtures.options().copy(endpoint = ""))

        assertTrue(result.hasError())
        assertTrue(
            result.errors().any {
                it.field == AiConfigValidator.FIELD_ENDPOINT &&
                        it.messageRes == R.string.ai_issue_endpoint_empty
            }
        )
    }

    @Test
    fun `endpoint缺协议头是ERROR`() {
        val result = AiConfigValidator.validate(
            AiTestFixtures.options().copy(endpoint = "api.openai.com/v1")
        )

        assertTrue(
            result.errors().any { it.messageRes == R.string.ai_issue_endpoint_scheme }
        )
    }

    @Test
    fun `http协议也被接受`() {
        val result = AiConfigValidator.validate(
            AiTestFixtures.options().copy(endpoint = "http://127.0.0.1:11434/v1")
        )

        assertFalse("本地部署常用 http，不能拦", result.hasError())
    }

    @Test
    fun `apiKey为空是ERROR`() {
        val result = AiConfigValidator.validate(AiTestFixtures.options().copy(apiKey = "  "))

        assertTrue(
            result.errors().any { it.messageRes == R.string.ai_issue_api_key_empty }
        )
    }

    @Test
    fun `model为空是ERROR`() {
        val result = AiConfigValidator.validate(AiTestFixtures.options().copy(model = ""))

        assertTrue(
            result.errors().any { it.messageRes == R.string.ai_issue_model_empty }
        )
    }

    @Test
    fun `temperature越界是ERROR`() {
        listOf(-0.1f, 2.1f).forEach { value ->
            val result = AiConfigValidator.validate(
                AiTestFixtures.options().copy(temperature = value)
            )
            assertTrue(
                "temperature=$value 应报错",
                result.errors().any { it.messageRes == R.string.ai_issue_temperature_range }
            )
        }
    }

    @Test
    fun `temperature边界值合法`() {
        listOf(AiChatOptions.MIN_TEMPERATURE, AiChatOptions.MAX_TEMPERATURE).forEach { value ->
            val result = AiConfigValidator.validate(
                AiTestFixtures.options().copy(temperature = value)
            )
            assertFalse("temperature=$value 是边界内合法值", result.hasError())
        }
    }

    @Test
    fun `maxTokens越界是ERROR`() {
        listOf(AiChatOptions.MIN_MAX_TOKENS - 1, AiChatOptions.MAX_MAX_TOKENS + 1).forEach {
            val result = AiConfigValidator.validate(AiTestFixtures.options().copy(maxTokens = it))
            assertTrue(
                "maxTokens=$it 应报错",
                result.errors().any { issue -> issue.messageRes == R.string.ai_issue_max_tokens_range }
            )
        }
    }

    @Test
    fun `timeoutSec越界是ERROR`() {
        listOf(AiChatOptions.MIN_TIMEOUT_SEC - 1, AiChatOptions.MAX_TIMEOUT_SEC + 1).forEach {
            val result = AiConfigValidator.validate(AiTestFixtures.options().copy(timeoutSec = it))
            assertTrue(
                "timeoutSec=$it 应报错",
                result.errors().any { issue -> issue.messageRes == R.string.ai_issue_timeout_range }
            )
        }
    }

    @Test
    fun `htmlMaxChars越界是ERROR`() {
        listOf(
            AiChatOptions.MIN_HTML_MAX_CHARS - 1,
            AiChatOptions.MAX_HTML_MAX_CHARS + 1
        ).forEach {
            val result =
                AiConfigValidator.validate(AiTestFixtures.options().copy(htmlMaxChars = it))
            assertTrue(
                "htmlMaxChars=$it 应报错",
                result.errors()
                    .any { issue -> issue.messageRes == R.string.ai_issue_html_max_chars_range }
            )
        }
    }

    @Test
    fun `多个问题一次全给`() {
        val result = AiConfigValidator.validate(
            AiChatOptions(endpoint = "", apiKey = "", model = "", temperature = 9f)
        )

        assertTrue("应一次给出至少 4 条 ERROR，实际 ${result.errors()}", result.errors().size >= 4)
    }

    @Test
    fun `firstErrorDetail优先带上字段名`() {
        val result = AiConfigValidator.validate(AiTestFixtures.options().copy(endpoint = ""))

        assertTrue(result.firstErrorDetail().contains(AiConfigValidator.FIELD_ENDPOINT))
    }
}
