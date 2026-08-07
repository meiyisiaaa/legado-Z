package io.legado.app.ai.retrieval

/**
 * 章节正文的只读数据源。
 *
 * **这是检索内核与 Android 之间唯一的解耦点。** [BookRetriever] 只认这个接口，
 * 因此它可以在纯 JVM 单测里跑（喂一个内存实现），
 * 而真机上由 [io.legado.app.help.ai.CachedBookTextProvider] 提供
 * Room + 本地缓存文件的实现。
 *
 * 契约：
 * - 所有方法都可能被高频调用，实现方**必须**自己做缓存（真机实现使用 LRU）。
 * - [textAt] 只返回**已缓存到本地**的正文，**绝不触发网络下载** ——
 *   检索是一个「本地、离线、零副作用」的操作，这是隐私与流量的双重红线。
 * - 下标一律以 0 起始，与 [io.legado.app.model.ReadBook.durChapterIndex] 同坐标系。
 */
interface ChapterTextProvider {

    /** 全书章节总数；返回 0 表示无可检索内容。 */
    fun chapterCount(): Int

    /**
     * 当前阅读位置的章节下标，用于「由近及远」扫描与位置衰减打分。
     * 无有效阅读位置时返回 0。
     */
    fun currentIndex(): Int

    /** 章节标题；越界或缺失时返回空串（不得抛异常）。 */
    fun titleAt(index: Int): String

    /**
     * 该章正文是否已在本地就绪。
     *
     * 用于在真正读文件前快速跳过未缓存章节 —— 这是把「扫描 3000 章」压进
     * [RetrievalRequest.timeBudgetMs] 的关键。
     */
    fun isCached(index: Int): Boolean

    /**
     * 读取章节正文。未缓存 / 读取失败时返回 null，**不得抛异常**。
     *
     * 允许发生磁盘 IO，调用方（[BookRetriever.retrieve]）已保证运行在 IO 调度器上。
     */
    fun textAt(index: Int): String?
}
