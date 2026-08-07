package io.legado.app.ai.engine

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.ChatTurn
import io.legado.app.ai.Citation
import io.legado.app.ai.QaInput
import io.legado.app.ai.QaOutcome
import io.legado.app.ai.QaPromptInput
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.prompt.PromptBuilder
import io.legado.app.ai.prompt.ReaderPromptTemplates
import io.legado.app.ai.retrieval.ScoredFragment
import io.legado.app.ai.retrieval.TextFragment

/**
 * 检索问答引擎：把「已召回的原文片段 + 用户的问题」变成一段有出处的回答。
 *
 * 编排顺序：
 * `配置守卫 → 零命中拦截 → 片段编号 → 历史裁剪 → PromptBuilder 组装 → AiChatClient 请求`
 *
 * ### 为什么引擎不自己做检索
 * 检索是**本地、可取消、可能耗时数百毫秒**的操作，而且 UI 需要在检索结束后
 * 先展示「扫描了多少章」再决定要不要发请求（零命中时根本不发）。
 * 把检索留在外面，才能让这两件事各自独立演进、独立单测。
 * 引擎在入口处仍然**强制校验**召回非空，作为隐私红线的最后一道闸门。
 *
 * ### 预算策略（决策 A2：片段优先，历史从最旧丢弃）
 * 召回片段是回答质量的地基，历史只是锦上添花。因此预算先扣给片段和问题，
 * 剩下多少才轮到历史；不够就从**最旧**的一轮开始丢。
 *
 * @param client 对话客户端，单测注入 `FakeAiChatClient`
 */
class ReaderQaEngine(private val client: AiChatClient) {

    /**
     * 提问。
     *
     * @throws AiError.NotConfigured / [AiError.InvalidConfig] 配置不可用
     * @throws AiError.QuestionEmpty 问题为空白
     * @throws AiError.RetrievalEmpty 本地检索零命中（**此时不会发起任何网络请求**）
     * @throws AiError.EmptyResponse 模型返回空内容
     */
    suspend fun ask(options: AiChatOptions, input: QaInput): QaOutcome {
        OutcomeAssembler.requireUsableConfig(options)

        val question = input.question.trim()
        if (question.isEmpty()) throw AiError.QuestionEmpty()

        // 隐私红线的最后一道闸门：召回为空 → 立即失败，绝不把问题发出去
        val fragments = input.retrieval.fragments
        if (fragments.isEmpty()) throw AiError.RetrievalEmpty()

        var selectedFragments = fragments
        var blocks = selectedFragments.mapIndexed { position, scored ->
            formatFragmentBlock(position + 1, scored)
        }
        val fragmentChars = blocks.sumOf { it.length }
        var trimmedHistory = trimHistory(input.history, fragmentChars + question.length)
        var messages = buildMessages(question, input.bookName, trimmedHistory, blocks)

        // MAX_PROMPT_CHARS 是最终消息的硬上限，不只是历史预算。
        while (messages.sumOf { it.content.length } > MAX_PROMPT_CHARS && trimmedHistory.isNotEmpty()) {
            trimmedHistory = trimmedHistory.drop(1)
            messages = buildMessages(question, input.bookName, trimmedHistory, blocks)
        }
        while (messages.sumOf { it.content.length } > MAX_PROMPT_CHARS && selectedFragments.size > 1) {
            selectedFragments = selectedFragments.dropLast(1)
            blocks = selectedFragments.mapIndexed { position, scored ->
                formatFragmentBlock(position + 1, scored)
            }
            messages = buildMessages(question, input.bookName, trimmedHistory, blocks)

        }
        if (messages.sumOf { it.content.length } > MAX_PROMPT_CHARS && selectedFragments.isNotEmpty()) {
            trimmedHistory = emptyList()
            val last = selectedFragments.last()
            val withoutText = selectedFragments.dropLast(1) + last.copy(
                fragment = last.fragment.copy(text = "")
            )
            val baseMessages = buildMessages(
                question,
                input.bookName,
                trimmedHistory,
                withoutText.mapIndexed { position, scored -> formatFragmentBlock(position + 1, scored) }
            )
            val available = (MAX_PROMPT_CHARS - baseMessages.sumOf { it.content.length })
                .coerceAtLeast(0)
            selectedFragments = selectedFragments.dropLast(1) + last.copy(
                fragment = last.fragment.copy(text = last.fragment.text.take(available))
            )
            blocks = selectedFragments.mapIndexed { position, scored ->
                formatFragmentBlock(position + 1, scored)
            }
            messages = buildMessages(question, input.bookName, trimmedHistory, blocks)

            while (messages.sumOf { it.content.length } > MAX_PROMPT_CHARS && selectedFragments.isNotEmpty()) {
                val overflow = messages.sumOf { it.content.length } - MAX_PROMPT_CHARS
                val last = selectedFragments.last()
                val text = last.fragment.text
                val shortened = text.dropLast(overflow.coerceAtMost(text.length))
                if (shortened == text) break
                selectedFragments = selectedFragments.dropLast(1) + last.copy(
                    fragment = last.fragment.copy(text = shortened)
                )
                blocks = selectedFragments.mapIndexed { position, scored ->
                    formatFragmentBlock(position + 1, scored)
                }
                messages = buildMessages(question, input.bookName, trimmedHistory, blocks)
            }
        }

        val response = client.chat(options, messages)
        val reply = response.firstContent()
        if (reply.isBlank()) throw AiError.EmptyResponse()

        return QaOutcome(
            answerMarkdown = reply.trim(),
            citations = selectedFragments.map { it.fragment.toCitation() },
            stats = input.retrieval.stats,
            promptChars = messages.sumOf { it.content.length },
            usage = response.usage,
            droppedHistoryTurns = input.history.count { it.isUsable() } - trimmedHistory.size
        )
    }

