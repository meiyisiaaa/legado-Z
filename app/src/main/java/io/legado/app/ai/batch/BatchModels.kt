package io.legado.app.ai.batch

import io.legado.app.ai.AiSourceOutcome

/**
 * 批量生成中单条任务的状态。
 *
 * 状态机（只允许这些迁移）：
 * ```
 * PENDING ──► RUNNING ──► SUCCESS
 *    │           │
 *    │           └──────► FAILED
 *    └──────────────────► CANCELLED ◄── RUNNING（用户取消）
 * ```
 * 不变式：一次编排结束后，**不允许有任何条目停留在 RUNNING**。
 */
enum class BatchTaskState {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;

    /** 已收敛（不会再变化）的状态。 */
    fun isTerminal(): Boolean = this == SUCCESS || this == FAILED || this == CANCELLED
}

/**
 * 批量生成的一条任务。
 *
 * 失败原因拆成 [errorMessageRes] + [errorDetail] 两截：
 * 前者是可本地化的通用文案，后者是模型/网络给的具体信息，UI 拼接展示即可，
 * 内核这一层依然不需要 Context。
 *
 * @param index 在输入列表中的原始次序，用于稳定排序（0 起始）
 * @param outcome 成功时的产出，其余状态为 null
 * @param errorMessageRes 失败时的文案资源 id，0 表示无
 */
data class BatchTask(
    val index: Int,
    val url: String,
    val state: BatchTaskState = BatchTaskState.PENDING,
    val outcome: AiSourceOutcome? = null,
    val errorMessageRes: Int = 0,
    val errorDetail: String = ""
) {

    /** 成功且校验无 ERROR，才算「可导入」。 */
    fun importableJson(): String? {
        val result = outcome ?: return null
        if (!result.canImport()) return null
        return result.sourceJson
    }
}

/**
 * 批量编排的一帧快照。
 *
 * 每完成一条就整体重发一份不可变快照，而不是发「第 i 条变了」的增量事件：
 * 批量最多 20 条，全量重算的开销可以忽略，换来的是 UI 侧永远不会出现
 * 「漏了一个事件导致进度条和列表对不上」这类难查的状态漂移。
 */
data class BatchProgress(
    val tasks: List<BatchTask> = emptyList()
) {

    val total: Int get() = tasks.size

    val successCount: Int get() = tasks.count { it.state == BatchTaskState.SUCCESS }

    val failedCount: Int get() = tasks.count { it.state == BatchTaskState.FAILED }

    val cancelledCount: Int get() = tasks.count { it.state == BatchTaskState.CANCELLED }

    /** 已收敛的条目数（成功 + 失败 + 已取消）。 */
    val completedCount: Int get() = tasks.count { it.state.isTerminal() }

    val runningCount: Int get() = tasks.count { it.state == BatchTaskState.RUNNING }

    fun isFinished(): Boolean = tasks.isNotEmpty() && tasks.all { it.state.isTerminal() }

    /** 所有可一键导入的书源 JSON，按原始次序排列。 */
    fun importableJsons(): List<String> = tasks.mapNotNull { it.importableJson() }

    companion object {
        val EMPTY = BatchProgress()
    }
}

/**
 * 解析后的批量输入。
 *
 * @param urls 去空白、去重、裁剪到上限之后的地址列表
 * @param ignoredCount 因超出上限而被丢弃的条数，> 0 时 UI 需提示（决策 A3）
 */
data class ParsedBatchInput(
    val urls: List<String> = emptyList(),
    val ignoredCount: Int = 0
) {

    fun isEmpty(): Boolean = urls.isEmpty()

    companion object {
        val EMPTY = ParsedBatchInput()
    }
}

/** 批量输入的解析工具。 */
object BatchInputParser {

    /**
     * 批量条数上限（决策 A3）。
     *
     * 定在 20 而不是「不限」：批量是严格串行的，一条按 10 秒算，20 条就已经 3 分多钟；
     * 再多既容易触发服务端限流，也超出了用户愿意盯着进度条等待的耐心。
     */
    const val DEFAULT_MAX_TASKS = 20

    /**
     * 把多行文本解析成地址列表。
     *
     * 依次做四件事：按行切分 → 去首尾空白并丢弃空行 → **保序**去重 → 裁剪到上限。
     * 去重放在裁剪之前：否则用户粘贴了一堆重复地址时，
     * 会出现「明明只有 3 个不同地址，却说超了 20 条上限」的荒谬提示。
     */
    fun parse(raw: String, maxTasks: Int = DEFAULT_MAX_TASKS): ParsedBatchInput {
        val limit = if (maxTasks < 1) 1 else maxTasks
        val distinct = LinkedHashSet<String>()
        raw.lineSequence().forEach { line ->
            val url = line.trim()
            if (url.isNotEmpty()) {
                distinct.add(url)
            }
        }
        if (distinct.isEmpty()) return ParsedBatchInput.EMPTY
        val all = distinct.toList()
        if (all.size <= limit) {
            return ParsedBatchInput(urls = all, ignoredCount = 0)
        }
        return ParsedBatchInput(urls = all.take(limit), ignoredCount = all.size - limit)
    }
}
