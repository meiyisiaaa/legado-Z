package io.legado.app.ai

import io.legado.app.R

/**
 * AI 配置的纯函数校验器（零 Android 运行时依赖，完全可 JVM 单测）。
 *
 * endpoint 拼接是经典 bug 源（`/v1` vs `/v1/` vs 已含 `/chat/completions`），
 * 故规范化逻辑集中在 [normalizeEndpoint]，由 [AiChatOptions.chatCompletionsUrl] 负责最终拼接。
 */
object AiConfigValidator {

    private const val CHAT_COMPLETIONS_PATH = "/chat/completions"
    private const val SCHEME_HTTP = "http://"
    private const val SCHEME_HTTPS = "https://"

    /**
     * 规范化用户填写的 endpoint，统一收敛为**不带尾斜杠、不带 `/chat/completions` 后缀**的基址。
     *
     * ```
     * "https://api.openai.com/v1"                      -> "https://api.openai.com/v1"
     * " https://api.openai.com/v1/ "                   -> "https://api.openai.com/v1"
     * "https://api.openai.com/v1/chat/completions"     -> "https://api.openai.com/v1"
     * "https://api.openai.com/v1/chat/completions/"    -> "https://api.openai.com/v1"
     * ""                                               -> ""
     * ```
     */
    fun normalizeEndpoint(raw: String): String {
        var endpoint = raw.trim()
        if (endpoint.isEmpty()) return ""
        endpoint = endpoint.trimEnd('/')
        if (endpoint.endsWith(CHAT_COMPLETIONS_PATH, ignoreCase = true)) {
            endpoint = endpoint.dropLast(CHAT_COMPLETIONS_PATH.length).trimEnd('/')
        }
        return endpoint
    }

    /**
     * 校验一份配置。返回的 [ValidationResult] 若 [ValidationResult.hasError] 为 true，
     * 则调用方（[AiConfig.save]）不得写入偏好。
     *
     * 入参 endpoint 允许尚未规范化，内部会先跑一次 [normalizeEndpoint] 再判断协议头。
     */
    fun validate(options: AiChatOptions): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val endpoint = normalizeEndpoint(options.endpoint)

        if (endpoint.isEmpty()) {
            issues.add(
                ValidationIssue(
                    field = FIELD_ENDPOINT,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_endpoint_empty
                )
            )
        } else if (!endpoint.startsWith(SCHEME_HTTP, ignoreCase = true) &&
            !endpoint.startsWith(SCHEME_HTTPS, ignoreCase = true)
        ) {
            issues.add(
                ValidationIssue(
                    field = FIELD_ENDPOINT,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_endpoint_scheme,
                    detail = endpoint
                )
            )
        }

        if (options.apiKey.isBlank()) {
            issues.add(
                ValidationIssue(
                    field = FIELD_API_KEY,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_api_key_empty
                )
            )
        }

        if (options.model.isBlank()) {
            issues.add(
                ValidationIssue(
                    field = FIELD_MODEL,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_model_empty
                )
            )
        }

        if (options.temperature < AiChatOptions.MIN_TEMPERATURE ||
            options.temperature > AiChatOptions.MAX_TEMPERATURE
        ) {
            issues.add(
                ValidationIssue(
                    field = FIELD_TEMPERATURE,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_temperature_range,
                    detail = options.temperature.toString()
                )
            )
        }

        if (options.maxTokens < AiChatOptions.MIN_MAX_TOKENS ||
            options.maxTokens > AiChatOptions.MAX_MAX_TOKENS
        ) {
            issues.add(
                ValidationIssue(
                    field = FIELD_MAX_TOKENS,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_max_tokens_range,
                    detail = options.maxTokens.toString()
                )
            )
        }

        if (options.timeoutSec < AiChatOptions.MIN_TIMEOUT_SEC ||
            options.timeoutSec > AiChatOptions.MAX_TIMEOUT_SEC
        ) {
            issues.add(
                ValidationIssue(
                    field = FIELD_TIMEOUT_SEC,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_timeout_range,
                    detail = options.timeoutSec.toString()
                )
            )
        }

        if (options.htmlMaxChars < AiChatOptions.MIN_HTML_MAX_CHARS ||
            options.htmlMaxChars > AiChatOptions.MAX_HTML_MAX_CHARS
        ) {
            issues.add(
                ValidationIssue(
                    field = FIELD_HTML_MAX_CHARS,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_html_max_chars_range,
                    detail = options.htmlMaxChars.toString()
                )
            )
        }

        return ValidationResult(issues)
    }

    const val FIELD_ENDPOINT = "endpoint"
    const val FIELD_API_KEY = "apiKey"
    const val FIELD_MODEL = "model"
    const val FIELD_TEMPERATURE = "temperature"
    const val FIELD_MAX_TOKENS = "maxTokens"
    const val FIELD_TIMEOUT_SEC = "timeoutSec"
    const val FIELD_HTML_MAX_CHARS = "htmlMaxChars"
}