    /**
     * 把一条召回片段格式化成带编号与出处的区块。
     *
     * 章号对用户展示时 +1（内部 0 起始，读者眼里是第 1 章）。
     */
    private fun formatFragmentBlock(position: Int, scored: ScoredFragment): String {
        val fragment = scored.fragment
        val title = fragment.chapterTitle.trim().ifEmpty {
            ReaderPromptTemplates.UNTITLED_CHAPTER
        }
        val header = ReaderPromptTemplates.FRAGMENT_HEADER_FORMAT.format(
            position,
            fragment.chapterIndex + 1,
            title
        )
        return header + "\n" + fragment.text.trim()
    }

    private fun buildMessages(
        question: String,
        bookName: String,
        history: List<ChatTurn>,
        blocks: List<String>
    ) = PromptBuilder.buildQa(
        QaPromptInput(
            bookName = bookName,
            question = question,
            history = history,
            fragmentBlocks = blocks
        )
    )

    /**
     * 按剩余预算裁剪历史，**从最旧的一轮开始丢**。
     *
     * @param usedChars 片段与问题已经占用的字符数
     * @return 由旧到新排列的可用历史，可能为空
     */
    private fun trimHistory(history: List<ChatTurn>, usedChars: Int): List<ChatTurn> {
        val usable = history.filter { it.isUsable() }
        if (usable.isEmpty()) return emptyList()

        val budget = MAX_PROMPT_CHARS - usedChars
        if (budget <= 0) return emptyList()

        val kept = ArrayDeque<ChatTurn>()
        var used = 0
        // 从最新一轮往回收：越近的上下文越值钱；单轮过大时跳过它，继续尝试更早的短轮次
        for (turn in usable.asReversed()) {
            if (kept.size >= MAX_HISTORY_TURNS) break
            val cost = turn.question.length + turn.answer.length + HISTORY_TURN_OVERHEAD
            if (used + cost > budget) continue
            kept.addFirst(turn)
            used += cost
        }
        return kept.toList()
    }

    /** [TextFragment] → [Citation] 的窄化投影。 */
    private fun TextFragment.toCitation(): Citation = Citation(
        chapterIndex = chapterIndex,
        chapterTitle = chapterTitle,
        charStart = charStart
    )

    companion object {
        /**
         * 单次 prompt 的字符硬上限。
         *
         * 用字符而不是 token：中文场景下 token 数强依赖具体分词器，本地估不准；
         * 而字符数是确定的，配上一个保守的阈值足以防住"上下文溢出"这类硬故障。
         */
        internal const val MAX_PROMPT_CHARS = 8000

        /** 最多回喂的历史轮数，超过这个数收益急剧递减而成本线性上涨。 */
        internal const val MAX_HISTORY_TURNS = 6

        /** 每轮历史的格式化开销（"问："/"答："/换行）。 */
        internal const val HISTORY_TURN_OVERHEAD = 8
    }
}
