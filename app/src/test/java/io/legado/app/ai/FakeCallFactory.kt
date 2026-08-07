package io.legado.app.ai

import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/** 一条预设的假 HTTP 响应。[ioException] 非空时直接抛出，用于模拟断网。 */
data class FakeHttpResponse(
    val code: Int = 200,
    val message: String = "OK",
    val body: String = "",
    val ioException: IOException? = null
)

/**
 * 测试用的 [Call.Factory]，让 `OpenAiClient` 在**零网络**环境下跑完整链路。
 *
 * 实现方式是「短路拦截器」而不是手写 `okhttp3.Call` 实现：
 * `Call` 接口跨大版本增删过方法（`timeout()` / `clone()` 等），手写实现会在升级 OkHttp 时
 * 悄无声息地编译失败；而 `Interceptor` + `Response.Builder` 是 OkHttp 最稳定的公开 API。
 * 拦截器不调用 `chain.proceed()`，因此不会产生任何真实连接、DNS 或线程外网请求。
 *
 * 顺带把每次请求的 URL / 头 / body 都录下来，供断言「有没有泄漏 apiKey」「stream 是不是 false」。
 */
class FakeCallFactory(
    private val responder: (Request) -> FakeHttpResponse
) : Call.Factory {

    private val recordedRequests = mutableListOf<Request>()
    private val recordedBodies = mutableListOf<String>()

    /** 依次记录的请求对象。 */
    val requests: List<Request> get() = recordedRequests

    /** 依次记录的请求体文本。 */
    val bodies: List<String> get() = recordedBodies

    /** 实际发起的请求次数。 */
    val callCount: Int get() = recordedRequests.size

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            recordedRequests.add(request)
            recordedBodies.add(request.bodyText())
            val fake = responder(request)
            fake.ioException?.let { throw it }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(fake.code)
                .message(fake.message)
                .body(fake.body.toResponseBody(JSON_MEDIA_TYPE))
                .build()
        })
        .build()

    override fun newCall(request: Request): Call = client.newCall(request)

    private fun Request.bodyText(): String {
        val requestBody = body ?: return ""
        return runCatching {
            val buffer = Buffer()
            requestBody.writeTo(buffer)
            buffer.readUtf8()
        }.getOrDefault("")
    }

    companion object {

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 按顺序返回预设响应；用完后重复最后一条。 */
        fun of(vararg responses: FakeHttpResponse): FakeCallFactory {
            require(responses.isNotEmpty()) { "至少需要一条预设响应" }
            val queue = ArrayDeque(responses.toList())
            val last = responses.last()
            return FakeCallFactory { queue.removeFirstOrNull() ?: last }
        }

        /** 依次返回若干个 200 响应体。 */
        fun okJson(vararg bodies: String): FakeCallFactory =
            of(*bodies.map { FakeHttpResponse(body = it) }.toTypedArray())

        /** 返回单个错误响应。 */
        fun error(code: Int, body: String = ""): FakeCallFactory =
            of(FakeHttpResponse(code = code, message = "ERR", body = body))

        /** 把一段助手回复包装成标准的 chat/completions 响应体。 */
        fun chatCompletion(
            content: String,
            promptTokens: Int = 0,
            completionTokens: Int = 0
        ): String {
            val escaped = escape(content)
            return """
                {
                  "id": "chatcmpl-fake",
                  "model": "fake-model",
                  "choices": [
                    {
                      "index": 0,
                      "message": { "role": "assistant", "content": "$escaped" },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": $promptTokens,
                    "completion_tokens": $completionTokens,
                    "total_tokens": ${promptTokens + completionTokens}
                  }
                }
            """.trimIndent()
        }

        private fun escape(text: String): String = text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
