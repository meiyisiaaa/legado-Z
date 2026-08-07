package io.legado.app.ai.retrieval

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 全书轻量检索器：把「用户的问题」变成「几段最相关的原文片段」。
 *
 * 这是 RAG 中的 R，也是本期最重要的一块 —— 它决定了「只发命中片段」这条隐私红线
 * 能否成立：召回为空时 [io.legado.app.ai.engine.ReaderQaEngine] 就不会发起任何网络请求。
 *
 * ### 算法（架构文档 §4.4）
 * 打分 = `tf × idfLite`（词频×局部逆文档频率）
 * `× (1 + 覆盖率)` + `标题加权` + `邻近度加权`，最后乘上**按章距的位置衰减**。
 *
 * - **idfLite**：全书 df 需要预先建索引，代价与收益完全不匹配。这里改用
 *   **章内 df**——同一章里几乎每个片段都出现的词（"的""说道"）权重自然被压低，
 *   效果逼近全局 idf，而代价是 O(1) 的额外开销。
 * - **位置衰减**：读者的问题绝大多数指向"刚读到的地方"，离当前章越远越应该降权。
 *
 * ### 性能护栏
 * - **由近及远交替扫描**：`cur, cur-1, cur+1, cur-2, cur+2 …`，
 *   配合 [RetrievalRequest.timeBudgetMs] 软超时，保证长篇（数千章）也能秒回；
 * - **未缓存章节直接跳过**（[ChapterTextProvider.isCached]），绝不触发下载；
 * - **有界 topK 堆**：候选片段不全量堆在内存里，峰值内存与全书体量无关。
 *
 * ### 可测试性
 * [nowMs] 允许注入假时钟，让"时间预算耗尽"这条分支能在单测里被稳定复现。
 */
