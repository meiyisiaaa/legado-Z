package io.legado.app.ai.batch

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.AiTestFixtures
import io.legado.app.ai.FakeAiChatClient
import io.legado.app.ai.engine.SourceGenEngine
import io.legado.app.ai.assertThrowsOf
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.client.ChatResponse
import io.legado.app.ai.client.Choice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BatchSourceGenerator] 单测。
 *
 * 重点守住三条不容商量的性质（见 [BatchSourceGenerator] 类注释）：
 * 1. **严格串行**：整批共用同一份配置，调用次数 == url 数；
 * 2. **失败隔离**：单条抛错只染色那一条，编排继续往下跑；
 * 3. **取消后状态收敛**：结束后没有任何条目停留在 RUNNING，且 [CancellationException] 原样重抛（§8）。
 *
 * 用 [FakeAiChatClient] 之外，本文件自带两个受控 Fake：
 * - [FailingIndexClient]：在指定第 N 次调用抛错，验证「失败隔离」；
 * - [CancellingClient]：在指定第 N 次调用从外部取消 job 并挂起，验证「取消收敛 + 重抛」。
 */
class BatchSourceGeneratorTest {

    private val validJson = AiTestFixtures.VALID_SOURCE_JSON

    @Test
    fun `正常串行全部成功且调用次数等于url数`() = runBlocking {
        val urls = listOf("https://a.com", "https://b.com", "https://c.com")
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val generator = BatchSourceGenerator(SourceGenEngine(client))

        val result = generator.run(AiTestFixtures.options(), urls) {}

        assertEquals("每条 url 应恰好触发一次生成", urls.size, client.callCount)
        assertEquals(urls.size, result.successCount)
        assertEquals(0, result.failedCount)
        assertEquals(0, result.cancelledCount)
        assertEquals(0, result.runningCount)
        assertEquals(urls.size, result.importableJsons().size)
    }

    @Test
    fun `空输入抛BatchInputEmpty`() = runBlocking {
        val generator = BatchSourceGenerator(SourceGenEngine(FakeAiChatClient.replying("x")))
        assertThrowsOf<AiError.BatchInputEmpty> {
            generator.run(AiTestFixtures.options(), emptyList())
        }
        Unit
    }

    @Test
    fun `中间条失败隔离后续仍成功`() = runBlocking {
        val urls = listOf("https://a.com", "https://b.com", "https://c.com")
        val client = FailingIndexClient(failIndex = 1, json = validJson)
        val generator = BatchSourceGenerator(SourceGenEngine(client))

        val result = generator.run(AiTestFixtures.options(), urls) {}

        assertEquals("失败不重试，仍继续跑完整批", urls.size, client.callCount)
        assertEquals(BatchTaskState.SUCCESS, result.tasks[0].state)
        assertEquals(BatchTaskState.FAILED, result.tasks[1].state)
        assertEquals(BatchTaskState.SUCCESS, result.tasks[2].state)
        // 失败的那条被染色：有文案资源 + 具体原因
        assertTrue(result.tasks[1].errorMessageRes != 0)
        assertTrue(result.tasks[1].errorDetail.isNotEmpty())
        // 前序与后续成功保留，失败不回滚整批
        assertEquals(2, result.successCount)
        assertEquals(1, result.failedCount)
        assertEquals(0, result.runningCount)
        assertEquals(2, result.importableJsons().size)
    }

