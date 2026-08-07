package io.legado.app.help.ai

import io.legado.app.ai.retrieval.ChapterTextProvider
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.model.ReadBook

/**
 * [ChapterTextProvider] 的真机实现：只读**已经缓存到本地**的章节正文。
 *
 * 这是检索内核（纯 Kotlin）与 Android 世界之间唯一的桥。内核只认接口，
 * 因此所有 Room / 文件 IO / 阅读进度的脏活都收敛在这一个类里。
 *
 * ### 三条硬约束
 * 1. **绝不联网**：[textAt] 只走 [BookHelp.getContent]（读本地缓存文件），
 *    未缓存的章节一律返回 null。用户没下载过的章节，检索就当它不存在 ——
 *    否则「问一句话触发全书下载」既费流量又慢得没法用。
 * 2. **不改数据**：全程只读，不写 Room、不写文件、不动阅读进度。
 * 3. **自带 LRU**：[BookRetriever][io.legado.app.ai.retrieval.BookRetriever]
 *    会对同一章反复取文本（切片 + 打分），没有缓存就会退化成反复读盘。
 *
 * ### 生命周期
 * 章节列表在 [current] 里一次性快照，之后不再回查数据库 ——
 * 一次检索最多两秒，期间目录发生变化的概率可以忽略，
 * 而每章都查一次 Room 会让「扫描 3000 章」直接超时。
 *
 * 构造与 [current] 都会发生磁盘 IO，**必须在 IO 线程调用**。
 */
class CachedBookTextProvider private constructor(
    private val book: Book,
    private val chapters: List<BookChapter>,
    private val currentChapterIndex: Int
) : ChapterTextProvider {

    /** accessOrder = true 的 LinkedHashMap 即最近最少使用序，配合 [cachedChars] 做容量控制。 */
    private val cache = LinkedHashMap<Int, String>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    private var cachedChars = 0

    override fun chapterCount(): Int = chapters.size

    override fun currentIndex(): Int = currentChapterIndex

    override fun titleAt(index: Int): String =
        chapters.getOrNull(index)?.title.orEmpty()

    override fun isCached(index: Int): Boolean {
        synchronized(cache) {
            if (cache.containsKey(index)) return true
        }
        val chapter = chapters.getOrNull(index) ?: return false
        return runCatching { BookHelp.hasContent(book, chapter) }.getOrDefault(false)
    }

    override fun textAt(index: Int): String? {
        synchronized(cache) {
            cache[index]?.let { return it }
        }
        val chapter = chapters.getOrNull(index) ?: return null
        // 接口契约要求「不得抛异常」：文件被清理、权限变更、编码异常都只当作"没有"
        val raw = runCatching {
            if (!BookHelp.hasContent(book, chapter)) null else BookHelp.getContent(book, chapter)
        }.getOrNull() ?: return null

        val text = clean(raw)
        if (text.isEmpty()) return null
        putCache(index, text)
        return text
    }

    /* ---------------- 内部实现 ---------------- */

    /**
     * 把缓存正文清洗成适合检索的纯文本。
     *
     * 只做**减法**：去掉图片标签与多余空白。绝不做替换或改写 ——
     * 片段最终会原样送进 prompt 并展示给用户，改了字就等于伪造原文。
     */
    private fun clean(raw: String): String {
        return IMG_TAG.replace(raw, "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun putCache(index: Int, text: String) {
        synchronized(cache) {
            cache.put(index, text)?.let { cachedChars -= it.length }
            cachedChars += text.length
            if (cachedChars <= MAX_CACHE_CHARS) return
            val iterator = cache.entries.iterator()
            // LRU 端在迭代器头部，逐个淘汰直到回到预算内
            while (cachedChars > MAX_CACHE_CHARS && iterator.hasNext()) {
                val eldest = iterator.next()
                if (eldest.key == index) continue
                iterator.remove()
                cachedChars -= eldest.value.length
            }
        }
    }

    companion object {
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f

        /**
         * 正文缓存上限（字符）。
         *
         * 取 100 万字符 ≈ 2MB（JVM 的 String 按 UTF-16 存，一个中文字符 2 字节），
         * 与架构文档 Q13 拍板的「LRU 2MB」对齐。按一章 3000 字算大约能兜住 330 章，
         * 远超一次窗口化检索的实际访问量。
         */
        private const val MAX_CACHE_CHARS = 1_000_000

        private val IMG_TAG = Regex("<img[^>]*>", RegexOption.IGNORE_CASE)

        /**
         * 为「当前正在阅读的书」建一个 provider。
         *
         * @return 没有在读的书、或目录为空时返回 null（UI 据此提示
         *   [R.string.ai_book_not_ready][io.legado.app.R.string.ai_book_not_ready]）
         */
        fun current(): CachedBookTextProvider? {
            val book = ReadBook.book ?: return null
            val chapters = runCatching {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }.getOrDefault(emptyList())
            if (chapters.isEmpty()) return null
            val index = ReadBook.durChapterIndex.coerceIn(0, chapters.lastIndex)
            return CachedBookTextProvider(book, chapters, index)
        }
    }
}
