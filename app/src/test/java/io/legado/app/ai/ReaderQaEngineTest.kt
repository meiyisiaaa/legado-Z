package io.legado.app.ai

import io.legado.app.ai.engine.ReaderQaEngine
import io.legado.app.ai.prompt.ReaderPromptTemplates
import io.legado.app.ai.retrieval.RetrievalResult
import io.legado.app.ai.retrieval.RetrievalStats
import io.legado.app.ai.retrieval.ScoredFragment
import io.legado.app.ai.retrieval.TextFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ReaderQaEngine] 单测。
 *
 * 最重要的一条断言是**隐私红线的最后闸门**：
 * 本地检索零命中时必须抛 [AiError.RetrievalEmpty] 且 `callCount == 0` ——
 * 一个字都不许发出去。其余覆盖：片段编号、出处投影、历史预算裁剪（决策 A2）、
 * 轮数上限、统计透传、以及"prompt 里绝不出现 apiKey"。
 */
class ReaderQaEngineTest {

    private fun scored(
        chapterIndex: Int = 0,
        chapterTitle: String = "第一章",
        charStart: Int = 0,
        text: String = "他抬起头看着远处的山。",
        score: Double = 1.0
    ): ScoredFragment = ScoredFragment(
        TextFragment(chapterIndex, chapterTitle, charStart, text),
        score
    )

    private fun retrieval(
        vararg fragments: ScoredFragment,
        stats: RetrievalStats = RetrievalStats.EMPTY
    ): RetrievalResult = RetrievalResult(fragments.toList(), stats)

    /* ==================== 主流程 ==================== */

    @Test
    fun `正常问答返回答案与出处`() = runBlocking {
        val client = FakeAiChatClient.replying("他是**罗辑**。（片段 1）")
        val engine = ReaderQaEngine(client)

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(
                bookName = "三体",
                question = "他是谁",
                retrieval = retrieval(scored(chapterIndex = 4, chapterTitle = "面壁者", charStart = 120))
            )
        )

