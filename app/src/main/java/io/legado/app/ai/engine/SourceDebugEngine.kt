package io.legado.app.ai.engine

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.ai.DebugInput
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.html.HtmlChunker
import io.legado.app.ai.prompt.PromptBuilder
import io.legado.app.ai.source.BookSourceValidator
import io.legado.app.ai.source.RulePreviewBuilder
import io.legado.app.ai.source.SourceJsonExtractor
import io.legado.app.model.DebugLogSanitizer
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 调试模式引擎：拿现有书源 JSON + 样例 HTML + 故障症状，产出诊断建议与修正后的 JSON。
 *
 * **隐私硬约束（架构文档 Q5）：构造函数里没有、也不允许出现 `PageFetcher`。**
 * 理由：调试场景的输入是「用户已经有的书源」，如果引擎能联网，
 * 那它就能拿着书源里的 `searchUrl` 去访问用户正在用的站点 —— 这既是隐私泄漏，
 * 也可能在用户毫不知情时触发目标站点的风控。把抓取能力从签名上删掉，
 * 是唯一无法被后续改动悄悄绕过的防线。`SourceDebugEngineTest` 会用反射断言这一点。
 *
 * @param client 对话客户端，单测注入 `FakeAiChatClient`
 */
class SourceDebugEngine(
    private val client: AiChatClient
) {

    /**
     * 诊断书源。
     *
     * @throws AiError.NotConfigured / [AiError.InvalidConfig] 配置不可用
     * @throws AiError.EmptyResponse 模型返回空内容
     * @throws AiError.JsonNotFound / [AiError.JsonMalformed] 抠不出合法 JSON 且修复额度用尽
     */
    suspend fun debug(options: AiChatOptions, input: DebugInput): AiSourceOutcome {
        OutcomeAssembler.requireUsableConfig(options)
        if (input.sourceJson.length > MAX_SOURCE_JSON_CHARS) {
            throw AiError.InvalidConfig("书源 JSON 超过调试输入上限")
        }

        val sanitizedInput = input.copy(
            sourceJson = DebugLogSanitizer.sanitize(input.sourceJson),
            symptom = DebugLogSanitizer.sanitize(input.symptom.take(MAX_SYMPTOM_CHARS)),
            sampleHtml = DebugLogSanitizer.sanitize(input.sampleHtml.take(MAX_HTML_INPUT_CHARS)),
            debugLogs = DebugLogSanitizer.sanitize(input.debugLogs.take(MAX_LOG_INPUT_CHARS))
        )

        val limit = minOf(options.htmlMaxChars, sanitizedInput.maxHtmlChars)
        val snippet = HtmlChunker.chunk(sanitizedInput.sampleHtml, limit)

        val messages = PromptBuilder.buildDebug(sanitizedInput, snippet)
        val promptTokenEstimate = HtmlChunker.estimateTokens(messages.map { it.content })

        val response = client.chat(options, messages)
        val reply = response.firstContent()
        if (reply.isBlank()) throw AiError.EmptyResponse()

        val outcome = OutcomeAssembler.assemble(
            client = client,
            // Debugging is one diagnosis request. A malformed reply is shown for review; it is
            // never sent back to the provider for an automatic repair round.
            options = options.copy(autoRepair = false),
            firstReply = reply,
            advice = extractAdvice(reply),
            firstUsage = response.usage,
            promptTokenEstimate = promptTokenEstimate
        )
        return restoreSensitiveSourceFields(input.sourceJson, outcome)
    }

    private fun restoreSensitiveSourceFields(
        originalSource: String,
        outcome: AiSourceOutcome
    ): AiSourceOutcome {
        val original = runCatching { JsonParser.parseString(originalSource).asJsonObject }.getOrNull()
            ?: return outcome
        val generated = runCatching { JsonParser.parseString(outcome.sourceJson).asJsonObject }.getOrNull()
            ?: return outcome
        val originalMainJs = original.get("mainJs")
        val hasOriginalScript = originalMainJs?.isJsonPrimitive == true &&
            originalMainJs.asJsonPrimitive.isString &&
            originalMainJs.asString.isNotBlank()
        val generatedMainJs = generated.get("mainJs")
        val hasGeneratedScript = generatedMainJs?.isJsonPrimitive == true &&
            generatedMainJs.asJsonPrimitive.isString &&
            generatedMainJs.asString.isNotBlank()
        if (hasOriginalScript && !hasGeneratedScript) {
            generated.add("mainJs", originalMainJs!!.deepCopy())
        }
        restoreSensitiveFields(original, generated)
        val json = SourceJsonExtractor.format(generated)
        return outcome.copy(
            sourceJson = json,
            preview = RulePreviewBuilder.build(generated),
            validation = BookSourceValidator.validate(generated)
        )
    }

    private fun restoreSensitiveFields(original: JsonElement, generated: JsonElement) {
        when {
            original.isJsonObject && generated.isJsonObject -> {
                original.asJsonObject.entrySet().forEach { (key, originalValue) ->
                    val target = generated.asJsonObject
                    val originalEntry = JsonObject().apply { add(key, originalValue) }.toString()
                    val wasRedacted = DebugLogSanitizer.sanitize(originalEntry) != originalEntry
                    val generatedValue = target.get(key)
                    val containsPlaceholder = generatedValue?.isJsonPrimitive == true &&
                        generatedValue.asJsonPrimitive.isString &&
                        generatedValue.asString.contains(REDACTION_MARKER)
                    if (wasRedacted && (generatedValue == null || generatedValue.isJsonNull || containsPlaceholder)) {
                        target.add(key, originalValue.deepCopy())
                    } else if (generatedValue != null && !generatedValue.isJsonNull) {
                        restoreSensitiveFields(originalValue, generatedValue)
                    }
                }
            }

            original.isJsonArray && generated.isJsonArray -> {
                val originalArray = original.asJsonArray
                val generatedArray = generated.asJsonArray
                repeat(minOf(originalArray.size(), generatedArray.size())) { index ->
                    restoreSensitiveFields(originalArray[index], generatedArray[index])
                }
            }
        }
    }

    /**
     * 取出 JSON 之前的那段中文诊断建议。
     *
     * 约定回复形如「建议正文 → ```json 代码块」，因此以首个代码围栏或首个 `{`
     * 作为切点；两者都没有时说明模型只给了文字建议，整段都算建议。
     */
    internal fun extractAdvice(reply: String): String {
        val fenceIndex = reply.indexOf(FENCE_MARKER)
        val braceIndex = reply.indexOf('{')
        val cut = listOf(fenceIndex, braceIndex).filter { it >= 0 }.minOrNull()
            ?: return reply.trim()
        return reply.take(cut).trim()
    }

    companion object {
        private const val FENCE_MARKER = "```"
        private const val MAX_SOURCE_JSON_CHARS = 60_000
        private const val MAX_SYMPTOM_CHARS = 4_000
        private const val MAX_HTML_INPUT_CHARS = 80_000
        private const val MAX_LOG_INPUT_CHARS = 8_000
        private const val REDACTION_MARKER = "[REDACTED]"
    }
}
