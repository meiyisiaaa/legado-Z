package io.legado.app.ai.retrieval

/**
 * 把章节正文切成可召回的片段。
 *
 * 策略（架构文档 §4.3）：**段落优先 + 定长窗口兜底 + 重叠**
 *
 * 1. 先按换行切段，尽量让一个片段落在完整的段落边界上 —— 小说的语义单元就是段落，
 *    在句中截断会让召回的片段读起来支离破碎；
 * 2. 连续短段落合并，直到逼近 [DEFAULT_MAX_CHARS]，避免产生大量一两句话的碎片；
 * 3. 单段本身就超长时，退化为定长窗口切分，窗口间保留 [DEFAULT_OVERLAP] 字重叠，
 *    防止关键句恰好卡在窗口边界上被"切成两半"而两边都召不回。
 *
 * 不变式：每个 [TextFragment.text] 都是 `text.substring(charStart, charStart + length)`，
 * 即片段是原文的**精确连续子串**，偏移可直接用于原文定位。
 */
object FragmentSplitter {

    const val DEFAULT_MAX_CHARS = 400
    const val DEFAULT_OVERLAP = 80

    /** 片段长度下限：再小就没有语义了，同时防止用户把配置调到 1 导致片段爆炸。 */
    const val MIN_MAX_CHARS = 40

    /**
     * 切分章节正文。
     *
     * @param chapterIndex 章节下标，原样写入产出片段
     * @param chapterTitle 章节标题，原样写入产出片段
     * @param text 章节正文
     * @param maxChars 单片段字符上限，会被钳制到 `>= [MIN_MAX_CHARS]`
     * @param overlap 超长段落做窗口切分时的重叠字符数，会被钳制到 `[0, maxChars / 2]`
     * @return 按出现顺序排列的片段；正文为空白时返回空列表
     */
    fun split(
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
        maxChars: Int = DEFAULT_MAX_CHARS,
        overlap: Int = DEFAULT_OVERLAP
    ): List<TextFragment> {
        if (text.isBlank()) return emptyList()

        val safeMax = maxChars.coerceAtLeast(MIN_MAX_CHARS)
        val safeOverlap = overlap.coerceIn(0, safeMax / 2)
        val fragments = ArrayList<TextFragment>()

        var groupStart = -1
        var groupEnd = -1

        fun flushGroup() {
            if (groupStart < 0) return
            emit(fragments, chapterIndex, chapterTitle, text, groupStart, groupEnd)
            groupStart = -1
            groupEnd = -1
        }

        for (para in paragraphSpans(text)) {
            if (para.length > safeMax) {
                // 超长段落：先冲掉待合并的组，再对本段做窗口切分
                flushGroup()
                splitOversizedParagraph(
                    fragments, chapterIndex, chapterTitle, text,
                    para.start, para.end, safeMax, safeOverlap
                )
                continue
            }
            if (groupStart < 0) {
                groupStart = para.start
                groupEnd = para.end
                continue
            }
            if (para.end - groupStart <= safeMax) {
                // 合并进当前组（组的跨度含段间换行，这样子串才连续）
                groupEnd = para.end
            } else {
                flushGroup()
                groupStart = para.start
                groupEnd = para.end
            }
        }
        flushGroup()
        return fragments
    }

    /** 对 `[start, end)` 这一超长段落做定长窗口 + 重叠切分。 */
    private fun splitOversizedParagraph(
        out: MutableList<TextFragment>,
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
        start: Int,
        end: Int,
        maxChars: Int,
        overlap: Int
    ) {
        // 步长至少为 1，防止 overlap == maxChars 时死循环（钳制后不会发生，此处为防御）
        val step = (maxChars - overlap).coerceAtLeast(1)
        var pos = start
        while (pos < end) {
            val stop = (pos + maxChars).coerceAtMost(end)
            emit(out, chapterIndex, chapterTitle, text, pos, stop)
            if (stop >= end) break
            pos += step
        }
    }

    /** 产出一个片段；空白片段直接丢弃。 */
    private fun emit(
        out: MutableList<TextFragment>,
        chapterIndex: Int,
        chapterTitle: String,
        text: String,
        start: Int,
        end: Int
    ) {
        if (end <= start) return
        val body = text.substring(start, end)
        if (body.isBlank()) return
        out.add(
            TextFragment(
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                charStart = start,
                text = body
            )
        )
    }

    /** 原文中的半开区间 `[start, end)`。用具名类型而不是 IntRange，避免闭/开区间语义混淆。 */
    private data class Span(val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    /**
     * 按换行切出**非空白**段落的 `[start, end)` 区间。
     *
     * 兼容 `\n` / `\r\n` / `\r` 三种换行；返回的区间已剔除首尾空白，
     * 因此 `text.substring(start, end)` 不会以空格或换行开头。
     */
    private fun paragraphSpans(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var lineStart = 0
        var i = 0
        val length = text.length
        while (i <= length) {
            val isBreak = i == length || text[i] == '\n' || text[i] == '\r'
            if (!isBreak) {
                i++
                continue
            }
            addTrimmed(spans, text, lineStart, i)
            // 跳过 \r\n 组合，避免多算一个空段
            if (i < length && text[i] == '\r' && i + 1 < length && text[i + 1] == '\n') {
                i++
            }
            i++
            lineStart = i
        }
        return spans
    }

    /** 把 `[start, end)` 去掉首尾空白后加入 [out]；全空白则跳过。 */
    private fun addTrimmed(out: MutableList<Span>, text: String, start: Int, end: Int) {
        var from = start
        var to = end
        while (from < to && text[from].isWhitespace()) from++
        while (to > from && text[to - 1].isWhitespace()) to--
        if (to > from) {
            out.add(Span(from, to))
        }
    }
}
