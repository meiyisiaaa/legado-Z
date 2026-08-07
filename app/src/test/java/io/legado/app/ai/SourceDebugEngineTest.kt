package io.legado.app.ai

import io.legado.app.ai.engine.SourceDebugEngine
import io.legado.app.ai.FakeAiChatClient
import io.legado.app.ai.PageFetcher
import kotlinx.coroutines.runBlocking
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
}
