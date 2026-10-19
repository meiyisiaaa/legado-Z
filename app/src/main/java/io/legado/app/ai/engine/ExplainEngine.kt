package io.legado.app.ai.engine

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.AiReaderOptions
import io.legado.app.ai.ExplainInput
import io.legado.app.ai.ExplainOutcome
import io.legado.app.ai.ExplainPromptInput
import io.legado.app.ai.SelectionQaPair
import io.legado.app.ai.SelectionQaSession
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.prompt.PromptBuilder

/**
 * 选中文本 AI 引擎：赏析或回答用户针对选区提出的问题。
 *
 * 编排顺序：`配置守卫 → 空选中拦截 → 超长截断 → PromptBuilder 组装 → AiChatClient 请求`
 *
 * 本链路没有检索，送出的书籍内容只有用户主动选中的那一段；
 * 不发送书名、章节名、相邻段落或其他章节正文。
 *
 * ### 为什么截断而不是报错（决策 A4）
 * 读者长按选中时经常一划就是好几屏，直接拒绝会让功能显得很脆。
 * 截断 + [ExplainOutcome.truncated] 置位 + UI 顶部提示，既守住成本又不打断阅读节奏。
 *
 * @param client 对话客户端，单测注入 `FakeAiChatClient`
 */
class ExplainEngine(private val client: AiChatClient) {

    /**
     * 赏析选中文本。
     *
     * @throws AiError.NotConfigured / [AiError.InvalidConfig] 配置不可用
     * @throws AiError.SelectionEmpty 选中文本为空白（**此时不会发起任何网络请求**）
     * @throws AiError.EmptyResponse 模型返回空内容
     */
    suspend fun explain(options: AiChatOptions, input: ExplainInput): ExplainOutcome {
        return complete(options, input, question = null)
    }

    /** 针对选中文本提问。 */
    suspend fun ask(
        options: AiChatOptions,
        input: ExplainInput,
        rawQuestion: String,
        history: List<SelectionQaPair> = emptyList()
    ): ExplainOutcome {
        val question = rawQuestion.trim()
        if (question.isEmpty()) throw AiError.QuestionEmpty()
        if (question.length > SelectionQaSession.MAX_QUESTION_CHARS) throw AiError.QuestionTooLong()
        return complete(options, input, question, history)
    }

    private suspend fun complete(
        options: AiChatOptions,
        input: ExplainInput,
        question: String?,
        history: List<SelectionQaPair> = emptyList()
    ): ExplainOutcome {
        OutcomeAssembler.requireUsableConfig(options)

        val selection = normalize(input.selection)
        if (selection.isEmpty()) throw AiError.SelectionEmpty()

        val limit = clampLimit(input.maxChars)
        val sent = truncate(selection, limit)

        val promptInput = ExplainPromptInput(selection = sent, question = question.orEmpty(), history = history)
        val messages = if (question == null) {
            PromptBuilder.buildExplain(promptInput)
        } else {
            PromptBuilder.buildSelectionQa(promptInput)
        }

        val response = client.chat(options, messages)
        val reply = response.firstContent()
        if (reply.isBlank()) throw AiError.EmptyResponse()

        return ExplainOutcome(
            answerMarkdown = reply.trim(),
            truncated = sent.length < selection.length,
            sentChars = sent.length,
            usage = response.usage
        )
    }

    /* ---------------- 内部实现 ---------------- */

    /**
     * 规整选中文本。
     *
     * 阅读器的选区常常带着排版留下的连续空行与行首缩进，原样上传纯属浪费 token；
     * 只规整排版空白，不增补、替换任何正文内容。
     */
    private fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    /** 把上限钳制到 [AiReaderOptions] 声明的合法区间，容错来自旧配置或单测的越界值。 */
    private fun clampLimit(maxChars: Int): Int = when {
        maxChars < AiReaderOptions.MIN_EXPLAIN_MAX_CHARS -> AiReaderOptions.MIN_EXPLAIN_MAX_CHARS
        maxChars > AiReaderOptions.MAX_EXPLAIN_MAX_CHARS -> AiReaderOptions.MAX_EXPLAIN_MAX_CHARS
        else -> maxChars
    }

    /**
     * 截断到 [limit] 字符，并尽量收在一个句子的末尾。
     *
     * 硬截断会把最后一句劈成半截，模型很容易顺着残句往下"补完"，赏析就跑偏了。
     * 因此在末尾 [SENTENCE_LOOKBACK_RATIO] 的范围内回退到最近一个句末标点；
     * 找不到就老老实实硬截 —— 宁可断句难看，也不能少给内容。
     */
    private fun truncate(text: String, limit: Int): String {
        if (text.length <= limit) return text

        val hard = text.substring(0, limit)
        val lowerBound = (limit * (1 - SENTENCE_LOOKBACK_RATIO)).toInt()
        val cut = hard.indexOfLast { it in SENTENCE_END_CHARS }
        if (cut >= lowerBound) {
            return hard.substring(0, cut + 1)
        }
        return hard
    }

    companion object {

        /** 句末标点集合，中英文都覆盖（含中文引号收尾的情况）。 */
        internal const val SENTENCE_END_CHARS = "。！？…；.!?;\n"

        /**
         * 允许为了「断在句末」而多丢弃的比例上限。
         *
         * 取 0.15：再大就会出现"选了 1000 字只发了 600 字"这种用户无法理解的行为。
         */
        internal const val SENTENCE_LOOKBACK_RATIO = 0.15
    }
}
