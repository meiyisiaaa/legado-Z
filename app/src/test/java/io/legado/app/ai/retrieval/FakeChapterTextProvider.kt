package io.legado.app.ai.retrieval

/**
 * 内存版章节数据源，用于在纯 JVM 下驱动 [BookRetriever]。
 *
 * 额外记录 [readIndexes]：检索器「跳过未缓存章节」「由近及远扫描」「时间预算内提前收手」
 * 这三条行为都只能通过**实际读了哪些章、按什么顺序读**来断言。
 *
 * @param chapters 全书章节；`text` 为 null 表示尚未缓存到本地
 * @param currentIndex 当前阅读位置
 */
internal class FakeChapterTextProvider(
    private val chapters: List<Chapter>,
    private val currentIndex: Int = 0
) : ChapterTextProvider {

    /** 一章的内存表示；[text] 为 null 即「未缓存」。 */
    internal data class Chapter(val title: String, val text: String?)

    private val readOrder = mutableListOf<Int>()

    /** 依次真正调用过 [textAt] 的章节下标。 */
    val readIndexes: List<Int> get() = readOrder

    override fun chapterCount(): Int = chapters.size

    override fun currentIndex(): Int = currentIndex

    override fun titleAt(index: Int): String = chapters.getOrNull(index)?.title.orEmpty()

    override fun isCached(index: Int): Boolean = chapters.getOrNull(index)?.text != null

    override fun textAt(index: Int): String? {
        readOrder.add(index)
        return chapters.getOrNull(index)?.text
    }

    companion object {

        /** 全部章节都已缓存的便捷构造。 */
        fun cached(currentIndex: Int, vararg bodies: String): FakeChapterTextProvider =
            FakeChapterTextProvider(
                chapters = bodies.mapIndexed { i, body -> Chapter("第${i + 1}章", body) },
                currentIndex = currentIndex
            )
    }
}
