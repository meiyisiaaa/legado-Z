package io.legado.app.ai.engine

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.ai.GenerateInput
import io.legado.app.ai.PageFetcher
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.html.HtmlChunker
import io.legado.app.ai.prompt.PromptBuilder

/**
 * 生成模式引擎：把「一句意图 + 一段网页」变成一份书源 JSON。
 *
 * 编排顺序：
 * `（可选）抓取 → HtmlChunker 压缩 → PromptBuilder 组装 → AiChatClient 请求
 *  → SourceJsonExtractor 抠 JSON → BookSourceValidator 校验 → RulePreviewBuilder 预览`
 * ，其中「抠 JSON 之后」的部分委托给 [OutcomeAssembler]，与调试模式共用同一套逻辑。
 *
 * 关于 [pageFetcher] 可空：
 * 生成模式**允许**用户只填一个地址让程序去抓，但这是可选能力 ——
 * 未注入抓取器时（例如用户在设置里关掉了联网抓取），只要用户粘贴了 HTML 就照常工作。
 * 「有 HTML 就绝不抓取」是硬约束：用户既然已经把内容给你了，再联网一次纯属多余且泄漏隐私。
 *
 * @param client 对话客户端，单测注入 `FakeAiChatClient`
 * @param pageFetcher 网页抓取器，可为 null；单测注入 `FakePageFetcher` 以断言调用次数
 */
class SourceGenEngine(
    private val client: AiChatClient,
    private val pageFetcher: PageFetcher? = null
) {

    /**
     * 生成书源。
     *
     * @throws AiError.NotConfigured / [AiError.InvalidConfig] 配置不可用
     * @throws AiError.FetchFailed 需要抓取但抓取失败
     * @throws AiError.EmptyResponse 模型返回空内容
     * @throws AiError.JsonNotFound / [AiError.JsonMalformed] 抠不出合法 JSON 且修复额度用尽
     */
    suspend fun generate(options: AiChatOptions, input: GenerateInput): AiSourceOutcome {
        OutcomeAssembler.requireUsableConfig(options)

        val html = resolveHtml(input)
        val limit = resolveHtmlLimit(options, input)
        val snippet = HtmlChunker.chunk(html, limit)

        val messages = PromptBuilder.buildGenerate(input, snippet)
        val promptTokenEstimate = HtmlChunker.estimateTokens(messages.map { it.content })

        val response = client.chat(options, messages)
        val reply = response.firstContent()
        if (reply.isBlank()) throw AiError.EmptyResponse()

        return OutcomeAssembler.assemble(
            client = client,
            options = options,
            firstReply = reply,
            advice = "",
            firstUsage = response.usage,
            promptTokenEstimate = promptTokenEstimate
        )
    }

    /**
     * 决定送进 prompt 的 HTML 来源。
     *
     * 优先级：用户粘贴的 HTML > 抓取用户填写的地址 > 空串（纯靠意图生成骨架）。
     */
    private suspend fun resolveHtml(input: GenerateInput): String {
        if (input.hasHtml()) return input.html
        val fetcher = pageFetcher ?: return ""
        if (!input.hasUrl()) return ""
        return fetcher.fetch(input.url)
    }

    /** 两个上限取小：全局配置是硬顶，单次输入只能更保守。 */
    private fun resolveHtmlLimit(options: AiChatOptions, input: GenerateInput): Int =
        minOf(options.htmlMaxChars, input.maxHtmlChars)
}
