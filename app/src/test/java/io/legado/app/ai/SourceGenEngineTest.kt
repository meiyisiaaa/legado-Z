package io.legado.app.ai

import io.legado.app.ai.engine.SourceGenEngine
import io.legado.app.ai.source.BookSourceSchema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SourceGenEngine] 单测。
 *
 * 关键断言是**隐私红线**：用户已经粘贴了 HTML，就**绝不**再联网抓取 ——
 * 用 [FakePageFetcher.callCount] 守住。其余覆盖：URL 抓取路径、未配置/非法守卫、
 * 空回复、围栏 JSON 抠取、以及自动修复触发（温度拉到 0、仅一轮）。
 */
class SourceGenEngineTest {

    private val validJson = AiTestFixtures.VALID_SOURCE_JSON

    @Test
    fun `粘贴HTML时不发起抓取`() = runBlocking {
        val fetcher = FakePageFetcher(html = "<div>页面</div>")
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val engine = SourceGenEngine(client, fetcher)

        val outcome = engine.generate(
            AiTestFixtures.options(),
            GenerateInput(
                intent = "生成书源",
                html = "<html><body><div>页面</div></body></html>"
            )
        )

        assertEquals("已提供 HTML 时不应抓取", 0, fetcher.callCount)
        assertTrue(outcome.canImport())
    }

    @Test
    fun `只给URL时先抓取再生成`() = runBlocking {
        val fetcher = FakePageFetcher(html = "<div>页面</div>")
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val engine = SourceGenEngine(client, fetcher)

        val outcome = engine.generate(
            AiTestFixtures.options(),
            GenerateInput(url = "https://example.com", intent = "生成书源")
        )

        assertEquals(1, fetcher.callCount)
        assertEquals("https://example.com", fetcher.urls.first())
        assertTrue(outcome.canImport())
    }

    @Test
    fun `既无HTML也无URL时仍可用空片段生成`() = runBlocking {
        val fetcher = FakePageFetcher(html = "x")
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val engine = SourceGenEngine(client, fetcher)

        val outcome = engine.generate(
            AiTestFixtures.options(),
            GenerateInput(intent = "只要意图")
        )

        assertEquals(0, fetcher.callCount)
        assertTrue(outcome.canImport())
    }

    @Test
    fun `未配置抛NotConfigured`() = runBlocking {
        val engine = SourceGenEngine(FakeAiChatClient.replying(validJson))
        assertThrowsOf<AiError.NotConfigured> {
            engine.generate(
                AiTestFixtures.options().copy(apiKey = ""),
                GenerateInput(html = "<div>x</div>")
            )
        }
        Unit
    }

    @Test
    fun `配置非法抛InvalidConfig`() = runBlocking {
        val engine = SourceGenEngine(FakeAiChatClient.replying(validJson))
        assertThrowsOf<AiError.InvalidConfig> {
            engine.generate(
                AiTestFixtures.options().copy(temperature = 99f),
                GenerateInput(html = "<div>x</div>")
            )
        }
        Unit
    }

    @Test
    fun `模型返回空内容抛EmptyResponse`() = runBlocking {
        val engine = SourceGenEngine(FakeAiChatClient.replying(""))
        assertThrowsOf<AiError.EmptyResponse> {
            engine.generate(
                AiTestFixtures.options(),
                GenerateInput(html = "<div>x</div>")
            )
        }
        Unit
    }

    @Test
    fun `模型返回围栏JSON被抠出`() = runBlocking {
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val engine = SourceGenEngine(client)

        val outcome = engine.generate(
            AiTestFixtures.options(),
            GenerateInput(html = "<div>x</div>")
        )

        assertTrue(outcome.sourceJson.isNotBlank())
        assertTrue(outcome.sourceJson.contains(BookSourceSchema.BOOK_SOURCE_URL))
    }

    @Test
    fun `JSON缺失且自动修复开启时触发一次重试`() = runBlocking {
        val client = FakeAiChatClient.replying("这不是JSON", validJson)
        val engine = SourceGenEngine(client)

        val outcome = engine.generate(
            AiTestFixtures.options(autoRepair = true),
            GenerateInput(html = "<div>x</div>")
        )

        assertEquals("应发起一次生成 + 一次修复", 2, client.callCount)
        assertEquals("修复轮必须把温度拉到 0", 0f, client.optionsSeen[1].temperature)
        assertTrue(outcome.canImport())
    }

    @Test
    fun `JSON校验错误且自动修复开启时触发一次重试`() = runBlocking {
        val client = FakeAiChatClient.replying(
            AiTestFixtures.SOURCE_JSON_MISSING_URL,
            validJson
        )
        val engine = SourceGenEngine(client)

        val outcome = engine.generate(
            AiTestFixtures.options(autoRepair = true),
            GenerateInput(html = "<div>x</div>")
        )

        assertEquals(2, client.callCount)
        assertTrue(outcome.canImport())
    }

    @Test
    fun `自动修复关闭时JSON缺失直接失败`() = runBlocking {
        val client = FakeAiChatClient.replying("不是JSON")
        val engine = SourceGenEngine(client)

        assertThrowsOf<AiError.JsonNotFound> {
            engine.generate(
                AiTestFixtures.options(autoRepair = false),
                GenerateInput(html = "<div>x</div>")
            )
        }
        assertEquals(1, client.callCount)
    }

    @Test
    fun `送进prompt的HTML片段会被HtmlChunker按上限截断`() = runBlocking {
        val big = "<div>" + "a".repeat(50_000) + "</div>"
        val client = FakeAiChatClient.replying(AiTestFixtures.fenced(validJson))
        val engine = SourceGenEngine(client)

        engine.generate(AiTestFixtures.options(), GenerateInput(html = big))

        val snippet = client.messagesSeen.first().last().content
        assertTrue(
            "片段不应超过 htmlMaxChars 太多",
            snippet.length <= AiChatOptions.DEFAULT_HTML_MAX_CHARS + 200
        )
    }
}
