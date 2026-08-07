package io.legado.app.ai

import io.legado.app.ai.engine.ExplainEngine
import io.legado.app.ai.prompt.ReaderPromptTemplates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExplainEngine] 单测。
 *
 * 三条必须守住的线：
 * 1. **隐私**：只发用户选中的文本；空选中不发请求（`callCount == 0`）；
 * 2. **成本**：超长选中必须截断，且 [ExplainOutcome.truncated] / `sentChars` 如实反映；
 * 3. **不改写原文**：截断只做"少发"，不做任何替换或润色。
 */
class ExplainEngineTest {

    private fun engineReplying(vararg replies: String) =
        FakeAiChatClient.replying(*replies)

    @Test
    fun `正常赏析返回Markdown答案`() = runBlocking {
        val client = engineReplying("### 文意\n讲的是离别。")
        val engine = ExplainEngine(client)

        val outcome = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(bookName = "红楼梦", chapterTitle = "第五回", selection = "开辟鸿蒙")
        )

        assertEquals("### 文意\n讲的是离别。", outcome.answerMarkdown)
        assertFalse(outcome.truncated)
        assertEquals("开辟鸿蒙".length, outcome.sentChars)
        assertEquals(1, client.callCount)
    }

    @Test
    fun `空白选中不发起任何请求`() = runBlocking {
        val client = engineReplying("不该被用到")
        val engine = ExplainEngine(client)

        assertThrowsOf<AiError.SelectionEmpty> {
            engine.explain(AiTestFixtures.options(), ExplainInput(selection = "   \n\t  "))
        }
        assertEquals("空选中必须在本地失败，绝不发请求", 0, client.callCount)
    }

    @Test
    fun `未配置时不发起任何请求`() = runBlocking {
        val client = engineReplying("不该被用到")
        val engine = ExplainEngine(client)

        assertThrowsOf<AiError.NotConfigured> {
            engine.explain(
                AiTestFixtures.options().copy(apiKey = ""),
                ExplainInput(selection = "文本")
            )
        }
        assertEquals(0, client.callCount)
    }

    @Test
    fun `配置非法时不发起任何请求`() = runBlocking {
        val client = engineReplying("不该被用到")
        val engine = ExplainEngine(client)

        assertThrowsOf<AiError.InvalidConfig> {
            engine.explain(
                AiTestFixtures.options().copy(temperature = 99f),
                ExplainInput(selection = "文本")
            )
        }
        assertEquals(0, client.callCount)
    }

    @Test
    fun `空回复抛EmptyResponse`() = runBlocking {
        val client = engineReplying("   ")
        val engine = ExplainEngine(client)

        assertThrowsOf<AiError.EmptyResponse> {
            engine.explain(AiTestFixtures.options(), ExplainInput(selection = "文本"))
        }
        return@runBlocking
    }

    @Test
    fun `超长选中被截断并置位truncated`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)
        // 无句末标点，走硬截断路径，长度可精确断言
        val selection = "甲".repeat(900)

        val outcome = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = selection, maxChars = 300)
        )

        assertTrue("超上限必须置位 truncated", outcome.truncated)
        assertEquals(300, outcome.sentChars)

        val sent = client.messagesSeen.single()[1].content
        assertTrue("送出的应是前 300 字", sent.contains("甲".repeat(300)))
        assertFalse("不应送出第 301 字", sent.contains("甲".repeat(301)))
    }

    @Test
    fun `截断优先收在句末标点`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)
        // 第 290 字处是句号（下标 289），落在 300 * (1 - 0.15) = 255 的回看范围内
        val selection = "甲".repeat(289) + "。" + "乙".repeat(600)

        val outcome = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = selection, maxChars = 300)
        )

        assertTrue(outcome.truncated)
        assertEquals("应回退到句号处收尾", 290, outcome.sentChars)

        val sent = client.messagesSeen.single()[1].content
        assertTrue(sent.contains("甲".repeat(289) + "。"))
        assertFalse("句号之后的内容不该被送出", sent.contains("乙"))
    }

    @Test
    fun `句末标点太靠前时仍走硬截断`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)
        // 唯一的句号在下标 9，远早于回看下界 255，硬截断才不至于少发太多
        val selection = "甲".repeat(9) + "。" + "乙".repeat(600)

        val outcome = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = selection, maxChars = 300)
        )

        assertEquals("不应为了断句丢掉大半内容", 300, outcome.sentChars)
        assertTrue(outcome.truncated)
    }

    @Test
    fun `恰好等于上限时不算截断`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)
        val selection = "甲".repeat(300)

        val outcome = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = selection, maxChars = 300)
        )

        assertFalse("边界值不应被判为截断", outcome.truncated)
        assertEquals(300, outcome.sentChars)
    }

    @Test
    fun `越界的maxChars被钳制而不是报错`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)
        val selection = "甲".repeat(3000)

        val tooSmall = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = selection, maxChars = 0)
        )
        assertEquals(AiReaderOptions.MIN_EXPLAIN_MAX_CHARS, tooSmall.sentChars)

        val tooLarge = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = selection, maxChars = 999999)
        )
        assertEquals(AiReaderOptions.MAX_EXPLAIN_MAX_CHARS, tooLarge.sentChars)
    }

    @Test
    fun `选中文本的空行与缩进被规整但不改字`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)

        val outcome = engine.explain(
            AiTestFixtures.options(),
            ExplainInput(selection = "  第一行  \n\n\n   第二行   \n")
        )

        assertEquals("第一行\n第二行".length, outcome.sentChars)
        val sent = client.messagesSeen.single()[1].content
        assertTrue(sent.contains("第一行\n第二行"))
    }

    @Test
    fun `只发送选中文本不含章节其余内容`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)

        engine.explain(
            AiTestFixtures.options(),
            ExplainInput(
                bookName = "红楼梦",
                chapterTitle = "第五回 游幻境指迷十二钗",
                selection = "开辟鸿蒙"
            )
        )

        val all = client.allContent()
        assertTrue(all.contains("开辟鸿蒙"))
        assertTrue("章节标题可作定位提示", all.contains("第五回 游幻境指迷十二钗"))
        assertFalse("赏析链路不该出现片段区块", all.contains(ReaderPromptTemplates.USER_FRAGMENTS_LABEL))
    }

    @Test
    fun `prompt不含apiKey`() = runBlocking {
        val secret = "sk-explain-secret-key-9876"
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)

        engine.explain(
            AiTestFixtures.options(apiKey = secret),
            ExplainInput(selection = "文本")
        )

        assertFalse("prompt 绝不能含 apiKey", client.allContent().contains(secret))
    }

    @Test
    fun `配置快照原样透传给客户端`() = runBlocking {
        val client = engineReplying("赏析")
        val engine = ExplainEngine(client)
        val options = AiTestFixtures.options()

        engine.explain(options, ExplainInput(selection = "文本"))

        assertEquals("赏析不做温度改写", options, client.optionsSeen.single())
    }
}
