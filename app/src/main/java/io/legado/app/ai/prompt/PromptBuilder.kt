package io.legado.app.ai.prompt

import io.legado.app.ai.DebugInput
import io.legado.app.ai.ExplainPromptInput
import io.legado.app.ai.GenerateInput
import io.legado.app.ai.QaPromptInput
import io.legado.app.ai.ValidationResult
import io.legado.app.ai.client.ChatMessage

/**
 * 把用户输入 + HTML 片段组装成 LLM 消息列表。
 *
 * 隐私红线：这里**只**拼装用户显式提供的内容（意图、页面地址、HTML 片段、书源 JSON、症状），
 * 绝不接触 [io.legado.app.ai.AiChatOptions] 中的 apiKey / endpoint。
 * 因此本类的所有方法都**不接收 AiChatOptions 参数** —— 从签名上就杜绝凭据泄漏。
 */
object PromptBuilder {

    /** 生成模式：system（含字段速查）+ user（意图 + 页面地址 + HTML 片段）。 */
    fun buildGenerate(input: GenerateInput, snippet: String): List<ChatMessage> {
        val user = buildString {
            appendSection(PromptTemplates.USER_INTENT_LABEL, input.intent)
            if (input.hasUrl()) {
                appendSection(PromptTemplates.USER_PAGE_URL_LABEL, input.url)
            }
            appendSection(PromptTemplates.USER_HTML_LABEL, snippet)
            append(PromptTemplates.USER_GENERATE_TAIL)
        }
        return listOf(
            ChatMessage.system(PromptTemplates.systemGenerate()),
            ChatMessage.user(user)
        )
    }

    /** 调试模式：system（含字段速查）+ user（现有书源 JSON + 症状 + 样例 HTML 片段）。 */
    fun buildDebug(input: DebugInput, snippet: String): List<ChatMessage> {
        val user = buildString {
            appendSection(PromptTemplates.USER_SOURCE_JSON_LABEL, input.sourceJson)
            appendSection(PromptTemplates.USER_SYMPTOM_LABEL, input.symptom)
            appendSection(PromptTemplates.USER_SAMPLE_HTML_LABEL, snippet)
            append(PromptTemplates.USER_DEBUG_TAIL)
        }
        return listOf(
            ChatMessage.system(PromptTemplates.systemDebug()),
            ChatMessage.user(user)
        )
    }

    /**
     * 自动修复重试（Q4 可选路径）：把上一轮无法解析的原始回复与问题清单一起回喂。
     *
     * 仅在 `AiChatOptions.autoRepair` 为 true 时由 Engine 调用，最多一次。
     */
    fun buildRepair(rawReply: String, validation: ValidationResult): List<ChatMessage> {
        val issues = validation.issues.joinToString("\n") { issue ->
            val suffix = if (issue.detail.isBlank()) "" else "（${issue.detail}）"
            "- [${issue.level}] ${issue.field}$suffix"
        }
        val user = buildString {
            appendSection(PromptTemplates.USER_RAW_REPLY_LABEL, rawReply)
            appendSection(PromptTemplates.USER_ISSUES_LABEL, issues)
            append(PromptTemplates.USER_REPAIR_TAIL)
        }
        return listOf(
            ChatMessage.system(PromptTemplates.systemRepair()),
            ChatMessage.user(user)
        )
    }

    /* ==================== Phase 2：读者助手 ==================== */

    /**
     * 检索问答：system（只准依据片段作答）+ user（书名 + 片段 + 历史 + 问题）。
     *
     * 隐私红线：本方法**只**拼装 [QaPromptInput] 里已经准备好的内容。
     * 片段由 [io.legado.app.ai.retrieval.BookRetriever] 检索命中后交给
     * [io.legado.app.ai.engine.ReaderQaEngine] 裁剪，**未命中的正文永远进不到这里**。
     *
     * 历史轮次只带问与答的文本、不重复携带当时的片段：既省 token，
     * 也避免"早期片段"随着对话变长被反复上传。
     */
    fun buildQa(input: QaPromptInput): List<ChatMessage> {
        val fragments = input.fragmentBlocks
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val history = input.history
            .filter { it.isUsable() }
            .joinToString("\n") { turn ->
                ReaderPromptTemplates.HISTORY_QUESTION_PREFIX + turn.question.trim() + "\n" +
                        ReaderPromptTemplates.HISTORY_ANSWER_PREFIX + turn.answer.trim()
            }
        val user = buildString {
            appendReaderSection(ReaderPromptTemplates.USER_BOOK_LABEL, input.bookName)
            appendReaderSection(ReaderPromptTemplates.USER_FRAGMENTS_LABEL, fragments)
            if (history.isNotBlank()) {
                appendReaderSection(ReaderPromptTemplates.USER_HISTORY_LABEL, history)
            }
            appendReaderSection(ReaderPromptTemplates.USER_QUESTION_LABEL, input.question)
            append(ReaderPromptTemplates.USER_QA_TAIL)
        }
        return listOf(
            ChatMessage.system(ReaderPromptTemplates.systemQa()),
            ChatMessage.user(user)
        )
    }

    /**
     * 选中文本赏析：system（三段式赏析）+ user（书名 + 章节 + 选中原文）。
     *
     * 隐私红线：**只**发送 [ExplainPromptInput.selection]（用户主动选中的文本），
     * 绝不附带所在章节的其余内容 —— 这是与问答链路最本质的区别。
     */
    fun buildExplain(input: ExplainPromptInput): List<ChatMessage> {
        val user = buildString {
            appendReaderSection(ReaderPromptTemplates.USER_BOOK_LABEL, input.bookName)
            appendReaderSection(ReaderPromptTemplates.USER_CHAPTER_LABEL, input.chapterTitle)
            appendReaderSection(ReaderPromptTemplates.USER_SELECTION_LABEL, input.selection)
            append(ReaderPromptTemplates.USER_EXPLAIN_TAIL)
        }
        return listOf(
            ChatMessage.system(ReaderPromptTemplates.systemExplain()),
            ChatMessage.user(user)
        )
    }

    /** 追加一个「标签 + 内容」区块，内容为空时用占位符，避免出现空标签块干扰模型。 */
    private fun StringBuilder.appendSection(label: String, content: String) {
        append(label).append('\n')
        val body = content.trim()
        append(body.ifEmpty { PromptTemplates.PLACEHOLDER_NONE })
        append("\n\n")
    }

    /** 读者助手侧的区块拼装，占位符取自 [ReaderPromptTemplates]。 */
    private fun StringBuilder.appendReaderSection(label: String, content: String) {
        append(label).append('\n')
        val body = content.trim()
        append(body.ifEmpty { ReaderPromptTemplates.PLACEHOLDER_NONE })
        append("\n\n")
    }
}