        assertEquals("他是**罗辑**。（片段 1）", outcome.answerMarkdown)
        assertEquals(1, outcome.citations.size)
        assertEquals(4, outcome.citations[0].chapterIndex)
        assertEquals("面壁者", outcome.citations[0].chapterTitle)
        assertEquals(120, outcome.citations[0].charStart)
        assertEquals(0, outcome.droppedHistoryTurns)
        assertEquals(1, client.callCount)
    }

    @Test
    fun `零命中时绝不发起网络请求`() = runBlocking {
        val client = FakeAiChatClient.replying("不该被用到")
        val engine = ReaderQaEngine(client)

        assertThrowsOf<AiError.RetrievalEmpty> {
            engine.ask(
                AiTestFixtures.options(),
                QaInput(question = "书里有没有提到猫", retrieval = RetrievalResult.EMPTY)
            )
        }
        assertEquals("零命中必须在本地失败，一个字都不许发出去", 0, client.callCount)
    }

    @Test
    fun `空白问题不发起网络请求`() = runBlocking {
        val client = FakeAiChatClient.replying("不该被用到")
        val engine = ReaderQaEngine(client)

        assertThrowsOf<AiError.QuestionEmpty> {
            engine.ask(
                AiTestFixtures.options(),
                QaInput(question = "  \n ", retrieval = retrieval(scored()))
            )
        }
        assertEquals(0, client.callCount)
    }

    @Test
    fun `未配置时不发起网络请求`() = runBlocking {
        val client = FakeAiChatClient.replying("不该被用到")
        val engine = ReaderQaEngine(client)

        assertThrowsOf<AiError.NotConfigured> {
            engine.ask(
                AiTestFixtures.options().copy(apiKey = ""),
                QaInput(question = "问", retrieval = retrieval(scored()))
            )
        }
        assertEquals(0, client.callCount)
    }

    @Test
    fun `配置非法时不发起网络请求`() = runBlocking {
        val client = FakeAiChatClient.replying("不该被用到")
        val engine = ReaderQaEngine(client)

        assertThrowsOf<AiError.InvalidConfig> {
            engine.ask(
                AiTestFixtures.options().copy(temperature = 99f),
                QaInput(question = "问", retrieval = retrieval(scored()))
            )
        }
        assertEquals(0, client.callCount)
    }

    @Test
    fun `空回复抛EmptyResponse`() = runBlocking {
        val client = FakeAiChatClient.replying("   ")
        val engine = ReaderQaEngine(client)

        assertThrowsOf<AiError.EmptyResponse> {
            engine.ask(
                AiTestFixtures.options(),
                QaInput(question = "问", retrieval = retrieval(scored()))
            )
        }
        return@runBlocking
    }

    /* ==================== 片段格式化 ==================== */

    @Test
    fun `片段编号从1开始且章号按人类习惯加1`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        engine.ask(
            AiTestFixtures.options(),
            QaInput(
                question = "问",
                retrieval = retrieval(
                    scored(chapterIndex = 0, chapterTitle = "开端", text = "甲段"),
                    scored(chapterIndex = 11, chapterTitle = "归途", text = "乙段")
                )
            )
        )
        val user = client.messagesSeen.single()[1].content

        assertTrue("首条应编号为 1、章号显示为第 1 章", user.contains("片段 1｜第 1 章 · 开端"))
        assertTrue("次条应编号为 2、章号显示为第 12 章", user.contains("片段 2｜第 12 章 · 归途"))
        assertTrue(user.contains("甲段"))
        assertTrue(user.contains("乙段"))
    }

    @Test
    fun `无标题章节用占位符`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        engine.ask(
            AiTestFixtures.options(),
            QaInput(question = "问", retrieval = retrieval(scored(chapterTitle = "   ")))
        )
        val user = client.messagesSeen.single()[1].content

        assertTrue(user.contains(ReaderPromptTemplates.UNTITLED_CHAPTER))
    }

    @Test
    fun `出处顺序与召回顺序一致`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(
                question = "问",
                retrieval = retrieval(
                    scored(chapterIndex = 7, chapterTitle = "甲"),
                    scored(chapterIndex = 2, chapterTitle = "乙"),
                    scored(chapterIndex = 9, chapterTitle = "丙")
                )
            )
        )

        assertEquals(listOf(7, 2, 9), outcome.citations.map { it.chapterIndex })
        assertEquals(listOf("甲", "乙", "丙"), outcome.citations.map { it.chapterTitle })
    }

    @Test
    fun `问题两端空白被裁剪后再入prompt`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        engine.ask(
            AiTestFixtures.options(),
            QaInput(question = "  他是谁  \n", retrieval = retrieval(scored()))
        )
        val user = client.messagesSeen.single()[1].content

        assertTrue(
            user.contains(ReaderPromptTemplates.USER_QUESTION_LABEL + "\n他是谁")
        )
    }

    /* ==================== 统计与成本 ==================== */

    @Test
    fun `检索统计原样透传给产出`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)
        val stats = RetrievalStats(
            scannedChapters = 40,
            cachedChapters = 12,
            totalChapters = 300,
            elapsedMs = 137L,
            budgetExceeded = true,
            usedFallback = true
        )

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(question = "问", retrieval = retrieval(scored(), stats = stats))
        )

        assertEquals(stats, outcome.stats)
    }

    @Test
    fun `promptChars等于实际送出的字符总数`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(bookName = "三体", question = "他是谁", retrieval = retrieval(scored()))
        )

        val actual = client.messagesSeen.single().sumOf { it.content.length }
        assertEquals(actual, outcome.promptChars)
        assertTrue(outcome.promptChars > 0)
    }

    /* ==================== 历史裁剪（决策 A2） ==================== */

    @Test
    fun `历史按由旧到新原样带入prompt`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(
                question = "那后来呢",
                history = listOf(ChatTurn("他是谁", "他是罗辑。"), ChatTurn("他做了什么", "他成了面壁者。")),
                retrieval = retrieval(scored())
            )
        )
        val user = client.messagesSeen.single()[1].content

        assertEquals(0, outcome.droppedHistoryTurns)
        assertTrue(user.indexOf("他是谁") < user.indexOf("他做了什么"))
    }

    @Test
    fun `片段吃满预算时历史全部让位`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)
        // 单条片段就超过 MAX_PROMPT_CHARS，历史一轮都塞不下
        val huge = "甲".repeat(7900)

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(
                question = "他是谁",
                history = listOf(ChatTurn("旧问", "旧答"), ChatTurn("新问", "新答")),
                retrieval = retrieval(scored(text = huge))
            )
        )
        val user = client.messagesSeen.single()[1].content

        assertEquals("两轮历史都应被丢弃", 2, outcome.droppedHistoryTurns)
        assertFalse(user.contains(ReaderPromptTemplates.USER_HISTORY_LABEL))
        assertTrue("硬上限生效后仍应保留片段内容", user.contains("甲"))
        assertTrue("promptChars=${outcome.promptChars}", outcome.promptChars <= ReaderQaEngine.MAX_PROMPT_CHARS)
    }

    @Test
    fun `预算不足时从最旧的一轮开始丢`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)
        // 片段 100 字 + 问题 3 字，余量约 7880；最旧一轮要 7110 字，加上最新一轮就溢出
        val oldest = ChatTurn("旧问", "甲".repeat(7100))
        val newest = ChatTurn("新问", "乙".repeat(900))

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(
                question = "他是谁",
                history = listOf(oldest, newest),
                retrieval = retrieval(scored(text = "丙".repeat(100)))
            )
        )
        val user = client.messagesSeen.single()[1].content

        assertEquals(1, outcome.droppedHistoryTurns)
        assertFalse("最旧的一轮应被丢弃", user.contains("旧问"))
        assertTrue("最新的一轮必须保留", user.contains("新问"))
    }

    @Test
    fun `最终prompt不会超过硬上限`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)
        val fragments = (0 until 10).map {
            scored(chapterIndex = it, text = "甲".repeat(1000))
        }

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(question = "他是谁", retrieval = retrieval(*fragments.toTypedArray()))
        )

        assertTrue(outcome.promptChars <= ReaderQaEngine.MAX_PROMPT_CHARS)
    }

    @Test
    fun `历史轮数超过上限时只保留最近若干轮`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)
        val history = (1..8).map { ChatTurn("提问$it", "回答$it") }

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(question = "他是谁", history = history, retrieval = retrieval(scored()))
        )
        val user = client.messagesSeen.single()[1].content

        assertEquals(8 - ReaderQaEngine.MAX_HISTORY_TURNS, outcome.droppedHistoryTurns)
        assertFalse("最旧的两轮应被丢弃", user.contains("提问1"))
        assertFalse(user.contains("提问2"))
        assertTrue("第 3 轮起应保留", user.contains("提问3"))
        assertTrue(user.contains("提问8"))
    }

    @Test
    fun `残缺历史轮次不计入丢弃数`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        val outcome = engine.ask(
            AiTestFixtures.options(),
            QaInput(
                question = "他是谁",
                history = listOf(
                    ChatTurn("只有问", ""),
                    ChatTurn("", "只有答"),
                    ChatTurn("完整问", "完整答")
                ),
                retrieval = retrieval(scored())
            )
        )
        val user = client.messagesSeen.single()[1].content

        assertEquals("残缺轮次本就不参与统计", 0, outcome.droppedHistoryTurns)
        assertFalse(user.contains("只有问"))
        assertTrue(user.contains("完整问"))
    }

    /* ==================== 隐私 ==================== */

    @Test
    fun `prompt不含apiKey`() = runBlocking {
        val secret = "sk-qa-secret-key-4321"
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        engine.ask(
            AiTestFixtures.options(apiKey = secret),
            QaInput(bookName = "三体", question = "他是谁", retrieval = retrieval(scored()))
        )

        assertFalse(client.allContent().contains(secret))
    }

    @Test
    fun `只送出召回片段本身`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)

        engine.ask(
            AiTestFixtures.options(),
            QaInput(
                bookName = "三体",
                question = "他是谁",
                retrieval = retrieval(scored(text = "被召回的那一段"))
            )
        )
        val all = client.allContent()

        assertTrue(all.contains("被召回的那一段"))
        // 未召回的内容压根没进过 QaInput，这里守的是"引擎不会自己去补正文"
        assertFalse(all.contains("未被召回的段落"))
    }

    @Test
    fun `配置快照原样透传给客户端`() = runBlocking {
        val client = FakeAiChatClient.replying("答")
        val engine = ReaderQaEngine(client)
        val options = AiTestFixtures.options()

        engine.ask(
            AiTestFixtures.options(),
            QaInput(question = "问", retrieval = retrieval(scored()))
        )

        assertEquals("问答不做温度改写", options, client.optionsSeen.single())
    }
}
