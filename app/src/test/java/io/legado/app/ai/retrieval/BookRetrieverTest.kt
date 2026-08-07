package io.legado.app.ai.retrieval

import io.legado.app.ai.assertThrowsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * [BookRetriever] 的行为回归。
 *
 * 这里守的是本期最重要的三条线：
 * 1. **零命中就是零命中** —— 它是「不发网络请求」的判定依据，判错等于隐私红线破防；
 * 2. **绝不读未缓存章节** —— 判错等于偷偷触发下载；
 * 3. **时间预算 / 取消能真正生效** —— 判错等于长篇书上 UI 卡死。
 */
class BookRetrieverTest {

    /** 每章一句话，且只有第 3 章（下标 2）含关键词。 */
    private fun singleHitProvider(currentIndex: Int = 0) = FakeChapterTextProvider.cached(
        currentIndex,
        "开篇。少年从山村出发。",
        "他在客栈里遇到一个说书人。",
        "小李飞刀，例不虚发。江湖人尽皆知。",
        "第二天清晨，风雪未停。",
        "他决定继续北上。"
    )

    /** 五章内容完全相同，用来隔离出「位置衰减」这一个变量。 */
    private fun uniformProvider(currentIndex: Int) = FakeChapterTextProvider.cached(
        currentIndex,
        "小李飞刀例不虚发", "小李飞刀例不虚发", "小李飞刀例不虚发",
        "小李飞刀例不虚发", "小李飞刀例不虚发"
    )

    @Test
    fun `命中关键词时召回对应章节的片段`() = runBlocking {
        val result = BookRetriever(singleHitProvider()).retrieve(
            RetrievalRequest(query = "小李飞刀")
        )

        assertFalse("应有命中", result.isEmpty)
        assertEquals(1, result.fragments.size)
        assertEquals(2, result.fragments[0].fragment.chapterIndex)
        assertTrue(result.fragments[0].score > 0.0)
    }

    @Test
    fun `毫不相关的问题返回零命中`() = runBlocking {
        val result = BookRetriever(singleHitProvider()).retrieve(
            RetrievalRequest(query = "量子计算机")
        )

        assertTrue("不应召回任何片段", result.isEmpty)
        assertEquals(0, result.totalChars)
    }

    @Test
    fun `空白问题不做任何扫描直接返回空`() = runBlocking {
        val provider = singleHitProvider()
        val result = BookRetriever(provider).retrieve(RetrievalRequest(query = "   "))

        assertTrue(result.isEmpty)
        assertEquals(0, result.stats.scannedChapters)
        assertTrue("空问题不应读取任何章节", provider.readIndexes.isEmpty())
    }

    @Test
    fun `无章节时安全返回空`() = runBlocking {
        val provider = FakeChapterTextProvider(emptyList())
        val result = BookRetriever(provider).retrieve(RetrievalRequest(query = "小李飞刀"))

        assertTrue(result.isEmpty)
        assertEquals(0, result.stats.totalChapters)
    }

    @Test
    fun `未缓存的章节被跳过且绝不读取`() = runBlocking {
        val provider = FakeChapterTextProvider(
            chapters = listOf(
                FakeChapterTextProvider.Chapter("第一章", "小李飞刀例不虚发"),
                FakeChapterTextProvider.Chapter("第二章", null),
                FakeChapterTextProvider.Chapter("第三章", "小李飞刀例不虚发")
            ),
            currentIndex = 0
        )
        val result = BookRetriever(provider).retrieve(RetrievalRequest(query = "飞刀"))

        assertEquals("未缓存章不得触发读取", listOf(0, 2), provider.readIndexes)
        assertEquals(3, result.stats.scannedChapters)
        assertEquals(2, result.stats.cachedChapters)
    }

    @Test
    fun `按由近及远交替顺序扫描`() = runBlocking {
        val provider = uniformProvider(currentIndex = 2)
        BookRetriever(provider).retrieve(RetrievalRequest(query = "飞刀", topK = 10))

        assertEquals(listOf(2, 1, 3, 0, 4), provider.readIndexes)
    }

    @Test
    fun `位置衰减让靠近当前章的片段排在前面`() = runBlocking {
        val result = BookRetriever(uniformProvider(currentIndex = 2)).retrieve(
            RetrievalRequest(query = "小李飞刀", topK = 5, maxTotalChars = 10000)
        )

        assertEquals(
            listOf(2, 1, 3, 0, 4),
            result.fragments.map { it.fragment.chapterIndex }
        )
    }

