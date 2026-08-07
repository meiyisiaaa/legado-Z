package io.legado.app.ai.client

import io.legado.app.ai.AiError
import io.legado.app.ai.AiTestFixtures
import io.legado.app.ai.FakeCallFactory
import io.legado.app.ai.assertThrowsOf
import io.legado.app.ai.client.OpenAiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OpenAiClient] 单测。
 *
 * 通过 [FakeCallFactory]（短路拦截器，零网络）覆盖：成功解析、各类 HTTP 错误映射、
 * 未配置守卫、请求头/体契约、以及 §7.4 的「apiKey 不得泄漏进错误详情」。
 */
class OpenAiClientTest {

    private fun client(body: String): OpenAiClient =
        OpenAiClient(FakeCallFactory.okJson(body))

    @Test
    fun `成功响应解析出content与usage`() = runBlocking {
        val body = FakeCallFactory.chatCompletion(
            "你好，书源已生成",
            promptTokens = 10,
            completionTokens = 5
        )
        val response = client(body).chat(AiTestFixtures.options(), listOf())

        assertEquals("你好，书源已生成", response.firstContent())
        assertNotNull(response.usage)
        assertEquals(10, response.usage!!.promptTokens)
        assertEquals(5, response.usage!!.completionTokens)
    }

    @Test
    fun `401映射为Unauthorized`() = runBlocking {
        val client = OpenAiClient(
            FakeCallFactory.error(401, """{"error":{"message":"invalid key"}}""")
        )
        assertThrowsOf<AiError.Unauthorized> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        Unit
    }

    @Test
    fun `403映射为Unauthorized`() = runBlocking {
        val client = OpenAiClient(
            FakeCallFactory.error(403, """{"error":{"message":"forbidden"}}""")
        )
        assertThrowsOf<AiError.Unauthorized> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        Unit
    }

    @Test
    fun `429映射为RateLimited`() = runBlocking {
        val client = OpenAiClient(
            FakeCallFactory.error(429, """{"error":{"message":"rate limited"}}""")
        )
        assertThrowsOf<AiError.RateLimited> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        Unit
    }

    @Test
    fun `408映射为Timeout`() = runBlocking {
        val client = OpenAiClient(
            FakeCallFactory.error(408, """{"error":{"message":"timeout"}}""")
        )
        assertThrowsOf<AiError.Timeout> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        Unit
    }

    @Test
    fun `500映射为ServerError`() = runBlocking {
        val client = OpenAiClient(
            FakeCallFactory.error(500, """{"error":{"message":"boom"}}""")
        )
        assertThrowsOf<AiError.ServerError> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        Unit
    }

    @Test
    fun `空body映射为EmptyResponse`() = runBlocking {
        val client = OpenAiClient(FakeCallFactory.okJson(""))
        assertThrowsOf<AiError.EmptyResponse> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        Unit
    }

    @Test
    fun `未配置apiKey时抛NotConfigured`() = runBlocking {
        val client = OpenAiClient(FakeCallFactory.okJson("{}"))
        assertThrowsOf<AiError.NotConfigured> {
            client.chat(AiTestFixtures.options().copy(apiKey = ""), listOf())
        }
        Unit
    }

    @Test
    fun `请求带上Bearer头且地址与stream契约正确`() = runBlocking {
        val factory = FakeCallFactory.okJson(FakeCallFactory.chatCompletion("ok"))
        val client = OpenAiClient(factory)
        client.chat(AiTestFixtures.options(), listOf())

        val request = factory.requests.first()
        val auth = request.header("Authorization").orEmpty()
        assertTrue("应携带 Bearer 头", auth.startsWith("Bearer "))
        assertTrue("应携带 apiKey", auth.contains(AiTestFixtures.options().apiKey))

        assertEquals(
            "地址应以 /chat/completions 结尾",
            "https://api.example.com/v1/chat/completions",
            request.url.toString()
        )
        assertTrue(
            "请求体应含 stream:false（v1 固定非流式）",
            Regex("\\\"stream\\\"\\s*:\\s*false").containsMatchIn(factory.bodies.first())
        )
    }

    @Test
    fun `错误detail不泄漏明文apiKey`() = runBlocking {
        val key = AiTestFixtures.options().apiKey
        val body = """{"error":{"message":"invalid api key $key"}}"""
        val client = OpenAiClient(FakeCallFactory.error(401, body))
        val error = assertThrowsOf<AiError.Unauthorized> {
            client.chat(AiTestFixtures.options(), listOf())
        }
        assertFalse("错误详情不应含明文 apiKey", error.detail.orEmpty().contains(key))
    }
}
