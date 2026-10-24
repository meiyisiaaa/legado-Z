package io.legado.app.ai.tts

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.client.ChatResponse
import io.legado.app.ai.client.Choice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardEngineTest {

    @Test
    fun `chunks preserve every UTF16 source unit without splitting surrogate pairs`() {
        val text = "甲😀乙。丙😀丁"

        val chunks = StoryboardEngine.splitIntoChunks(text, maxChars = 3)

        assertEquals(text, chunks.joinToString("") { it.text })
        chunks.forEach { chunk ->
            assertEquals(text.substring(chunk.start, chunk.endExclusive), chunk.text)
            assertTrue(chunk.endExclusive == text.length ||
                !(Character.isHighSurrogate(text[chunk.endExclusive - 1]) &&
                    Character.isLowSurrogate(text[chunk.endExclusive])))
        }
    }

    @Test
    fun `engine joins contiguous chapter local results into source offsets`() = runBlocking {
        val text = "甲乙。丙丁"
        val client = ReplyClient("""{"segments":[{"start":0,"end":2,"kind":"narration","speaker":"旁白"},{"start":2,"end":5,"kind":"dialogue","speaker":"主角"}]}""")
        val engine = StoryboardEngine(client)

        val storyboard = engine.create(
            options = AiChatOptions(endpoint = "https://api.example/v1", model = "model", apiKey = "secret"),
            chapterIndex = 7,
            text = text,
            roles = listOf(BookTtsRole("n", "旁白"), BookTtsRole("p", "主角"))
        )

        assertEquals(7, storyboard.chapterIndex)
        assertEquals(StoryboardEngine.sha256(text), storyboard.contentHash)
        assertEquals(listOf(0, 2), storyboard.segments.map { it.start })
        assertEquals(listOf(2, 5), storyboard.segments.map { it.endExclusive })
        assertEquals(listOf("n", "p"), storyboard.segments.map { it.speakerId })
        assertEquals(1, client.callCount)
    }

    @Test
    fun `engine offsets remain contiguous across long chapter chunks`() = runBlocking {
        val text = "甲".repeat(StoryboardEngine.MAX_CHUNK_CHARS * 2 + 37)
        val client = OffsetReplyClient()

        val storyboard = StoryboardEngine(client).create(
            AiChatOptions(endpoint = "https://api.example/v1", model = "model", apiKey = "secret"),
            chapterIndex = 12,
            text = text,
            roles = listOf(BookTtsRole("n", "旁白"))
        )

        assertEquals(3, client.callCount)
        assertEquals(0, storyboard.segments.first().start)
        assertEquals(text.length, storyboard.segments.last().endExclusive)
        storyboard.segments.zipWithNext().forEach { (left, right) ->
            assertEquals(left.endExclusive, right.start)
        }
    }

    @Test
    fun `an invalid gap rejects the entire chapter`() = runBlocking {
        val client = ReplyClient("""{"segments":[{"start":0,"end":1,"kind":"narration","speaker":"旁白"},{"start":2,"end":3,"kind":"narration","speaker":"旁白"}]}""")

        try {
            StoryboardEngine(client).create(
                AiChatOptions(endpoint = "https://example", model = "m", apiKey = "test-key"),
                0,
                "甲乙丙",
                listOf(BookTtsRole("n", "旁白"))
            )
            throw AssertionError("A gapped storyboard must be rejected")
        } catch (error: AiError.SchemaInvalid) {
            assertEquals(1, client.callCount)
        }
    }

    @Test
    fun `voice changes invalidate audio key but do not change storyboard fingerprint`() {
        val options = AiChatOptions(endpoint = "https://example/v1", model = "m", apiKey = "first")
        val sameModelNewSecret = options.copy(apiKey = "second")
        val role = BookTtsRole(
            "hero",
            "主角",
            binding = RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.SYSTEM, "engine", "voice-a")
        )
        val changedVoiceRole = role.copy(
            binding = RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.SYSTEM, "engine", "voice-b")
        )
        val fingerprint = StoryboardEngine.requestFingerprint(options)
        val changedVoice = StoryboardEngine.audioCacheKey(fingerprint, "hash", 0, 10, "profile", "voice-a", 1f)
        val changedVoiceKey = StoryboardEngine.audioCacheKey(fingerprint, "hash", 0, 10, "profile", "voice-b", 1f)

        assertEquals(fingerprint, StoryboardEngine.requestFingerprint(sameModelNewSecret))
        assertNotEquals(
            fingerprint,
            StoryboardEngine.requestFingerprint(options.copy(temperature = options.temperature + 0.1f))
        )
        assertNotEquals(
            fingerprint,
            StoryboardEngine.requestFingerprint(options.copy(maxTokens = options.maxTokens + 1))
        )
        assertEquals(
            StoryboardEngine.requestFingerprint(options, listOf(role)),
            StoryboardEngine.requestFingerprint(options, listOf(changedVoiceRole))
        )
        assertNotEquals(
            StoryboardEngine.requestFingerprint(options, listOf(role)),
            StoryboardEngine.requestFingerprint(options, listOf(role.copy(name = "改名")))
        )
        assertNotEquals(changedVoice, changedVoiceKey)
    }

    private class ReplyClient(private val reply: String) : AiChatClient {
        var callCount = 0
            private set

        override suspend fun chat(options: AiChatOptions, messages: List<ChatMessage>): ChatResponse {
            callCount++
            return ChatResponse(choices = listOf(Choice(message = ChatMessage.assistant(reply))))
        }
    }

    private class OffsetReplyClient : AiChatClient {
        var callCount = 0
            private set

        override suspend fun chat(options: AiChatOptions, messages: List<ChatMessage>): ChatResponse {
            callCount++
            val chunk = messages.last().content
                .substringAfter("正文开始：\n")
                .substringBeforeLast("\n正文结束。")
            val reply = """{"segments":[{"start":0,"end":${chunk.length},"kind":"narration","speaker":"旁白"}]}"""
            return ChatResponse(choices = listOf(Choice(message = ChatMessage.assistant(reply))))
        }
    }
}