    @Test
    fun `windowRadius限制扫描范围`() = runBlocking {
        val provider = uniformProvider(currentIndex = 2)
        val result = BookRetriever(provider).retrieve(
            RetrievalRequest(query = "飞刀", topK = 10, windowRadius = 1)
        )

        assertEquals(listOf(2, 1, 3), provider.readIndexes)
        assertFalse("窗口检索不应标记为全书", result.stats.usedFallback)
    }

    @Test
    fun `windowRadius非正数表示全书检索`() = runBlocking {
        val provider = uniformProvider(currentIndex = 2)
        val result = BookRetriever(provider).retrieve(
            RetrievalRequest(query = "飞刀", topK = 10, windowRadius = 0)
        )

        assertEquals(5, provider.readIndexes.size)
        assertTrue("全书检索应置位 usedFallback", result.stats.usedFallback)
    }

    @Test
    fun `topK限制召回条数`() = runBlocking {
        val result = BookRetriever(uniformProvider(currentIndex = 2)).retrieve(
            RetrievalRequest(query = "飞刀", topK = 2, maxTotalChars = 10000)
        )

        assertEquals(2, result.fragments.size)
        assertEquals(2, result.fragments[0].fragment.chapterIndex)
    }

    @Test
    fun `maxTotalChars限制片段字符总量`() = runBlocking {
        // 每章正文 8 字，切出的片段也是 8 字；预算 12 字只放得下一条
        val result = BookRetriever(uniformProvider(currentIndex = 2)).retrieve(
            RetrievalRequest(query = "飞刀", topK = 5, maxTotalChars = 12)
        )

        assertEquals(1, result.fragments.size)
        assertEquals(8, result.totalChars)
    }

    @Test
    fun `第一条片段超预算时截断到总预算`() = runBlocking {
        val result = BookRetriever(uniformProvider(currentIndex = 0)).retrieve(
            RetrievalRequest(query = "飞刀", topK = 5, maxTotalChars = 1)
        )

        assertEquals("宁可给一条也不给零条", 1, result.fragments.size)
        assertEquals(1, result.totalChars)
    }

    @Test
    fun `超出时间预算时提前收手并置位标记`() = runBlocking {
        // 时钟脚本：起始 0 → 前两章各 0 → 第三章跳到 5000 触发超时 → 收尾读数
        val ticks = ArrayDeque(listOf(0L, 0L, 0L, 5000L, 5000L))
        val provider = uniformProvider(currentIndex = 0)
        val retriever = BookRetriever(provider) { ticks.removeFirstOrNull() ?: 5000L }

        val result = retriever.retrieve(
            RetrievalRequest(query = "飞刀", topK = 5, timeBudgetMs = 2000L)
        )

        assertTrue("应置位超时标记", result.stats.budgetExceeded)
        assertEquals(2, result.stats.scannedChapters)
        assertEquals(listOf(0, 1), provider.readIndexes)
        assertEquals(5000L, result.stats.elapsedMs)
    }

    @Test
    fun `未超时时不置位超时标记`() = runBlocking {
        val result = BookRetriever(uniformProvider(currentIndex = 0)).retrieve(
            RetrievalRequest(query = "飞刀", topK = 5)
        )

        assertFalse(result.stats.budgetExceeded)
        assertEquals(5, result.stats.scannedChapters)
        assertEquals(5, result.stats.totalChapters)
    }

    @Test
    fun `扫描过程中被取消时原样抛出CancellationException`() {
        val jobRef = AtomicReference<Job>()
        val bodies = List(50) { "小李飞刀例不虚发" }
        val provider = object : ChapterTextProvider {
            override fun chapterCount(): Int = bodies.size
            override fun currentIndex(): Int = 0
            override fun titleAt(index: Int): String = "第${index + 1}章"
            override fun isCached(index: Int): Boolean = true
            override fun textAt(index: Int): String {
                // 读完第一章就取消，下一轮循环开头的 ensureActive() 必须立即抛出
                jobRef.get()?.cancel()
                return bodies[index]
            }
        }
        val retriever = BookRetriever(provider)

        assertThrowsOf<CancellationException> {
            runBlocking {
                val deferred = async(Dispatchers.Unconfined, CoroutineStart.LAZY) {
                    retriever.retrieve(
                        RetrievalRequest(query = "飞刀", timeBudgetMs = 60_000L)
                    )
                }
                jobRef.set(deferred)
                deferred.start()
                deferred.await()
            }
        }
    }

