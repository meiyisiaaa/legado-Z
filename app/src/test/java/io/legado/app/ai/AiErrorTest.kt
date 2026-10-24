package io.legado.app.ai

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [AiError] 单测。
 *
 * 两条不能破的契约：
 * 1. `CancellationException` **必须原样重抛**。一旦被包成 AiError，
 *    协程取消就变成了业务错误，UI 会在用户主动取消后弹一个"未知错误"，
 *    更糟的是父协程的取消传播会被截断。
 * 2. `detail` **绝不能带凭据**。这是唯一会被写进日志、崩溃上报和用户截图的字段。
 */
class AiErrorTest {

    @Test
    fun `wrap遇到取消异常原样重抛而不是包装`() {
        val cancellation = CancellationException("用户取消")

        val thrown = assertThrowsOf<CancellationException> {
            AiError.wrap(cancellation)
        }

        assertSame("必须是同一个异常对象", cancellation, thrown)
    }

    @Test
    fun `wrap保持已有的AiError不变`() {
        val original = AiError.SchemaInvalid("bookSourceUrl")

        assertSame(original, AiError.wrap(original))
    }

    @Test
    fun `wrap把超时异常映射为Timeout`() {
        val wrapped = AiError.wrap(SocketTimeoutException("timeout"))

        assertTrue(wrapped is AiError.Timeout)
        assertEquals(R_TIMEOUT, wrapped.messageRes)
    }

    @Test
    fun `wrap把并发超时异常映射为Timeout`() {
        val wrapped = AiError.wrap(java.util.concurrent.TimeoutException("timeout"))

        assertTrue(wrapped is AiError.Timeout)
    }

    @Test
    fun `wrap把IO异常映射为NetworkFailure`() {
        assertTrue(AiError.wrap(IOException("broken pipe")) is AiError.NetworkFailure)
        assertTrue(AiError.wrap(UnknownHostException("dns")) is AiError.NetworkFailure)
    }

    @Test
    fun `wrap兜底为Unknown`() {
        val wrapped = AiError.wrap(IllegalStateException("哪儿都不挨着"))

        assertTrue(wrapped is AiError.Unknown)
        assertEquals("哪儿都不挨着", wrapped.detail)
    }

    @Test
    fun `wrap保留原始异常作为cause`() {
        val cause = IOException("boom")

        assertSame(cause, AiError.wrap(cause).cause)
    }

    @Test
    fun `每个错误类型都有可用的文案资源`() {
        val errors = listOf(
            AiError.NotConfigured(),
            AiError.InvalidConfig("x"),
            AiError.NetworkFailure("x"),
            AiError.Timeout("x"),
            AiError.Unauthorized("x"),
            AiError.RateLimited("x"),
            AiError.ServerError("x"),
            AiError.EmptyResponse("x"),
            AiError.JsonNotFound("x"),
            AiError.JsonMalformed("x"),
            AiError.SchemaInvalid("x"),
            AiError.FetchFailed("x"),
            AiError.StreamNotSupported(),
            AiError.Unknown("x")
        )

        errors.forEach { error ->
            assertNotEquals(
                "${error::class.java.simpleName} 的 messageRes 未设置",
                0,
                error.messageRes
            )
        }
    }

    @Test
    fun `文案资源两两不同避免复制粘贴事故`() {
        val resIds = listOf(
            AiError.NotConfigured().messageRes,
            AiError.InvalidConfig().messageRes,
            AiError.NetworkFailure().messageRes,
            AiError.Timeout().messageRes,
            AiError.Unauthorized().messageRes,
            AiError.RateLimited().messageRes,
            AiError.ServerError().messageRes,
            AiError.EmptyResponse().messageRes,
            AiError.JsonNotFound().messageRes,
            AiError.JsonMalformed().messageRes,
            AiError.SchemaInvalid().messageRes,
            AiError.FetchFailed().messageRes,
            AiError.StreamNotSupported().messageRes,
            AiError.Unknown().messageRes
        )

        assertEquals("存在重复的文案资源 id", resIds.size, resIds.toSet().size)
    }

    @Test
    fun `JsonNotFound不把原始回复放进detail`() {
        val raw = "模型絮絮叨叨了一堆但没给 JSON"

        assertEquals(null, AiError.JsonNotFound(raw).detail)
    }

    @Test
    fun `detail会作为异常message透出`() {
        assertEquals("bookSourceUrl 缺失", AiError.SchemaInvalid("bookSourceUrl 缺失").message)
    }

    @Test
    fun `构造错误时detail不含凭据`() {
        // 这条是对调用方的约定式回归：AiError 自身不该拼接任何配置信息
        val options = AiTestFixtures.options()
        val error = AiError.Unauthorized("HTTP 401 invalid api key")

        assertFalse(error.detail.orEmpty().contains(options.apiKey))
        assertFalse(error.detail.orEmpty().contains("Authorization"))
    }

    private companion object {
        val R_TIMEOUT = AiError.Timeout().messageRes
    }
}
