package io.legado.app.ai.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.utils.INITIAL_GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OpenAI 兼容端点的一次性（非流式）对话补全实现。
 *
 * 设计要点：
 * - **依赖注入 [Call.Factory] 而不是 `OkHttpClient`**：`okhttp3.Call.Factory` 是单方法接口，
 *   单测里用 `FakeCallFactory` 三行就能伪造任意 HTTP 响应，无需引入 MockWebServer。
     *   同时也满足架构文档 §7.2「禁止新建 OkHttpClient」——真实实例由 UI 层从
 *   `okHttpClient.newBuilder()` 派生后注入。
 * - **超时用 [withTimeout] 而不是 OkHttp 的 readTimeout**：超时秒数来自每次调用的
 *   [AiChatOptions]，而 OkHttp 的超时是建在 client 上的，做不到 per-call。
 *   [TimeoutCancellationException] 是我们自己作用域内产生的，映射为 [AiError.Timeout]；
 *   来自父协程的 [CancellationException] 则原样上抛（架构文档 §7.3）。
 * - **响应手工解析而不是 `Gson.fromJson`**：第三方"OpenAI 兼容"端点字段缺失/类型漂移极常见
 *   （`usage` 缺失、`prompt_tokens` 为 null 等），反射式反序列化遇到 null 写入 Int 字段会直接崩。
 *   手工解析可以做到「任何字段坏掉都只影响那一个字段」。请求体则仍用 Gson 序列化
 *   （[INITIAL_GSON] 只注册了反序列化器，序列化路径完全安全，且能正确应用 `@SerializedName`）。
 */
