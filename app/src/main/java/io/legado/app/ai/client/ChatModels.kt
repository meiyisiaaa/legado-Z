package io.legado.app.ai.client

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容接口的请求/响应 DTO。
 *
 * 约定：snake_case 的线上字段用 [SerializedName] 映射，Kotlin 侧一律 camelCase。
 * 所有响应字段都给了可空默认值 —— 第三方"OpenAI 兼容"端点的字段缺失非常常见，
 * 缺字段不应导致反序列化崩溃，而应走 [ChatResponse.firstContent] 的空串路径。
 */

/** 一条对话消息。 */
data class ChatMessage(
    val role: String = ROLE_USER,
    val content: String = ""
) {

    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun system(content: String): ChatMessage = ChatMessage(ROLE_SYSTEM, content)

        fun user(content: String): ChatMessage = ChatMessage(ROLE_USER, content)

        fun assistant(content: String): ChatMessage = ChatMessage(ROLE_ASSISTANT, content)
    }
}

/** chat/completions 请求体。v1 固定 [stream] = false（架构 Q1）。 */
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val stream: Boolean = false
)

/** 服务端回传的 token 用量。 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerializedName("completion_tokens")
    val completionTokens: Int = 0,
    @SerializedName("total_tokens")
    val totalTokens: Int = 0
)

/** 一个候选回复。 */
data class Choice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

/** chat/completions 响应体。 */
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice>? = null,
    val usage: Usage? = null
) {

    /** 取首个候选的正文；无候选或正文为空时返回空串（由调用方转 `AiError.EmptyResponse`）。 */
    fun firstContent(): String = choices?.firstOrNull()?.message?.content.orEmpty()

    /** 回复是否因超出 max_tokens 而被截断。 */
    fun truncated(): Boolean = choices?.firstOrNull()?.finishReason == FINISH_REASON_LENGTH

    companion object {
        const val FINISH_REASON_LENGTH = "length"
    }
}

/** 流式增量（v1 未实现，接口预留）。 */
data class ChatDelta(
    val content: String = "",
    val done: Boolean = false
)

/**
 * OpenAI 风格的错误响应体。
 * 用于把服务端返回的可读错误信息带进 `AiError.detail`（**不含**任何请求头/凭据）。
 */
data class ApiErrorBody(
    val error: ApiErrorDetail? = null
) {

    data class ApiErrorDetail(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null
    )

    /** 提取一段适合直接展示给用户的错误描述，取不到时返回空串。 */
    fun readableMessage(): String {
        val detail = error ?: return ""
        val message = detail.message?.trim().orEmpty()
        if (message.isNotEmpty()) return message
        return listOfNotNull(detail.code, detail.type).joinToString(" ")
    }
}
