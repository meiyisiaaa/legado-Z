package io.legado.app.ai

import io.legado.app.ai.engine.ExplainEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SelectionQaSessionTest {
    @Test fun `whole pairs are evicted by count and character budget`() {
        val session = SelectionQaSession()
        session.bind("selection-a")
        repeat(6) { session.complete(session.begin(), SelectionQaPair("q$it", "a$it")) }
        assertEquals(listOf("q2", "q3", "q4", "q5"), session.history.map { it.question })
        session.complete(session.begin(), SelectionQaPair("long", "x".repeat(7996)))
        assertEquals(1, session.history.size)
        session.complete(session.begin(), SelectionQaPair("too-long", "x".repeat(8000)))
        assertTrue(session.history.isEmpty())
    }

    @Test fun `cancel and changing selection reject late outcomes`() {
        val session = SelectionQaSession()
        session.bind("a")
        val old = session.begin()
        session.cancel()
        assertFalse(session.complete(old, SelectionQaPair("cancelled", "late")))
        val current = session.begin()
        session.bind("b")
        assertFalse(session.complete(current, SelectionQaPair("old selection", "late")))
        assertTrue(session.history.isEmpty())
    }

    @Test fun `successful history is sent with current selection only`() = runBlocking {
        val client = FakeAiChatClient.replying("answer")
        ExplainEngine(client).ask(AiTestFixtures.options(), ExplainInput("selected"), "current",
            listOf(SelectionQaPair("previous", "successful")))
        val messages = client.messagesSeen.single()
        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it.role })
        assertEquals("previous", messages[1].content)
        assertEquals("successful", messages[2].content)
        assertTrue(messages.last().content.contains("selected"))
        assertTrue(messages.last().content.contains("current"))
    }

    @Test fun `long questions never reach provider`() = runBlocking {
        val client = FakeAiChatClient.replying("unused")
        try {
            ExplainEngine(client).ask(AiTestFixtures.options(), ExplainInput("selected"), "q".repeat(2001))
            fail("question should be rejected")
        } catch (_: AiError.QuestionTooLong) { }
        assertEquals(0, client.callCount)
    }
}
