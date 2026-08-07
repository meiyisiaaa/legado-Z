package io.legado.app.ai.retrieval

/**
 * 轻量检索（RAG 的 R）的数据模型。
 *
 * 与 `io.legado.app.ai` 其余内核一致：本包**禁止**引用任何
 * `android.*` / `androidx.*` / `io.legado.app.data.*`，全部逻辑纯 JVM 可测。
 */

/**
 * 章节正文中的一个可召回片段。
 *
 * [text] 保证是原章节正文的**连续子串**，[charStart] 是它在原文中的精确起始偏移，
 * 因此 UI 未来可以据此做「定位到原文段落」而无需二次查找。
 */
data class TextFragment(
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val charStart: Int = 0,
    val text: String = ""
) {

    /** 片段在原章节正文中的结束偏移（开区间）。 */
    val charEnd: Int get() = charStart + text.length

    /** 片段字符数。 */
    val length: Int get() = text.length
}

/** 带相关度得分的片段。得分由 [BookRetriever] 计算，无跨次可比性，仅用于排序。 */
data class ScoredFragment(
    val fragment: TextFragment,
    val score: Double = 0.0
)

/**
 * 一次检索请求。
 *
 * 默认值来自用户拍板（Q13 / A1）：窗口半径 200 章、时间预算 2000ms。
 */
data class RetrievalRequest(
    val query: String = "",
    /** 最终保留的片段条数上限 */
    val topK: Int = DEFAULT_TOP_K,
    /** 单条片段的字符上限，同时作为切片窗口大小 */
    val maxCharsPerFragment: Int = DEFAULT_MAX_CHARS_PER_FRAGMENT,
    /** 所有片段的字符总和上限，超出后停止追加（宁可少给几条，也不炸 token） */
    val maxTotalChars: Int = DEFAULT_MAX_TOTAL_CHARS,
    /**
     * 以当前章为中心的扫描半径（章）。
     * `<= 0` 表示不限范围（即「扩大到全书」，决策 A1 的二次操作）。
     */
    val windowRadius: Int = DEFAULT_WINDOW_RADIUS,
    /** 扫描的软时间预算，超时立即停止并置位 [RetrievalStats.budgetExceeded] */
    val timeBudgetMs: Long = DEFAULT_TIME_BUDGET_MS
) {

    /** 是否为全书检索（无窗口限制）。 */
    fun isFullBook(): Boolean = windowRadius <= 0

    companion object {
        const val DEFAULT_TOP_K = 5
        const val DEFAULT_MAX_CHARS_PER_FRAGMENT = 400
        const val DEFAULT_MAX_TOTAL_CHARS = 2000
        const val DEFAULT_WINDOW_RADIUS = 200
        const val DEFAULT_TIME_BUDGET_MS = 2000L
    }
}

/** 一次检索的过程统计，用于 UI 展示「扫描了多少章、花了多久」。 */
data class RetrievalStats(
    /** 实际访问过的章节数（含未缓存而跳过的） */
    val scannedChapters: Int = 0,
    /** 其中真正读到正文的章节数 */
    val cachedChapters: Int = 0,
    val totalChapters: Int = 0,
    val elapsedMs: Long = 0L,
    /** 是否因 [RetrievalRequest.timeBudgetMs] 提前终止 */
    val budgetExceeded: Boolean = false,
    /**
     * 本次是否放弃了窗口限制、按全书范围扫描。
     *
     * 命名沿用架构文档；语义上等价于 `request.isFullBook()`，
     * 冗余保留是为了让 UI 只依赖 [RetrievalResult] 就能渲染「检索范围」标签。
     */
    val usedFallback: Boolean = false
) {

    companion object {
        val EMPTY = RetrievalStats()
    }
}

/**
 * 检索结果。
 *
 * [isEmpty] 与 [totalChars] 做成派生属性而非构造参数：它们完全由 [fragments] 决定，
 * 让调用方自己传会引入「字段互相矛盾」的非法状态。
 */
data class RetrievalResult(
    /** 按得分降序排列，长度 <= [RetrievalRequest.topK] */
    val fragments: List<ScoredFragment> = emptyList(),
    val stats: RetrievalStats = RetrievalStats.EMPTY
) {

    /** 零命中。**这是「不发网络请求」的判定依据**，见 [io.legado.app.ai.AiError.RetrievalEmpty]。 */
    val isEmpty: Boolean get() = fragments.isEmpty()

    val isNotEmpty: Boolean get() = fragments.isNotEmpty()

    /** 所有片段的字符总和，用于成本预估。 */
    val totalChars: Int get() = fragments.sumOf { it.fragment.length }

    companion object {
        val EMPTY = RetrievalResult()
    }
}
