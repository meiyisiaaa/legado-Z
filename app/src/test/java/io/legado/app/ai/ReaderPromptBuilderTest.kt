package io.legado.app.ai

import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.prompt.PromptBuilder
import io.legado.app.ai.prompt.ReaderPromptTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PromptBuilder] 读者助手侧（`buildQa` / `buildExplain`）单测。
 *
 * 单开一个测试类而不是并进 [PromptBuilderTest]：书源侧与读者侧的断言重点完全不同 ——
 * 书源侧盯"字段速查表有没有漏"，读者侧盯"未召回的正文有没有漏进 prompt"。
 *
 * 最重要的两组断言：
 * 1. **隐私红线**：prompt 里绝不出现 apiKey / endpoint（方法签名里就没有 `AiChatOptions`）；
 * 2. **只发片段**：`buildQa` 只拼装调用方给定的 `fragmentBlocks`，
 *    赏析只拼装 `selection` —— 任何未经检索命中的正文都进不来。
 */
class ReaderPromptBuilderTest {

    private val secretKey = "sk-super-secret-key-0123456789"
    private val secretEndpoint = "https://my-private-proxy.internal/v1"

    /* ==================== buildQa ==================== */

    @Test
    fun `问答产出system与user两条消息`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                bookName = "三体",
                question = "叶文洁为什么按下了发射键",
                fragmentBlocks = listOf("片段 1｜第 3 章 · 红岸\n她抬起头看着天线。")
            )
        )

        assertEquals(2, messages.size)
        assertEquals(ChatMessage.ROLE_SYSTEM, messages[0].role)
        assertEquals(ChatMessage.ROLE_USER, messages[1].role)
        assertEquals(ReaderPromptTemplates.SYSTEM_QA, messages[0].content)
    }

    @Test
    fun `问答user消息按书名片段问题的顺序组织`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                bookName = "三体",
                question = "叶文洁为什么按下了发射键",
                fragmentBlocks = listOf("片段 1｜第 3 章 · 红岸\n她抬起头看着天线。")
            )
        )
        val user = messages[1].content

        val bookAt = user.indexOf(ReaderPromptTemplates.USER_BOOK_LABEL)
        val fragmentAt = user.indexOf(ReaderPromptTemplates.USER_FRAGMENTS_LABEL)
        val questionAt = user.indexOf(ReaderPromptTemplates.USER_QUESTION_LABEL)

        assertTrue("应包含书名标签", bookAt >= 0)
        assertTrue("书名应在片段之前", bookAt < fragmentAt)
        assertTrue("片段应在问题之前", fragmentAt < questionAt)
        assertTrue("应以问答收尾语结束", user.trimEnd().endsWith(ReaderPromptTemplates.USER_QA_TAIL))
        assertTrue(user.contains("三体"))
        assertTrue(user.contains("她抬起头看着天线。"))
    }

    @Test
    fun `多条片段之间用空行分隔`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                question = "问",
                fragmentBlocks = listOf("片段一正文", "片段二正文")
            )
        )
        val user = messages[1].content

        assertTrue("两条片段应以空行分隔", user.contains("片段一正文\n\n片段二正文"))
    }

    @Test
    fun `空白片段块被剔除`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                question = "问",
                fragmentBlocks = listOf("有效片段", "   ", "")
            )
        )
        val user = messages[1].content

        assertFalse("不应留下空白片段造成的连续空行", user.contains("有效片段\n\n\n"))
        assertTrue(user.contains("有效片段"))
    }

    @Test
    fun `无历史时不出现历史标签`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(question = "问", fragmentBlocks = listOf("片段"))
        )

        assertFalse(
            "没有历史就不该出现空的历史区块",
            messages[1].content.contains(ReaderPromptTemplates.USER_HISTORY_LABEL)
        )
    }

    @Test
    fun `有历史时按问答前缀逐轮展开`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                question = "那后来呢",
                history = listOf(ChatTurn("他是谁", "他是罗辑。")),
                fragmentBlocks = listOf("片段")
            )
        )
        val user = messages[1].content

        assertTrue(user.contains(ReaderPromptTemplates.USER_HISTORY_LABEL))
        assertTrue(user.contains(ReaderPromptTemplates.HISTORY_QUESTION_PREFIX + "他是谁"))
        assertTrue(user.contains(ReaderPromptTemplates.HISTORY_ANSWER_PREFIX + "他是罗辑。"))
    }

    @Test
    fun `残缺的历史轮次被过滤`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                question = "问",
                history = listOf(
                    ChatTurn("只有问没有答", ""),
                    ChatTurn("", "只有答没有问"),
                    ChatTurn("完整问", "完整答")
                ),
                fragmentBlocks = listOf("片段")
            )
        )
        val user = messages[1].content

        assertFalse(user.contains("只有问没有答"))
        assertFalse(user.contains("只有答没有问"))
        assertTrue(user.contains("完整问"))
    }

    @Test
    fun `书名为空时用占位符而不是留空标签`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(bookName = "", question = "问", fragmentBlocks = listOf("片段"))
        )
        val user = messages[1].content

        assertTrue(
            "空书名应回落占位符",
            user.contains(
                ReaderPromptTemplates.USER_BOOK_LABEL + "\n" +
                        ReaderPromptTemplates.PLACEHOLDER_NONE
            )
        )
    }

    @Test
    fun `问答prompt不含任何凭据`() {
        val messages = PromptBuilder.buildQa(
            QaPromptInput(
                bookName = "三体",
                question = "问",
                history = listOf(ChatTurn("旧问", "旧答")),
                fragmentBlocks = listOf("片段")
            )
        )
        val all = messages.joinToString("\n") { it.content }

        assertFalse("prompt 绝不能含 apiKey", all.contains(secretKey))
        assertFalse("prompt 绝不能含 endpoint", all.contains(secretEndpoint))
        assertFalse(all.contains("apiKey", ignoreCase = true))
        assertFalse(all.contains("Authorization", ignoreCase = true))
    }

    /* ==================== buildExplain ==================== */

    @Test
    fun `赏析产出system与user两条消息`() {
        val messages = PromptBuilder.buildExplain(
            ExplainPromptInput(bookName = "红楼梦", chapterTitle = "第五回", selection = "开辟鸿蒙")
        )

        assertEquals(2, messages.size)
        assertEquals(ChatMessage.ROLE_SYSTEM, messages[0].role)
        assertEquals(ReaderPromptTemplates.SYSTEM_EXPLAIN, messages[0].content)
        assertEquals(ChatMessage.ROLE_USER, messages[1].role)
    }

    @Test
    fun `赏析user消息只带书名章节与选中原文`() {
        val messages = PromptBuilder.buildExplain(
            ExplainPromptInput(
                bookName = "红楼梦",
                chapterTitle = "第五回",
                selection = "开辟鸿蒙，谁为情种？"
            )
        )
        val user = messages[1].content

        assertTrue(user.contains(ReaderPromptTemplates.USER_BOOK_LABEL))
        assertTrue(user.contains(ReaderPromptTemplates.USER_CHAPTER_LABEL))
        assertTrue(user.contains(ReaderPromptTemplates.USER_SELECTION_LABEL))
        assertTrue(user.contains("开辟鸿蒙，谁为情种？"))
        assertTrue(user.trimEnd().endsWith(ReaderPromptTemplates.USER_EXPLAIN_TAIL))

        // 赏析链路没有检索，绝不该出现片段/历史这两个区块
        assertFalse(user.contains(ReaderPromptTemplates.USER_FRAGMENTS_LABEL))
        assertFalse(user.contains(ReaderPromptTemplates.USER_HISTORY_LABEL))
    }

    @Test
    fun `赏析章节标题为空时用占位符`() {
        val messages = PromptBuilder.buildExplain(
            ExplainPromptInput(bookName = "书", chapterTitle = "  ", selection = "文本")
        )
        val user = messages[1].content

        assertTrue(
            user.contains(
                ReaderPromptTemplates.USER_CHAPTER_LABEL + "\n" +
                        ReaderPromptTemplates.PLACEHOLDER_NONE
            )
        )
    }

    @Test
    fun `赏析prompt不含任何凭据`() {
        val messages = PromptBuilder.buildExplain(
            ExplainPromptInput(bookName = "书", chapterTitle = "章", selection = "文本")
        )
        val all = messages.joinToString("\n") { it.content }

        assertFalse(all.contains(secretKey))
        assertFalse(all.contains(secretEndpoint))
    }

    @Test
    fun `两条链路的system prompt互不串味`() {
        val qa = PromptBuilder.buildQa(
            QaPromptInput(question = "问", fragmentBlocks = listOf("片段"))
        )[0].content
        val explain = PromptBuilder.buildExplain(
            ExplainPromptInput(selection = "文本")
        )[0].content

        assertFalse("问答的 system 不该混入赏析要求", qa == explain)
        assertTrue("问答必须约束只依据片段作答", qa.contains("只能"))
        assertTrue("赏析必须要求三段式", explain.contains("文意"))
    }
}
