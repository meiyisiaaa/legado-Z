package io.legado.app.ai

import io.legado.app.ai.client.Usage

/**
 * AI 书源助手跨层共享的数据模型。
 *
 * 本文件（以及整个 `io.legado.app.ai` 包，除 [AiConfig] 外）**禁止**引用任何
 * `android.*` / `androidx.*` / `io.legado.app.data.*` API，以保证内核逻辑可在
 * 纯 JVM 单元测试中运行。`messageRes` / `titleRes` 仅作为普通 Int 传递，
 * 由 UI 侧调用 `getString(res)` 解析。
 */

/**
 * 一次 AI 请求所需的全部配置快照。
 *
 * 不可变 data class，由 [AiConfig.snapshot] 产出后作为参数逐层下传；
 * 所有 Engine / Client **不得**持有 [AiConfig] 引用，以保证可测试性。
 */
data class AiChatOptions(
    /** 形如 `https://api.openai.com/v1`，已由 [AiConfigValidator.normalizeEndpoint] 规范化 */
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Float = DEFAULT_TEMPERATURE,
    /** 仅约束**输出**长度；输入片段长度由 [htmlMaxChars] 约束 */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val timeoutSec: Int = DEFAULT_TIMEOUT_SEC,
    /** 送入 LLM 的 HTML 片段字符上限 */
    val htmlMaxChars: Int = DEFAULT_HTML_MAX_CHARS,
    /** JSON 抠取/校验失败时是否自动发起一次修复请求（会额外消耗一次请求） */
    val autoRepair: Boolean = DEFAULT_AUTO_REPAIR
) {

    /**
     * 拼出最终的 chat completions 地址。
     *
     * 兼容用户三种常见写法：
     * - `https://host/v1`             → `https://host/v1/chat/completions`
     * - `https://host/v1/`            → `https://host/v1/chat/completions`
     * - `https://host/v1/chat/completions` → 原样返回
     */
    fun chatCompletionsUrl(): String {
        val base = endpoint.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        if (base.endsWith(CHAT_COMPLETIONS_PATH, ignoreCase = true)) {
            return base
        }
        return base + CHAT_COMPLETIONS_PATH
    }

    /**
     * 用于日志/错误详情的脱敏 key，形如 `sk-***...***abcd`。
     * 隐私红线：任何日志输出 apiKey 前必须经过此方法。
     */
    fun maskedKey(): String {
        val key = apiKey.trim()
        if (key.isEmpty()) return EMPTY_KEY_MASK
        if (key.length <= MASK_KEEP_TOTAL) return FULL_MASK
        return key.take(MASK_KEEP_HEAD) + FULL_MASK + "..." + FULL_MASK + key.takeLast(MASK_KEEP_TAIL)
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.2f
        const val DEFAULT_MAX_TOKENS = 4096
        const val DEFAULT_TIMEOUT_SEC = 90
        const val DEFAULT_HTML_MAX_CHARS = 12000

        /**
         * 自动修复默认**开启**（用户拍板，覆盖架构文档 Q4 的"默认关闭"建议）。
         * 配置页保留开关，文案需注明"会额外消耗一次请求"。
         */
        const val DEFAULT_AUTO_REPAIR = true

        const val MIN_TEMPERATURE = 0f
        const val MAX_TEMPERATURE = 2f
        const val MIN_MAX_TOKENS = 256
        const val MAX_MAX_TOKENS = 131072
        const val MIN_TIMEOUT_SEC = 5
        const val MAX_TIMEOUT_SEC = 600
        const val MIN_HTML_MAX_CHARS = 2000
        const val MAX_HTML_MAX_CHARS = 64000

        private const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        private const val EMPTY_KEY_MASK = "<empty>"
        private const val FULL_MASK = "***"
        private const val MASK_KEEP_HEAD = 3
        private const val MASK_KEEP_TAIL = 4
        private const val MASK_KEEP_TOTAL = 8
    }
}

/** 校验问题的严重级别。ERROR 阻断导入，WARNING 仅提示。 */
enum class IssueLevel {
    ERROR,
    WARNING
}

