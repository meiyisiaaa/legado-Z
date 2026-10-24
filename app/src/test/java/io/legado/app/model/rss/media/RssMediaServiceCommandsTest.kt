package io.legado.app.model.rss.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssMediaServiceCommandsTest {
    @Test fun pendingWriteAndUndeliveredStartBothKeepTheServiceAlive() {
        val commands = RssMediaServiceCommands()
        commands.begin()
        assertFalse(commands.stopIfIdle(0) { error("A database write is pending") })
        val version = commands.issue()
        commands.finish()
        assertFalse(commands.stopIfIdle(0) { error("The start command has not arrived") })
        assertTrue(commands.stopIfIdle(version) { true })
    }

    @Test fun aNewRequestInvalidatesThePreviousIdleSnapshot() {
        val commands = RssMediaServiceCommands()
        val first = commands.issue()
        commands.begin()
        val resumed = commands.issue()
        commands.finish()
        assertFalse(commands.stopIfIdle(first) { error("An old idle snapshot stopped the resumed request") })
        assertTrue(commands.stopIfIdle(resumed) { true })
    }

    @Test fun aRejectedForegroundStartDoesNotLeaveAnUndeliverableRequest() {
        val commands = RssMediaServiceCommands()
        val failed = commands.issue()
        commands.failed(failed)
        assertTrue(commands.stopIfIdle(0) { true })
    }
}
