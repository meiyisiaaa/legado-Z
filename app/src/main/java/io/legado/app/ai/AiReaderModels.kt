package io.legado.app.ai

import io.legado.app.ai.client.Usage
import io.legado.app.ai.retrieval.RetrievalResult
import io.legado.app.ai.retrieval.RetrievalStats

/**
 * AI 读者助手（Phase 2）跨层共享的数据模型。
 *
 * 与 [AiModels.kt][AiChatOptions] 同样受「内核零 Android 依赖」约束：
 * 本文件**禁止**引用任何 `android.*` / `androidx.*` / `io.legado.app.data.*`，
 * 以保证问答/赏析内核可在纯 JVM 单元测试中运行。
 *
 * 命名约定：`XxxInput` 为引擎入参，`XxxOutcome` 为引擎产出，
 * 与 v1 的 [GenerateInput] / [AiSourceOutcome] 保持一致。
 */

/**
 * 读者助手的可调参数快照。
 *
 * 与 [AiChatOptions] 拆开而不是合并进去，原因有二：
 * 1. [AiChatOptions] 是 v1 已冻结的**网络请求参数**契约，混入检索参数会污染语义；
 * 2. 书源助手侧完全用不到这些字段，拆开可让两条链路的 prompt 与引擎签名各自最小化。
 *
 * 所有 Int 字段在 [AiConfig.readerSnapshot] 读取时已由 [AiConfig.clamp] 钳制到合法区间，
 * 下游无需重复校验；但构造函数仍提供默认值，便于单测直接 `AiReaderOptions()`。
 */
data class AiReaderOptions(
    /** 单次问答召回的片段条数上限 */
    val retrievalTopK: Int = DEFAULT_TOP_K,
    /** 单条召回片段送入 LLM 的字符上限 */
    val retrievalMaxChars: Int = DEFAULT_RETRIEVAL_MAX_CHARS,
    /** 「AI 赏析」允许送出的选中文本字符上限，超出即截断并提示用户 */
    val explainMaxChars: Int = DEFAULT_EXPLAIN_MAX_CHARS,
    /** 阅读器 AI 入口总开关 */
    val readerAssistantEnabled: Boolean = DEFAULT_READER_ASSISTANT_ENABLED
) {

    /**
     * 单次问答送入 LLM 的**全部片段**字符上限。
     *
     * 不单独做成配置项：它是 topK 与单片段上限的乘积，额外暴露只会让用户配出自相矛盾的组合。
     */
    fun maxTotalChars(): Int = retrievalTopK * retrievalMaxChars

    companion object {
        const val DEFAULT_TOP_K = 5
        const val DEFAULT_RETRIEVAL_MAX_CHARS = 400
        const val DEFAULT_EXPLAIN_MAX_CHARS = 1000

        /** 默认开启：Phase 2 的核心卖点，关闭需用户显式操作 */
        const val DEFAULT_READER_ASSISTANT_ENABLED = true

        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 10
        const val MIN_RETRIEVAL_MAX_CHARS = 100
        const val MAX_RETRIEVAL_MAX_CHARS = 1000
        const val MIN_EXPLAIN_MAX_CHARS = 200
        const val MAX_EXPLAIN_MAX_CHARS = 2000
    }
}

/**
 * 一轮已完成的问答，用于多轮对话的上下文回喂。
 *
 * 只保留问与答的**文本**，不保留召回片段 —— 历史轮次的片段一律不重复上传（省 token + 隐私）。
 */
data class ChatTurn(
    val question: String = "",
    val answer: String = ""
) {

    fun isUsable(): Boolean = question.isNotBlank() && answer.isNotBlank()
}

/**
 * 答案中引用的出处，供 UI 渲染「跳转到第 N 章」。
 *
 * @param chapterIndex 章节下标，与 [io.legado.app.model.ReadBook.durChapterIndex] 同一坐标系
 * @param chapterTitle 章节标题，UI 直接展示
 * @param charStart 片段在章节正文中的起始字符偏移，便于未来做「定位到段落」
 */
data class Citation(
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val charStart: Int = 0
)

/**
 * 检索问答入参。
 *
 * [retrieval] 由 UI 层先行调用 [io.legado.app.ai.retrieval.BookRetriever] 产出后传入 ——
 * 引擎**不负责检索**，这样才能做到「零命中不发网络请求」由引擎统一裁决，
 * 同时让检索与问答两块都能被独立单测。
 */
data class QaInput(
    val bookName: String = "",
    val question: String = "",
    /** 历史轮次，**由旧到新**排列；超预算时由引擎从最旧开始丢弃（决策 A2） */
    val history: List<ChatTurn> = emptyList(),
    val retrieval: RetrievalResult = RetrievalResult.EMPTY
)

/** 检索问答产出。 */
data class QaOutcome(
    /** LLM 回复的 Markdown 原文，由 UI 用 markwon 渲染 */
    val answerMarkdown: String = "",
    /** 本轮实际送出的片段出处，按召回顺序排列 */
    val citations: List<Citation> = emptyList(),
    val stats: RetrievalStats = RetrievalStats.EMPTY,
    /** 本轮 prompt 的字符总数，用于 UI 预告成本 */
    val promptChars: Int = 0,
    val usage: Usage? = null,
    /** 因预算裁剪而未送出的历史轮数（决策 A2），> 0 时 UI 可提示「已省略较早对话」 */
    val droppedHistoryTurns: Int = 0
)

/**
 * 选中文本赏析入参。
 *
 * 隐私红线：**只**包含用户主动选中的文本，绝不附带上下文章节全文。
 */
data class ExplainInput(
    val bookName: String = "",
    val chapterTitle: String = "",
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
 * 下面两个类型是 Engine 与 [io.legado.app.ai.prompt.PromptBuilder] 之间的契约。
 *
 * 它们与上面的 `XxxInput` **刻意分开**，只装「确定要写进 prompt 的东西」：
 * 因此从类型签名上就能一眼看出 prompt 里绝不可能出现 apiKey / endpoint / 未召回的正文，
 * 而不必逐行读实现去确认。这与 v1 「PromptBuilder 方法不接收 AiChatOptions」是同一条思路。
 */

/**
 * 组装问答 prompt 所需的全部（且仅需的）素材。
 *
 * @param fragmentBlocks 已格式化好的召回片段文本块，**由 Engine 负责裁剪与编号**
 */
data class QaPromptInput(
    val bookName: String = "",
    val question: String = "",
    /** 参与本轮的历史（已由 Engine 按预算裁剪），由旧到新 */
    val history: List<ChatTurn> = emptyList(),
    val fragmentBlocks: List<String> = emptyList()
)

/** 组装赏析 prompt 所需的全部素材。[selection] 已由 Engine 截断到上限。 */
data class ExplainPromptInput(
    val bookName: String = "",
    val chapterTitle: String = "",
    val selection: String = ""
)
