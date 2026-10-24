package io.legado.app.ai

import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.prompt.PromptBuilder
import io.legado.app.ai.prompt.ReaderPromptTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPromptBuilderTest {

    @Test
    fun `选区问答只包含选中文字和问题`() {
        val messages = PromptBuilder.buildSelectionQa(
            ExplainPromptInput(selection = "开辟鸿蒙", question = "这句话是什么意思")
        )

        assertEquals(2, messages.size)
        assertEquals(ChatMessage.ROLE_SYSTEM, messages[0].role)
        assertEquals(ReaderPromptTemplates.SYSTEM_SELECTION_QA, messages[0].content)
        assertEquals(ChatMessage.ROLE_USER, messages[1].role)
        assertTrue(messages[1].content.contains("开辟鸿蒙"))
        assertTrue(messages[1].content.contains("这句话是什么意思"))
        assertTrue(
            messages[1].content.trimEnd()
                .endsWith(ReaderPromptTemplates.USER_SELECTION_QA_TAIL)
        )
    }

    @Test
    fun `选区问答不携带未选正文或书籍元信息`() {
        val all = PromptBuilder.buildSelectionQa(
            ExplainPromptInput(selection = "选中片段", question = "问")
        ).joinToString("\n") { it.content }

        assertFalse(all.contains("未选中的相邻正文"))
        assertFalse(all.contains("书名：秘密作品"))
        assertFalse(all.contains("章节：秘密章节"))
    }

    @Test
    fun `赏析只包含选中文字`() {
        val messages = PromptBuilder.buildExplain(
            ExplainPromptInput(selection = "开辟鸿蒙，谁为情种？")
        )
        val user = messages[1].content

        assertEquals(2, messages.size)
        assertEquals(ReaderPromptTemplates.SYSTEM_EXPLAIN, messages[0].content)
        assertTrue(user.contains(ReaderPromptTemplates.USER_SELECTION_LABEL))
        assertTrue(user.contains("开辟鸿蒙，谁为情种？"))
        assertTrue(user.trimEnd().endsWith(ReaderPromptTemplates.USER_EXPLAIN_TAIL))
        assertFalse(user.contains(ReaderPromptTemplates.USER_QUESTION_LABEL))
    }

    @Test
    fun `两种选区能力的system prompt互不串味`() {
        val qa = PromptBuilder.buildSelectionQa(
            ExplainPromptInput(selection = "文本", question = "问")
        )[0].content
        val explain = PromptBuilder.buildExplain(
            ExplainPromptInput(selection = "文本")
        )[0].content

        assertFalse(qa == explain)
        assertTrue(qa.contains("只能"))
        assertTrue(explain.contains("文意"))
    }
}
