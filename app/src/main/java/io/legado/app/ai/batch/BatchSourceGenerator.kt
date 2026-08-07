package io.legado.app.ai.batch

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.GenerateInput
import io.legado.app.ai.engine.SourceGenEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 批量书源生成编排器。
 *
 * 三条不容商量的性质：
 *
 * 1. **严格串行**。并发度恒为 1，不提供任何调大的入口。批量生成天然会撞上
 *    服务端限流，并发只会把「20 条慢慢成功」变成「20 条一起 429」；
 *    而且串行才能让用户随时按停止并立刻见效。
 * 2. **失败隔离**。任何一条抛错都只染色那一条，编排继续往下走 ——
 *    批量的价值就在于「跑一晚上，明早捡能用的」，一条挂掉就整批回滚毫无意义。
 * 3. **取消后状态收敛**。用户取消时，正在跑的那条和后面所有待跑的都会被标成
 *    [BatchTaskState.CANCELLED]，保证「结束后没有任何条目停留在 RUNNING」。
 *
 * 本类不含任何 Android / 网络依赖，[SourceGenEngine] 由外部注入，
 * 因此单测可用 FakeAiChatClient 精确断言调用次数与状态迁移。
 */
class BatchSourceGenerator(private val engine: SourceGenEngine) {

    /**
     * 逐条生成。
     *
     * @param options 配置快照，整批共用同一份（中途改配置不影响本批）
     * @param urls 已由 [BatchInputParser] 解析过的地址列表
     * @param intent 整批共用的意图描述，通常来自 [io.legado.app.ai.prompt.IntentPromptTemplates]
     * @param onProgress 每次状态变化回调一份完整快照，UI 直接整体刷新
     * @return 收敛后的最终快照
     *
     * @throws AiError.BatchInputEmpty [urls] 为空
     * @throws CancellationException 被取消时原样抛出（§8 硬约束，绝不吞）
     */
    suspend fun run(
        options: AiChatOptions,
        urls: List<String>,
        intent: String = "",
        onProgress: (BatchProgress) -> Unit = {}
    ): BatchProgress {
        if (urls.isEmpty()) throw AiError.BatchInputEmpty()

        val tasks = MutableList(urls.size) { index ->
            BatchTask(index = index, url = urls[index])
        }
        onProgress(snapshot(tasks))

        for (index in tasks.indices) {
            // 进入下一条之前先看一眼有没有被取消：
            // 否则用户点了停止，还得等当前这条把网络请求跑完才会生效
            try {
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                cancelFrom(tasks, index)
                onProgress(snapshot(tasks))
                throw e
            }

            tasks[index] = tasks[index].copy(state = BatchTaskState.RUNNING)
            onProgress(snapshot(tasks))

            tasks[index] = try {
                val outcome = engine.generate(
                    options,
                    GenerateInput(url = tasks[index].url, intent = intent)
                )
                tasks[index].copy(state = BatchTaskState.SUCCESS, outcome = outcome)
            } catch (e: CancellationException) {
                // 取消不是失败：当前这条连同后面所有待跑的一起标成已取消再重抛
                cancelFrom(tasks, index)
                onProgress(snapshot(tasks))
                throw e
            } catch (e: Throwable) {
                val error = AiError.wrap(e)
                tasks[index].copy(
                    state = BatchTaskState.FAILED,
                    errorMessageRes = error.messageRes,
                    errorDetail = error.detail.orEmpty().redact(options)
                )
            }
            onProgress(snapshot(tasks))
        }

        return snapshot(tasks)
    }

    /**
     * 把从 [from] 开始（含）的所有未收敛条目标记为已取消。
     *
     * 前面已经跑完的成功/失败结果**保留** —— 用户按停止是「别再花钱了」，
     * 不是「把已经花掉的钱买到的东西也扔了」。
     */
    private fun cancelFrom(tasks: MutableList<BatchTask>, from: Int) {
        for (i in from until tasks.size) {
            if (!tasks[i].state.isTerminal()) {
                tasks[i] = tasks[i].copy(state = BatchTaskState.CANCELLED)
            }
        }
    }

    private fun snapshot(tasks: List<BatchTask>): BatchProgress = BatchProgress(tasks.toList())

    private fun String.redact(options: AiChatOptions): String {
        val key = options.apiKey.trim()
        return if (key.isBlank()) this else replace(key, options.maskedKey())
    }
}