    @Test
    fun `取消后状态收敛：无RUNNING残留且前序SUCCESS保留`() = runBlocking {
        val urls = listOf("https://a.com", "https://b.com", "https://c.com")
        val snapshots = mutableListOf<BatchProgress>()
        lateinit var job: Job
        val client = CancellingClient(cancelOnCall = 1, json = validJson) {
            // 第二次调用（第二条任务开始生成）时取消整个编排
            job.cancel()
        }
        val generator = BatchSourceGenerator(SourceGenEngine(client))

        job = launch {
            generator.run(AiTestFixtures.options(), urls) { snapshots.add(it) }
        }
        job.join()

        val last = snapshots.last()
        // 不变式：结束时不许有任何条目停留在 RUNNING
        assertEquals(0, last.runningCount)
        // 前序成功保留、后续连同当前条一起标成已取消
        assertEquals(BatchTaskState.SUCCESS, last.tasks[0].state)
        assertEquals(BatchTaskState.CANCELLED, last.tasks[1].state)
        assertEquals(BatchTaskState.CANCELLED, last.tasks[2].state)
        assertEquals(1, last.successCount)
        assertEquals(2, last.cancelledCount)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `取消时CancellationException原样重抛`() = runBlocking {
        val urls = listOf("https://a.com", "https://b.com")
        lateinit var deferred: Deferred<BatchProgress>
        val client = CancellingClient(cancelOnCall = 0, json = validJson) {
            deferred.cancel()
        }
        val generator = BatchSourceGenerator(SourceGenEngine(client))

        deferred = async {
            generator.run(AiTestFixtures.options(), urls) {}
        }
        val thrown = runCatching { deferred.await() }.exceptionOrNull()

        assertTrue(
            "被取消时必须原样重抛 CancellationException，而非包装成 AiError（§8 硬约束）",
            thrown is CancellationException
        )
    }

    @Test
    fun `进度回调每次都是完整快照且首帧全PENDING`() = runBlocking {
        val urls = listOf("https://a.com", "https://b.com", "https://c.com")
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val generator = BatchSourceGenerator(SourceGenEngine(client))

        val snapshots = mutableListOf<BatchProgress>()
        val finalProgress = generator.run(AiTestFixtures.options(), urls) { snapshots.add(it) }

        // 1 帧初始（全 PENDING）+ 每任务 2 帧（进入 RUNNING、完成）
        assertEquals(1 + 2 * urls.size, snapshots.size)
        // 每帧任务数恒定 == url 数，杜绝状态漂移
        assertTrue(snapshots.all { it.tasks.size == urls.size })
        assertTrue("首帧应为全 PENDING", snapshots.first().tasks.all { it.state == BatchTaskState.PENDING })
        assertTrue("末帧应为全 SUCCESS", snapshots.last().tasks.all { it.state == BatchTaskState.SUCCESS })
        // 返回值等于最后发出的快照
        assertEquals(finalProgress, snapshots.last())
    }

    /* ---------------------- 受控 Fake 客户端 ---------------------- */

    /**
     * 在 [failIndex]（0 起始）那一次调用抛错，其余正常吐 JSON。
     * 用于验证「失败隔离」：单条异常只染色那一条。
     */
    private class FailingIndexClient(
        private val failIndex: Int,
        private val json: String
    ) : AiChatClient {

        var callCount: Int = 0
            private set

        override suspend fun chat(
            options: AiChatOptions,
            messages: List<ChatMessage>
        ): ChatResponse {
            val current = callCount++
            if (current == failIndex) throw RuntimeException("模拟生成失败-$current")
            return ok(json, options)
        }
    }

    /**
     * 在 [cancelOnCall]（0 起始）那一次调用时，先触发外部取消 [onCancel]，
     * 再挂起等待取消信号到达，从而把 [CancellationException] 抛回编排器。
     */
    private class CancellingClient(
        private val cancelOnCall: Int,
        private val json: String,
        private val onCancel: () -> Unit
    ) : AiChatClient {

        override suspend fun chat(
            options: AiChatOptions,
            messages: List<ChatMessage>
        ): ChatResponse {
            if (callCount++ == cancelOnCall) {
                onCancel()
                // 挂起等待取消信号：一旦 job 被取消，delay 会立即抛 CancellationException
                delay(Long.MAX_VALUE)
            }
            return ok(json, options)
        }

        private var callCount = 0
    }

    private companion object {
        /** 合成一条标准成功回复。 */
        fun ok(json: String, options: AiChatOptions): ChatResponse = ChatResponse(
            id = "fake",
            model = options.model,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage.assistant(AiTestFixtures.fenced(json)),
                    finishReason = "stop"
                )
            ),
            usage = null
        )
    }
}