/**
 * 单条校验问题。
 *
 * @param field 出问题的字段路径，如 `bookSourceUrl`、`ruleSearch.bookList`
 * @param level 严重级别
 * @param messageRes 文案资源 id（由 UI 侧解析）
 * @param detail 补充说明，可直接展示给用户
 */
data class ValidationIssue(
    val field: String,
    val level: IssueLevel,
    val messageRes: Int,
    val detail: String = ""
)

/** 一次校验的完整结果。 */
data class ValidationResult(
    val issues: List<ValidationIssue> = emptyList()
) {

    fun hasError(): Boolean = issues.any { it.level == IssueLevel.ERROR }

    fun hasWarning(): Boolean = issues.any { it.level == IssueLevel.WARNING }

    fun errors(): List<ValidationIssue> = issues.filter { it.level == IssueLevel.ERROR }

    fun warnings(): List<ValidationIssue> = issues.filter { it.level == IssueLevel.WARNING }

    fun isEmpty(): Boolean = issues.isEmpty()

    /** 汇总首条 ERROR 的 detail，供 [AiError] 的 detail 使用 */
    fun firstErrorDetail(): String = errors().firstOrNull()?.let {
        if (it.detail.isBlank()) it.field else "${it.field}: ${it.detail}"
    } ?: ""

    companion object {
        val EMPTY = ValidationResult()
    }
}

/** 规则预览的一行：字段名 + 字段值。 */
data class RulePreviewEntry(
    val key: String,
    val value: String
)

/** 规则预览的一组：如"基础信息""搜索规则"。 */
data class RulePreviewGroup(
    val titleRes: Int,
    val entries: List<RulePreviewEntry>
)

/** 规则预览整体。 */
data class RulePreview(
    val groups: List<RulePreviewGroup> = emptyList()
) {

    fun isEmpty(): Boolean = groups.all { it.entries.isEmpty() }

    companion object {
        val EMPTY = RulePreview()
    }
}

/**
 * 一次生成/诊断的产出。
 *
 * 即使 [validation] 含 ERROR 也照常返回（"草稿态"预览），只是 [canImport] 为 false。
 */
data class AiSourceOutcome(
    /** LLM 的原始回复全文，用于兜底展示与「编辑 JSON」 */
    val rawReply: String = "",
    /** 抠取并格式化后的书源 JSON；抠不出时为空串 */
    val sourceJson: String = "",
    /** 诊断建议（生成模式下为空串） */
    val advice: String = "",
    val preview: RulePreview = RulePreview.EMPTY,
    val validation: ValidationResult = ValidationResult.EMPTY,
    /** 服务端回传的 token 用量，可能为 null（部分兼容端点不返回） */
    val usage: Usage? = null,
    /** 本地估算的 prompt token 数，用于 UI 预告成本 */
    val promptTokenEstimate: Int = 0
) {

    /** 只有"有 JSON 且无 ERROR"才允许一键导入。 */
    fun canImport(): Boolean = sourceJson.isNotBlank() && !validation.hasError()
}

/**
 * 生成模式输入。
 *
 * [url] 与 [html] 二选一：优先使用 [html]（用户直接粘贴，无隐私风险且不耗流量）。
 */
data class GenerateInput(
    val url: String = "",
    val html: String = "",
    val intent: String = "",
    val maxHtmlChars: Int = AiChatOptions.DEFAULT_HTML_MAX_CHARS
) {

    fun hasHtml(): Boolean = html.isNotBlank()

    fun hasUrl(): Boolean = url.isNotBlank()
}

/** 调试模式输入。注意：**不含任何待抓取的 URL**（Q5 隐私红线）。 */
data class DebugInput(
    val sourceJson: String = "",
    val sampleHtml: String = "",
    val symptom: String = "",
    val maxHtmlChars: Int = AiChatOptions.DEFAULT_HTML_MAX_CHARS
) {

    fun hasSampleHtml(): Boolean = sampleHtml.isNotBlank()
}
