package io.legado.app.ai

import io.legado.app.R
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * AI 书源助手统一错误类型。
 *
 * 契约（见架构文档 §7.3）：
 * - `io.legado.app.ai` 层抛出的**所有**异常都必须是 [AiError] 的子类，
 *   HTTP / IO 异常在 `OpenAiClient.mapHttpError()` 与 `OkHttpPageFetcher` 内就地转换。
 * - **[CancellationException] 必须原样重抛**，绝不包装成 [AiError]，否则协程取消语义被破坏。
 * - [detail] 中**禁止**携带 apiKey、Authorization 头或含 key 的完整 URL。
 *
 * 说明：这里引用 `io.legado.app.R` 只是取一个编译期 int 常量，
 * 不涉及任何 Android 运行时 API，JVM 单测可正常加载。
 */
sealed class AiError(
    val messageRes: Int,
    val detail: String? = null,
    cause: Throwable? = null
) : Exception(detail, cause) {

    /** 未配置 endpoint / apiKey / model */
    class NotConfigured : AiError(R.string.ai_err_not_configured)

    /** 配置不合法（校验未通过） */
    class InvalidConfig(detail: String? = null) : AiError(R.string.ai_err_invalid_config, detail)

    /** 断网、DNS 失败、连接被拒等 */
    class NetworkFailure(detail: String? = null, cause: Throwable? = null) :
        AiError(R.string.ai_err_network, detail, cause)

    /** 请求超时 */
    class Timeout(detail: String? = null, cause: Throwable? = null) :
        AiError(R.string.ai_err_timeout, detail, cause)

    /** HTTP 401 / 403：key 无效或无权限 */
    class Unauthorized(detail: String? = null) : AiError(R.string.ai_err_unauthorized, detail)

    /** HTTP 429：限流或余额不足 */
    class RateLimited(detail: String? = null) : AiError(R.string.ai_err_rate_limited, detail)

    /** HTTP 5xx 或其它非 2xx */
    class ServerError(detail: String? = null) : AiError(R.string.ai_err_server, detail)

    /** 200 但 choices 为空 / content 为空 */
    class EmptyResponse(detail: String? = null) : AiError(R.string.ai_err_empty_response, detail)

    /** LLM 回复里抠不出 JSON；原始回复不得进入错误详情。 */
    class JsonNotFound(@Suppress("UNUSED_PARAMETER") rawReply: String? = null) :
        AiError(R.string.ai_err_json_not_found)

    /** JSON 语法非法或顶层不是对象 */
    class JsonMalformed(detail: String? = null, cause: Throwable? = null) :
        AiError(R.string.ai_err_json_malformed, detail, cause)

    /** JSON 语法合法但不符合书源 schema（校验存在 ERROR 级问题） */
    class SchemaInvalid(detail: String? = null) : AiError(R.string.ai_err_schema_invalid, detail)

    /** 抓取目标网页失败 */
    class FetchFailed(detail: String? = null, cause: Throwable? = null) :
        AiError(R.string.ai_err_fetch_failed, detail, cause)

    /** v1 不支持流式（接口已预留，实现待 v1.1） */
    class StreamNotSupported : AiError(R.string.ai_err_stream_unsupported)

    /* ---------------- Phase 2：读者助手 ---------------- */

    /** 问题为空白 */
    class QuestionEmpty : AiError(R.string.ai_err_question_empty)

    class QuestionTooLong : AiError(R.string.ai_err_question_too_long)

    /** 选中文本为空白 */
    class SelectionEmpty : AiError(R.string.ai_err_selection_empty)

    /** 批量生成的地址列表为空 */
    class BatchInputEmpty : AiError(R.string.ai_err_batch_input_empty)

    /** 兜底：未归类的异常 */
    class Unknown(detail: String? = null, cause: Throwable? = null) :
        AiError(R.string.ai_err_unknown, detail, cause)

    companion object {

        /**
         * 把任意异常收敛成 [AiError]。
         *
         * **调用方必须先处理 [CancellationException]**；为防误用，这里遇到取消异常时
         * 直接原样重抛而不是包装。
         */
        fun wrap(e: Throwable): AiError {
            if (e is CancellationException) throw e
            return when (e) {
                is AiError -> e
                is SocketTimeoutException -> Timeout(e.message, e)
                is java.util.concurrent.TimeoutException -> Timeout(e.message, e)
                is IOException -> NetworkFailure(e.message, e)
                else -> Unknown(e.message, e)
            }
        }
    }
}