class OpenAiClient(
    private val callFactory: Call.Factory
) : AiChatClient {

    override suspend fun chat(
        options: AiChatOptions,
        messages: List<ChatMessage>
    ): ChatResponse {
        val url = options.chatCompletionsUrl()
        if (url.isBlank() || options.apiKey.isBlank() || options.model.isBlank()) {
            throw AiError.NotConfigured()
        }

        val request = buildRequest(url, options, messages)
        val timeoutMillis = options.timeoutSec.coerceAtLeast(1).toLong() * MILLIS_PER_SECOND

        return try {
            withTimeout(timeoutMillis) {
                callFactory.newCall(request).awaitAiResponse().use { response ->
                    handleResponse(response, options)
                }
            }
        } catch (e: TimeoutCancellationException) {
            // 本作用域自己触发的超时，不是外部取消，必须转成业务错误。
            throw AiError.Timeout("${options.timeoutSec}s", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw AiError.wrap(e)
        }
    }

    /* ---------------- 请求 ---------------- */

    private fun buildRequest(
        url: String,
        options: AiChatOptions,
        messages: List<ChatMessage>
    ): Request {
        val payload = INITIAL_GSON.toJson(
            ChatRequest(
                model = options.model,
                messages = messages,
                temperature = options.temperature,
                maxTokens = options.maxTokens,
                stream = false
            )
        )
        return runCatching {
            Request.Builder()
                .url(url)
                .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX${options.apiKey}")
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .post(payload.toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
                .build()
        }.getOrElse {
            // 只回显 endpoint，绝不回显带 key 的完整请求信息。
            throw AiError.InvalidConfig(options.endpoint)
        }
    }

    /* ---------------- 响应 ---------------- */

    private fun handleResponse(response: Response, options: AiChatOptions): ChatResponse {
        val bodyText = runCatching { response.body.string() }.getOrDefault("")
        if (!response.isSuccessful) {
            throw mapHttpError(response.code, bodyText, options)
        }
        return parseChatResponse(bodyText, options)
    }

    /**
     * 把非 2xx 响应映射成语义化的 [AiError]。
     *
     * detail 只携带服务端返回的可读描述，并强制抹掉可能被原样回显的 apiKey
     * （架构文档 §7.4 隐私红线）。
     */
    internal fun mapHttpError(code: Int, bodyText: String, options: AiChatOptions): AiError {
        val detail = scrub(readableError(bodyText, code), options)
        return when {
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> AiError.Unauthorized(detail)
            code == HTTP_REQUEST_TIMEOUT -> AiError.Timeout(detail)
            code == HTTP_TOO_MANY_REQUESTS -> AiError.RateLimited(detail)
            else -> AiError.ServerError(detail)
        }
    }

    private fun readableError(bodyText: String, code: Int): String {
        val fallback = "HTTP $code"
        if (bodyText.isBlank()) return fallback
        val message = runCatching {
            val element = JsonParser.parseString(bodyText)
            if (!element.isJsonObject) return@runCatching ""
            val errorObject = element.asJsonObject.get(FIELD_ERROR)
                ?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@runCatching ""
            ApiErrorBody(
                ApiErrorBody.ApiErrorDetail(
                    message = errorObject.readString(FIELD_MESSAGE),
                    type = errorObject.readString(FIELD_TYPE),
                    code = errorObject.readString(FIELD_CODE)
                )
            ).readableMessage()
        }.getOrDefault("")
        val readable = message.ifBlank { bodyText.trim() }
        return "$fallback ${readable.clip()}".trim()
    }

    /** 手工解析，任何字段异常都只降级该字段，不影响整体。 */
    private fun parseChatResponse(bodyText: String, options: AiChatOptions): ChatResponse {
        if (bodyText.isBlank()) throw AiError.EmptyResponse()
        val element = runCatching { JsonParser.parseString(bodyText) }.getOrNull()
            ?: throw AiError.JsonMalformed(scrub(bodyText.clip(), options))
        if (!element.isJsonObject) throw AiError.JsonMalformed(scrub(bodyText.clip(), options))
        val root = element.asJsonObject

        val choices = root.get(FIELD_CHOICES)
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapIndexedNotNull { index, item ->
                if (!item.isJsonObject) null else parseChoice(item.asJsonObject, index)
            }
            .orEmpty()

        val usage = root.get(FIELD_USAGE)
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.let {
                Usage(
                    promptTokens = it.readInt(FIELD_PROMPT_TOKENS),
                    completionTokens = it.readInt(FIELD_COMPLETION_TOKENS),
                    totalTokens = it.readInt(FIELD_TOTAL_TOKENS)
                )
            }

        return ChatResponse(
            id = root.readString(FIELD_ID).ifBlank { null },
            model = root.readString(FIELD_MODEL).ifBlank { null },
            choices = choices,
            usage = usage
        )
    }

    private fun parseChoice(choice: JsonObject, index: Int): Choice {
        val messageObject = choice.get(FIELD_MESSAGE)?.takeIf { it.isJsonObject }?.asJsonObject
        val message = messageObject?.let {
            ChatMessage(
                role = it.readString(FIELD_ROLE).ifBlank { ChatMessage.ROLE_ASSISTANT },
                content = it.readString(FIELD_CONTENT)
            )
        }
        return Choice(
            index = choice.readIntOrNull(FIELD_INDEX) ?: index,
            message = message,
            finishReason = choice.readString(FIELD_FINISH_REASON).ifBlank { null }
        )
    }

    /* ---------------- 小工具 ---------------- */

    /** 万一服务端把请求头原样回显，这里兜底抹掉 apiKey。 */
    private fun scrub(text: String, options: AiChatOptions): String {
        val key = options.apiKey.trim()
        if (key.isEmpty() || !text.contains(key)) return text
        return text.replace(key, options.maskedKey())
    }

    private fun JsonObject.readString(name: String): String {
        val element = get(name) ?: return ""
        if (element.isJsonNull || !element.isJsonPrimitive) return ""
        return runCatching { element.asString }.getOrDefault("")
    }

    private fun JsonObject.readIntOrNull(name: String): Int? {
        val element = get(name) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return runCatching { element.asInt }.getOrNull()
    }

    private fun JsonObject.readInt(name: String): Int = readIntOrNull(name) ?: 0

    private fun String.clip(): String =
        if (length <= MAX_DETAIL_CHARS) this else take(MAX_DETAIL_CHARS) + "…"

    companion object {
        const val MAX_DETAIL_CHARS = 300

        private const val MILLIS_PER_SECOND = 1000L

        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_ACCEPT = "Accept"
        private const val BEARER_PREFIX = "Bearer "
        private const val CONTENT_TYPE_JSON = "application/json"

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429

        private const val FIELD_ERROR = "error"
        private const val FIELD_MESSAGE = "message"
        private const val FIELD_TYPE = "type"
        private const val FIELD_CODE = "code"
        private const val FIELD_CHOICES = "choices"
        private const val FIELD_USAGE = "usage"
        private const val FIELD_ID = "id"
        private const val FIELD_MODEL = "model"
        private const val FIELD_INDEX = "index"
        private const val FIELD_ROLE = "role"
        private const val FIELD_CONTENT = "content"
        private const val FIELD_FINISH_REASON = "finish_reason"
        private const val FIELD_PROMPT_TOKENS = "prompt_tokens"
        private const val FIELD_COMPLETION_TOKENS = "completion_tokens"
        private const val FIELD_TOTAL_TOKENS = "total_tokens"
    }
}

/**
 * 把 OkHttp 的回调式 [Call] 桥接成挂起函数，并把协程取消透传成 `Call.cancel()`。
 *
 * 声明为 `internal` 顶层扩展而非私有方法，是为了让同模块的
 * `io.legado.app.ai.OkHttpPageFetcher` 复用同一份取消语义，避免两处各写一遍导致行为漂移。
 */
internal suspend fun Call.awaitAiResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}
