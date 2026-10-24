package io.legado.app.ai

import io.legado.app.ai.client.Usage

/**
 * AI 读者助手（Phase 2）跨层共享的数据模型。
 *
 * 与 [AiModels.kt][AiChatOptions] 同样受「内核零 Android 依赖」约束：
 * 本文件**禁止**引用任何 `android.*` / `androidx.*` / `io.legado.app.data.*`，
 * 以保证选区问答/赏析内核可在纯 JVM 单元测试中运行。
 *
 * 命名约定：`XxxInput` 为引擎入参，`XxxOutcome` 为引擎产出，
 * 与 v1 的 [GenerateInput] / [AiSourceOutcome] 保持一致。
 */

/**
 * 读者助手的可调参数快照。
 *
 * 与 [AiChatOptions] 拆开，避免把阅读器选区限制混入通用网络请求参数。
 *
 * 所有 Int 字段在 [AiConfig.readerSnapshot] 读取时已由 [AiConfig.clamp] 钳制到合法区间，
 * 下游无需重复校验；但构造函数仍提供默认值，便于单测直接 `AiReaderOptions()`。
 */
data class AiReaderOptions(
    /** 选区 AI 允许送出的文本字符上限，超出即截断并提示用户 */
    val explainMaxChars: Int = DEFAULT_EXPLAIN_MAX_CHARS,
    /** 阅读器 AI 入口总开关 */
    val readerAssistantEnabled: Boolean = DEFAULT_READER_ASSISTANT_ENABLED
) {
    companion object {
        const val DEFAULT_EXPLAIN_MAX_CHARS = 1000

        /** 默认开启：Phase 2 的核心卖点，关闭需用户显式操作 */
        const val DEFAULT_READER_ASSISTANT_ENABLED = true

        const val MIN_EXPLAIN_MAX_CHARS = 200
        const val MAX_EXPLAIN_MAX_CHARS = 2000
    }
}

/**
 * 选中文本赏析入参。
 *
 * 隐私红线：**只**包含用户主动选中的文本，绝不附带上下文章节全文。
 */
data class ExplainInput(
    val selection: String = "",
    val maxChars: Int = AiReaderOptions.DEFAULT_EXPLAIN_MAX_CHARS
)

/** 选中文本赏析产出。 */
data class ExplainOutcome(
    val answerMarkdown: String = "",
    /** 选中文本是否因超过 [ExplainInput.maxChars] 被截断（决策 A4，UI 需顶部提示） */
    val truncated: Boolean = false,
    /** 实际送出的选中文本字符数 */
    val sentChars: Int = 0,
    val usage: Usage? = null
)

/* ==================== prompt 层 DTO ==================== */

/*
 * 下面的类型是 Engine 与 [io.legado.app.ai.prompt.PromptBuilder] 之间的契约。
 *
 * 它们与上面的 `XxxInput` **刻意分开**，只装「确定要写进 prompt 的东西」：
 * 因此从类型签名上就能一眼看出 prompt 里绝不可能出现 apiKey / endpoint / 未召回的正文，
 * 而不必逐行读实现去确认。这与 v1 「PromptBuilder 方法不接收 AiChatOptions」是同一条思路。
 */

/** 组装选区 AI prompt 所需的全部素材。[selection] 已由 Engine 截断到上限。 */
data class ExplainPromptInput(
    val selection: String = "",
    val question: String = "",
    val history: List<SelectionQaPair> = emptyList()
)

/** 仅包含成功完成的同选区问答。 */
data class SelectionQaPair(val question: String, val answer: String)

/** 面板独占的有界上下文；令牌同时隔离已取消及旧会话的结果。 */
class SelectionQaSession {
    private val pairs = ArrayDeque<SelectionQaPair>()
    private var generation = 0L
    private var key: String? = null
    val history: List<SelectionQaPair> get() = pairs.toList()

    fun bind(value: String): Boolean {
        if (key == value) return false
        clear()
        key = value
        return true
    }

    fun begin(): Long = ++generation
    fun isCurrent(token: Long): Boolean = token == generation
    fun cancel() { generation++ }
    fun clear() { cancel(); pairs.clear() }

    fun complete(token: Long, pair: SelectionQaPair): Boolean {
        if (!isCurrent(token)) return false
        pairs.addLast(pair)
        while (pairs.size > MAX_PAIRS || pairs.sumOf { it.question.length + it.answer.length } > MAX_CHARS) {
            pairs.removeFirst()
        }
        return true
    }

    companion object {
        const val MAX_PAIRS = 4
        const val MAX_CHARS = 8000
        const val MAX_QUESTION_CHARS = 2000
    }
}