class BookRetriever(
    private val provider: ChapterTextProvider,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * 执行一次检索。
     *
     * 本方法可能做大量磁盘 IO，调用方需保证运行在 IO 调度器上；
     * 内部在每章开始处调用 `ensureActive()`，取消可在百毫秒内生效。
     *
     * @return 永不为 null；无命中时 [RetrievalResult.isEmpty] 为 true
     */
    suspend fun retrieve(request: RetrievalRequest): RetrievalResult {
        val startedAt = nowMs()
        val totalChapters = provider.chapterCount()
        val queryTokens = Tokenizer.distinctTokens(request.query)
        if (totalChapters <= 0 || queryTokens.isEmpty()) {
            return RetrievalResult(
                fragments = emptyList(),
                stats = RetrievalStats(
                    totalChapters = totalChapters.coerceAtLeast(0),
                    elapsedMs = nowMs() - startedAt,
                    usedFallback = request.isFullBook()
                )
            )
        }

        val queryTokenSet = queryTokens.toHashSet()
        val center = provider.currentIndex().coerceIn(0, totalChapters - 1)
        val topK = request.topK.coerceAtLeast(1)
        // 队首为「最差」的候选，超出 topK 时直接淘汰队首，峰值内存与全书体量无关
        val heap = PriorityQueue(topK + 1, worstFirstComparator(center))

        var scanned = 0
        var cached = 0
        var budgetExceeded = false

        for (index in scanOrder(totalChapters, center, request.windowRadius)) {
            currentCoroutineContext().ensureActive()
            if (nowMs() - startedAt >= request.timeBudgetMs) {
                budgetExceeded = true
                break
            }
            scanned++
            if (!provider.isCached(index)) continue
            val text = provider.textAt(index)
            if (text.isNullOrBlank()) continue
            cached++

            val title = provider.titleAt(index)
            val fragments = FragmentSplitter.split(
                chapterIndex = index,
                chapterTitle = title,
                text = text,
                maxChars = request.maxCharsPerFragment
            )
            if (fragments.isEmpty()) continue

            scoreChapter(
                fragments = fragments,
                queryTokens = queryTokens,
                queryTokenSet = queryTokenSet,
                chapterTitle = title,
                distance = abs(index - center),
                sink = { scored ->
                    heap.offer(scored)
                    if (heap.size > topK) heap.poll()
                }
            )
        }

        val ranked = heap.sortedWith(worstFirstComparator(center).reversed())
        return RetrievalResult(
            fragments = capByTotalChars(ranked, request.maxTotalChars),
            stats = RetrievalStats(
                scannedChapters = scanned,
                cachedChapters = cached,
                totalChapters = totalChapters,
                elapsedMs = nowMs() - startedAt,
                budgetExceeded = budgetExceeded,
                usedFallback = request.isFullBook()
            )
        )
    }

    /**
     * 给一章内的所有片段打分，得分 > 0 的通过 [sink] 交出去。
     *
     * 用回调而不是返回 List：这样候选片段不会在章级别再堆一份，
     * 直接流进外层的有界堆。
     */
    private fun scoreChapter(
        fragments: List<TextFragment>,
        queryTokens: List<String>,
        queryTokenSet: HashSet<String>,
        chapterTitle: String,
        distance: Int,
        sink: (ScoredFragment) -> Unit
    ) {
        val tokenLists = fragments.map { Tokenizer.tokenize(it.text) }
        val documentFrequency = chapterDocumentFrequency(tokenLists, queryTokenSet)
        val fragmentCount = fragments.size
        val titleTokens = Tokenizer.distinctTokens(chapterTitle).toHashSet()
        val titleHits = queryTokens.count { titleTokens.contains(it) }
        val decay = 1.0 / (1.0 + DECAY_ALPHA * distance)

        for (i in fragments.indices) {
            val score = scoreFragment(
                tokens = tokenLists[i],
                queryTokens = queryTokens,
                queryTokenSet = queryTokenSet,
                documentFrequency = documentFrequency,
                fragmentCount = fragmentCount,
                titleHits = titleHits
            )
            if (score <= 0.0) continue
            sink(ScoredFragment(fragments[i], score * decay))
        }
    }

    /** 统计每个**查询词**在本章多少个片段里出现过（章内 df）。 */
    private fun chapterDocumentFrequency(
        tokenLists: List<List<String>>,
        queryTokenSet: HashSet<String>
    ): HashMap<String, Int> {
        val df = HashMap<String, Int>(queryTokenSet.size * 2)
        val seen = HashSet<String>()
        for (tokens in tokenLists) {
            seen.clear()
            for (token in tokens) {
                if (queryTokenSet.contains(token) && seen.add(token)) {
                    df[token] = (df[token] ?: 0) + 1
                }
            }
        }
        return df
    }

    /**
     * 单片段打分。
     *
     * **正文零命中直接判 0**：否则一旦章节标题命中，该章所有片段都会拿到相同的标题分，
     * 整个 topK 会被同一章刷屏。标题分只用来**放大**已经命中的片段。
     */
    private fun scoreFragment(
        tokens: List<String>,
        queryTokens: List<String>,
        queryTokenSet: HashSet<String>,
        documentFrequency: Map<String, Int>,
        fragmentCount: Int,
        titleHits: Int
    ): Double {
        if (tokens.isEmpty()) return 0.0

        val termFrequency = HashMap<String, Int>()
        val positions = HashMap<String, MutableList<Int>>()
        for (pos in tokens.indices) {
            val token = tokens[pos]
            if (!queryTokenSet.contains(token)) continue
            termFrequency[token] = (termFrequency[token] ?: 0) + 1
            positions.getOrPut(token) { ArrayList() }.add(pos)
        }
        val matchedDistinct = termFrequency.size
        if (matchedDistinct == 0) return 0.0

        // 除以 sqrt(片段长度)：抵消"长片段天然命中更多"的偏置
        val lengthNorm = sqrt(tokens.size.toDouble())
        var tfIdf = 0.0
        for ((token, count) in termFrequency) {
            val df = documentFrequency[token] ?: 1
            tfIdf += (count / lengthNorm) * idfLite(fragmentCount, df)
        }
        if (tfIdf <= 0.0) return 0.0

        val coverage = matchedDistinct.toDouble() / queryTokens.size
        val titleScore = TITLE_BONUS * titleHits / queryTokens.size
        val proximityScore = PROXIMITY_BONUS * proximityRatio(positions.values)
        return tfIdf * (1.0 + coverage) + titleScore + proximityScore
    }

    /**
     * 章内逆文档频率。
     *
     * `ln(1 + N / (1 + df))`：df 越接近 N（本章处处都有的高频虚词）值越趋近 ln(2)≈0.69，
     * df == 1（只在一个片段里出现的特征词）时值最大。加 1 是标准的零除保护与平滑。
     */
    private fun idfLite(fragmentCount: Int, documentFrequency: Int): Double =
        ln(1.0 + fragmentCount.toDouble() / (1.0 + documentFrequency))

    /**
     * 邻近度：命中词挤得越近，越可能是一句完整的答案而不是碰巧撞词。
     *
     * 取「覆盖全部命中词的最小窗口」，返回 `命中词数 / 窗口宽度`，值域 (0, 1]。
     * 只命中一个词时无邻近度可言，返回 0。
     */
    private fun proximityRatio(positionsByToken: Collection<List<Int>>): Double {
        val distinct = positionsByToken.size
        if (distinct < 2) return 0.0
        val span = minCoveringSpan(positionsByToken.toList())
        if (span <= 0) return 0.0
        return distinct.toDouble() / span
    }

    /**
     * 「最小区间覆盖 k 组有序位置」的经典滑动窗口解法。
     *
     * 把所有位置按下标归并排序后，用双指针维持一个「包含全部 k 组」的窗口并不断收缩。
     * 复杂度 O(M log M)，M 为命中位置总数（片段级别，通常个位到几十）。
     */
    private fun minCoveringSpan(positionsByToken: List<List<Int>>): Int {
        val groupCount = positionsByToken.size
        if (groupCount == 0) return 0
        if (groupCount == 1) return 1

        val merged = ArrayList<IntArray>()
        for (group in positionsByToken.indices) {
            for (pos in positionsByToken[group]) {
                merged.add(intArrayOf(pos, group))
            }
        }
        if (merged.isEmpty()) return 0
        merged.sortBy { it[0] }

        val counts = IntArray(groupCount)
        var covered = 0
        var left = 0
        var best = Int.MAX_VALUE
        for (right in merged.indices) {
            if (counts[merged[right][1]]++ == 0) covered++
            while (covered == groupCount) {
                val width = merged[right][0] - merged[left][0] + 1
                if (width < best) best = width
                if (--counts[merged[left][1]] == 0) covered--
                left++
            }
        }
        return if (best == Int.MAX_VALUE) 0 else best
    }

    /**
     * 按总字符预算裁剪已排序的候选。
     *
     * 至少保留一条：单条片段长度已由 [RetrievalRequest.maxCharsPerFragment] 约束，
     * 真出现"第一条就超预算"也说明用户把参数配歪了，给一条总比给零条有用。
     */
    private fun capByTotalChars(
        ranked: List<ScoredFragment>,
        maxTotalChars: Int
    ): List<ScoredFragment> {
        if (ranked.isEmpty()) return emptyList()
        val limit = maxTotalChars.coerceAtLeast(1)
        val kept = ArrayList<ScoredFragment>(ranked.size)
        var used = 0
        for (candidate in ranked) {
            val remaining = limit - used
            if (remaining <= 0) break
            if (candidate.fragment.length > remaining) {
                if (kept.isEmpty()) {
                    kept.add(
                        candidate.copy(
                            fragment = candidate.fragment.copy(text = candidate.fragment.text.take(remaining))
                        )
                    )
                }
                break
            }
            val fragment = candidate
            kept.add(fragment)
            used += fragment.fragment.length
        }
        return kept
    }

    /**
     * 由近及远交替扫描：`cur, cur-1, cur+1, cur-2, cur+2 …`
     *
     * 用 [Sequence] 而不是先算出下标数组：万卷长篇也不会为此额外分配几十 KB，
     * 且时间预算一到就能立刻停止产出。
     *
     * @param radius `<= 0` 表示不限范围（全书）
     */
    private fun scanOrder(total: Int, center: Int, radius: Int): Sequence<Int> = sequence {
        yield(center)
        val limit = if (radius <= 0) total else radius
        var offset = 1
        while (offset <= limit) {
            val left = center - offset
            val right = center + offset
            var emitted = false
            if (left >= 0) {
                yield(left)
                emitted = true
            }
            if (right < total) {
                yield(right)
                emitted = true
            }
            if (!emitted) break
            offset++
        }
    }

    /**
     * 「最差优先」比较器，直接用作有界最小堆的排序依据；取反即为最终展示顺序。
     *
     * 优先级：得分高者更优 → 距当前章近者更优 → 章内偏移小者更优 → 章号小者更优。
     * 后三级只是**打破平局**。这是一个**全序**：任意两个候选都能分出先后，
     * 因此同一份数据的检索结果逐条可复现，回归测试才能断言具体顺序。
     */
    private fun worstFirstComparator(center: Int): Comparator<ScoredFragment> =
        compareBy<ScoredFragment> { it.score }
            .thenByDescending { abs(it.fragment.chapterIndex - center) }
            .thenByDescending { it.fragment.charStart }
            .thenByDescending { it.fragment.chapterIndex }

    companion object {
        /** 章节标题命中的加权系数 */
        internal const val TITLE_BONUS = 0.35

        /** 命中词邻近度的加权系数 */
        internal const val PROXIMITY_BONUS = 0.25

        /** 位置衰减速率：距当前章 50 章时权重约降至 1/2 */
        internal const val DECAY_ALPHA = 0.02
    }
}
