package io.legado.app.ai

import io.legado.app.ai.engine.SourceDebugEngine
import io.legado.app.ai.FakeAiChatClient
import io.legado.app.ai.PageFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SourceDebugEngine] 单测。
 *
 * 与生成引擎最大的区别是**调试引擎构造函数里没有 PageFetcher**（架构文档 Q5）。
 * `[调试引擎构造函数不含PageFetcher]` 用反射做回归守卫 —— 哪天有人"顺手"把抓取器加进来，
 * 这里立刻红灯。其余覆盖：诊断建议产出、未配置/空回复守卫。
 */
class SourceDebugEngineTest {

    private val validJson = AiTestFixtures.VALID_SOURCE_JSON

    @Test
    fun `调试产出诊断建议与修正JSON`() = runBlocking {
        val reply = "诊断：目录规则缺失，请补充 ruleToc。\n```json\n${validJson.trimIndent()}\n```"
        val client = FakeAiChatClient.replying(reply)
        val engine = SourceDebugEngine(client)

        val outcome = engine.debug(
            AiTestFixtures.options(),
            DebugInput(
                sourceJson = validJson,
                symptom = "目录为空",
                sampleHtml = "<ul><li>章</li></ul>"
            )
        )

        assertTrue(outcome.advice.contains("诊断"))
        assertTrue(outcome.sourceJson.isNotBlank())
        assertTrue(outcome.canImport())
    }

    @Test
    fun `调试引擎构造函数不含PageFetcher(Q5隐私守卫)`() {
        val type = SourceDebugEngine::class.java
        val ctor = type.declaredConstructors.first()

        assertFalse(
            "SourceDebugEngine 构造函数不应包含 PageFetcher 参数",
            ctor.parameterTypes.any { it == PageFetcher::class.java }
        )

        assertFalse(
            "SourceDebugEngine 不应持有 PageFetcher 属性",
            type.declaredFields.any { PageFetcher::class.java.isAssignableFrom(it.type) }
        )
    }

    @Test
    fun `未配置抛NotConfigured`() = runBlocking {
        val engine = SourceDebugEngine(FakeAiChatClient.replying(validJson))
        assertThrowsOf<AiError.NotConfigured> {
            engine.debug(
                AiTestFixtures.options().copy(apiKey = ""),
                DebugInput(sourceJson = validJson)
            )
        }
        Unit
    }

    @Test
    fun `模型返回空内容抛EmptyResponse`() = runBlocking {
        val engine = SourceDebugEngine(FakeAiChatClient.replying(""))
        assertThrowsOf<AiError.EmptyResponse> {
            engine.debug(
                AiTestFixtures.options(),
                DebugInput(sourceJson = validJson)
            )
        }
        Unit
    }

    @Test
    fun `调试诊断不自动二次修复且对提示中的凭据脱敏`() = runBlocking {
        val sourceSecret = "source-secret-987654"
        val sourceWithCredential = validJson.replace(
            "\"bookSourceName\": \"示例书源\",",
            "\"bookSourceName\": \"示例书源\",\n  \"header\": \"Authorization: Bearer $sourceSecret\","
        )
        val logSecret = "debug-secret-123456"
        val htmlSecret = "html-secret-123456"
        val client = FakeAiChatClient.replying("这不是合法书源 JSON", validJson)
        val engine = SourceDebugEngine(client)

        assertThrowsOf<AiError.JsonNotFound> {
            engine.debug(
                AiTestFixtures.options(autoRepair = true),
                DebugInput(
                    sourceJson = sourceWithCredential,
                    symptom = "目录为空",
                    sampleHtml = "<div>api_key=$htmlSecret</div>",
                    debugLogs = "Authorization: Bearer $logSecret"
                )
            )
        }

        assertEquals("Debug diagnosis must not retry a malformed response", 1, client.callCount)
        val submittedPrompt = client.allContent()
        assertTrue(submittedPrompt.contains("[REDACTED]"))
        assertFalse(submittedPrompt.contains(sourceSecret))
        assertFalse(submittedPrompt.contains(logSecret))
        assertFalse(submittedPrompt.contains(htmlSecret))
    }

    @Test
    fun `诊断成功时提示脱敏但本地结果保留原书源凭据`() = runBlocking {
        val sourceSecret = "source-secret-987654"
        val sourceWithCredential = validJson.replace(
            "\"bookSourceName\": \"示例书源\",",
            "\"bookSourceName\": \"示例书源\",\n  \"header\": \"Authorization: Bearer $sourceSecret\","
        )
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val outcome = SourceDebugEngine(client).debug(
            AiTestFixtures.options(),
            DebugInput(sourceJson = sourceWithCredential, debugLogs = "token=debug-secret")
        )

        assertFalse(client.allContent().contains(sourceSecret))
        assertFalse(client.allContent().contains("debug-secret"))
        assertTrue(outcome.sourceJson.contains(sourceSecret))
    }

    @Test
    fun `诊断结果省略mainJs时保留原单文件脚本`() = runBlocking {
        val script = "function search(key, page) { return []; }"
        val sourceWithScript = validJson.replace(
            "\"bookSourceName\": \"示例书源\",",
            "\"bookSourceName\": \"示例书源\",\n  \"mainJs\": ${com.google.gson.Gson().toJson(script)},"
        )
        val outcome = SourceDebugEngine(
            FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        ).debug(
            AiTestFixtures.options(),
            DebugInput(sourceJson = sourceWithScript)
        )

        assertTrue(outcome.sourceJson.contains(script))
    }
}
