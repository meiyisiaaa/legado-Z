package io.legado.app.ai.engine

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiConfigValidator
import io.legado.app.ai.AiError
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.ai.ValidationResult
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.Usage
import io.legado.app.ai.prompt.PromptBuilder
import io.legado.app.ai.source.BookSourceValidator
import io.legado.app.ai.source.RulePreviewBuilder
import io.legado.app.ai.source.SourceJsonExtractor
import kotlinx.coroutines.CancellationException

/**
 * 「模型回复 → [AiSourceOutcome]」的共用装配流水线。
 *
 * 生成模式与调试模式在**拿到回复之后**的处理完全一致
 * （抠 JSON → 校验 → 建预览 → 可选自动修复），抽出来避免两个 Engine 各写一遍而行为漂移。
 * 声明为 `internal`，不对 UI 层暴露。
 */
internal object OutcomeAssembler {

    /** 修复轮强制把温度拉到 0：这一步要的是确定性，不是创造力。 */
    const val REPAIR_TEMPERATURE = 0f

    /** 自动修复最多只做一轮（架构文档 Q4），避免失控烧钱。 */
    const val MAX_REPAIR_ROUNDS = 1

    /**
     * 入口守卫：配置不全直接失败，不浪费一次网络往返。
     *
     * @throws AiError.NotConfigured 三要素（endpoint / apiKey / model）任一为空
     * @throws AiError.InvalidConfig 配置校验存在 ERROR
     */
    fun requireUsableConfig(options: AiChatOptions) {
        if (options.endpoint.isBlank() ||
            options.apiKey.isBlank() ||
            options.model.isBlank()
        ) {
            throw AiError.NotConfigured()
        }
        val result = AiConfigValidator.validate(options)
        if (result.hasError()) {
            throw AiError.InvalidConfig(result.firstErrorDetail())
        }
    }

    /**
     * 把一次模型回复装配成产出。
     *
     * 自动修复触发条件（二选一，且全程最多触发 [MAX_REPAIR_ROUNDS] 次）：
     * 1. 回复里抠不出合法 JSON；
     * 2. JSON 抠出来了但校验存在 ERROR 级问题。
     *
     * 注意：即便校验只剩 WARNING 也照常返回「草稿态」产出，
     * 由 [AiSourceOutcome.canImport] 决定能不能一键导入 —— 用户有权看到半成品。
     *
     * @param advice 诊断建议（生成模式传空串）；修复轮不会改写它，因为修复只动 JSON
     * @return 装配好的产出
     * @throws AiError 抠取/校验彻底失败且修复额度已用尽
     */
    suspend fun assemble(
        client: AiChatClient,
        options: AiChatOptions,
        firstReply: String,
        advice: String,
        firstUsage: Usage?,
        promptTokenEstimate: Int
    ): AiSourceOutcome {
        var reply = firstReply.redact(options)
        var usage = firstUsage
        var repairRounds = 0

        while (true) {
            val extracted = runCatching { SourceJsonExtractor.extract(reply) }
            val json = extracted.getOrNull()

            if (json == null) {
                val error = extracted.exceptionOrNull() ?: AiError.JsonNotFound(reply)
                if (error is CancellationException) throw error
                if (options.autoRepair && repairRounds < MAX_REPAIR_ROUNDS) {
                    repairRounds++
                    val repair = repairOnce(client, options, reply, ValidationResult.EMPTY)
                    reply = repair.reply.redact(options)
                    usage = mergeUsage(usage, repair.usage)
                    continue
                }
                throw AiError.wrap(error)
            }

            val validation = BookSourceValidator.validate(json)
            if (validation.hasError() &&
                options.autoRepair &&
                repairRounds < MAX_REPAIR_ROUNDS
            ) {
                repairRounds++
                val repair = repairOnce(client, options, reply, validation)
                reply = repair.reply.redact(options)
                usage = mergeUsage(usage, repair.usage)
                continue
            }

            return AiSourceOutcome(
                rawReply = reply,
                sourceJson = SourceJsonExtractor.format(json),
                advice = advice,
                preview = RulePreviewBuilder.build(json),
                validation = validation,
                usage = usage,
                promptTokenEstimate = promptTokenEstimate
            )
        }
    }

    /* ---------------- 内部实现 ---------------- */

    /** 一轮修复请求的结果。 */
    private data class RepairResult(val reply: String, val usage: Usage?)

    private suspend fun repairOnce(
        client: AiChatClient,
        options: AiChatOptions,
        brokenReply: String,
        validation: ValidationResult
    ): RepairResult {
        val messages = PromptBuilder.buildRepair(brokenReply, validation)
        val response = client.chat(options.copy(temperature = REPAIR_TEMPERATURE), messages)
        val content = response.firstContent()
        if (content.isBlank()) throw AiError.EmptyResponse()
        return RepairResult(content, response.usage)
    }

    /** 修复轮的 token 消耗必须累加进总量，否则用户看到的成本是假的。 */
    private fun mergeUsage(first: Usage?, second: Usage?): Usage? {
        if (first == null) return second
        if (second == null) return first
        return Usage(
            promptTokens = first.promptTokens + second.promptTokens,
            completionTokens = first.completionTokens + second.completionTokens,
            totalTokens = first.totalTokens + second.totalTokens
        )
    }

    private fun String.redact(options: AiChatOptions): String {
        val key = options.apiKey.trim()
        return if (key.isBlank()) this else replace(key, options.maskedKey())
    }
}