    @Test
    fun `统计信息如实反映扫描规模`() = runBlocking {
        val provider = FakeChapterTextProvider(
            chapters = listOf(
                FakeChapterTextProvider.Chapter("第一章", "小李飞刀例不虚发"),
                FakeChapterTextProvider.Chapter("第二章", null),
                FakeChapterTextProvider.Chapter("第三章", "   "),
                FakeChapterTextProvider.Chapter("第四章", "小李飞刀例不虚发")
            ),
            currentIndex = 0
        )
        val stats = BookRetriever(provider)
            .retrieve(RetrievalRequest(query = "飞刀")).stats

        assertEquals(4, stats.scannedChapters)
        assertEquals("空白正文不计入已缓存", 2, stats.cachedChapters)
        assertEquals(4, stats.totalChapters)
    }

    @Test
    fun `标题命中会放大同章片段的得分`() = runBlocking {
        val withTitleHit = FakeChapterTextProvider(
            chapters = listOf(
                FakeChapterTextProvider.Chapter("第一章 小李飞刀", "小李飞刀例不虚发")
            ),
            currentIndex = 0
        )
        val withoutTitleHit = FakeChapterTextProvider(
            chapters = listOf(
                FakeChapterTextProvider.Chapter("第一章 归途", "小李飞刀例不虚发")
            ),
            currentIndex = 0
        )
        val request = RetrievalRequest(query = "小李飞刀")

        val hit = BookRetriever(withTitleHit).retrieve(request).fragments[0].score
        val miss = BookRetriever(withoutTitleHit).retrieve(request).fragments[0].score

        assertTrue("标题命中应得到更高分：$hit vs $miss", hit > miss)
    }

    @Test
    fun `Q13_千章大书窗口检索在2秒内完成且不卡死`() = runBlocking {
        // 构造 1000 章、每章约 2000 字的内存假书
        val chapterText = "小李飞刀例不虚发 ".repeat(200) // ~2000 字
        val chapters = List(1000) { i ->
            FakeChapterTextProvider.Chapter("第${i + 1}章", chapterText)
        }
        val provider = FakeChapterTextProvider(chapters, currentIndex = 500)

        val start = System.nanoTime()
        val result = BookRetriever(provider).retrieve(RetrievalRequest(query = "飞刀"))
        val wallMs = (System.nanoTime() - start) / 1_000_000

        // Q13 硬指标：1000 章窗口检索真实耗时 < 2s（默认 windowRadius=200，实际扫 401 章）。
        //
        // 这条墙钟断言与 RetrievalRequest.DEFAULT_TIME_BUDGET_MS(=2000) 是同一个数：
        // 检索器自己就在 2s 处软超时收手，所以「超过 2s」等价于「软预算被触发」，
        // 断言失败 == 千章检索没跑进预算，正是 Q13 要守的那条线。
        // 本机实测热路径约 50~250ms（含 JIT 冷启动），留有约 8x 余量；
        // 若未来 CI 机器慢到抖动，改断言阈值前先看 budgetExceeded 是否已置位。
        assertFalse("千章窗口检索不应触发时间预算截断", result.stats.budgetExceeded)
        assertTrue("千章窗口检索应 < 2s，实际 ${wallMs}ms", wallMs < 2000)
        assertEquals(1000, result.stats.totalChapters)
        // 窗口模式不应触发全书兜底
        assertFalse(result.stats.usedFallback)
        // 防「跑得快是因为啥也没干」：召回必须真的有内容
        assertFalse("千章书检索不应零命中", result.isEmpty)
    }

    @Test
    fun `Q13_千章大书全书模式兜底能跑完不OOM`() = runBlocking {
        // 全书模式（windowRadius = 0）：必须真正扫完千章并置位 usedFallback，验证不 OOM / 不卡死
        val chapterText = "小李飞刀例不虚发 ".repeat(200) // ~2000 字
        val chapters = List(1000) { i ->
            FakeChapterTextProvider.Chapter("第${i + 1}章", chapterText)
        }
        val provider = FakeChapterTextProvider(chapters, currentIndex = 500)

        val result = BookRetriever(provider)
            .retrieve(RetrievalRequest(query = "飞刀", windowRadius = 0))

        assertTrue("全书模式应置位 usedFallback", result.stats.usedFallback)
        assertEquals(1000, result.stats.totalChapters)
        // scannedChapters == 1000 隐含依赖「全书扫描没被默认 2s 软预算截断」。
        // 先断言 budgetExceeded，否则机器一慢就只会看到 "expected 1000 but was 743" 这种谜之报错。
        // 全书 1000 章实测约 170~260ms，余量充足；真要抖动，加 timeBudgetMs = 60_000L 即可解耦。
        assertFalse("全书扫描不应被时间预算截断", result.stats.budgetExceeded)
        assertEquals(1000, result.stats.scannedChapters)
        assertFalse("全书模式检索不应零命中", result.isEmpty)
    }
}
