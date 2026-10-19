package io.legado.app.mcp

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.service.McpService
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Exercises the Android 15+ dataSync timeout through the system, not by calling
 * Service.onTimeout() directly. The Android Developers guide documents this
 * device_config override in its foreground-service timeout testing section.
 */
@RunWith(AndroidJUnit4::class)
class McpDataSyncTimeoutInstrumentedTest {

    @Test
    fun systemTimeoutStopsOnlyTheTestMcpServiceAndClosesItsListener() = runBlocking {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(
            "dataSync foreground-service timeouts require an Android 15+ device",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        )
        assumeTrue(
            "The app must target Android 15+; this test does not persist an am compat override",
            appContext.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        )
        assumeFalse(
            "Do not interrupt an MCP session that was already active before this test",
            McpService.status.value.running || McpService.status.value.starting
        )

        val previousPort = McpConfig.port
        val testPort = ServerSocket(0).use { it.localPort }
        assumeTrue("The ephemeral port must be valid for McpConfig", testPort in 1024..65535)
        val credentials = appContext.getSharedPreferences(MCP_CREDENTIALS_PREFS, Context.MODE_PRIVATE)
        val hadTokenPreference = credentials.contains(McpConfig.TOKEN_KEY)
        val previousTokenPreference = credentials.getString(McpConfig.TOKEN_KEY, null)

        val previousTimeout = executeShellCommand(DEVICE_CONFIG_GET).trim()
        assertTrue(
            "Cannot safely restore device_config value: ${previousTimeout.ifEmpty { "<empty>" }}",
            previousTimeout == UNSET_DEVICE_CONFIG_VALUE || previousTimeout.toLongOrNull() != null
        )

        val scenario = ActivityScenario.launch<ConfigActivity>(
            android.content.Intent(appContext, ConfigActivity::class.java)
                .putExtra("configTag", ConfigTag.OTHER_CONFIG)
        )
        var portChanged = false
        var timeoutOverrideAttempted = false
        var serviceStartAttempted = false

        try {
            McpConfig.port = testPort
            portChanged = true

            // The app targets API 35+, so FGS_INTRODUCE_TIME_LIMITS is enabled
            // by target behavior; avoid a persistent `am compat enable` change.
            timeoutOverrideAttempted = true
            executeShellCommand("device_config put $DEVICE_CONFIG_NAMESPACE $DEVICE_CONFIG_KEY $TEST_TIMEOUT_MS")
            assertEquals(TEST_TIMEOUT_MS.toString(), executeShellCommand(DEVICE_CONFIG_GET).trim())

            serviceStartAttempted = true
            scenario.onActivity { McpService.start(it) }
            val running = awaitRunningOrError()
            assertFalse("McpService failed to start: ${running.error}", running.error != null)
            assertTrue(running.running)
            assertTrue("McpServer listener was not bound", isPortListening(testPort))

            // This is the same background transition used by the documented adb test flow.
            executeShellCommand("input keyevent KEYCODE_HOME")
            awaitActivityBackgrounded(scenario)

            // No stop call is issued here: only the Android system timeout may end the session.
            val stopped = awaitServiceStopped()
            assertFalse(stopped.running)
            assertFalse(stopped.starting)
            assertTrue("McpServer listener remained open after dataSync timeout", awaitPortClosed(testPort))
        } finally {
            try {
                if (serviceStartAttempted) {
                    McpService.stop(appContext)
                    awaitServiceStopped()
                    awaitPortClosed(testPort)
                }
            } finally {
                try {
                    scenario.close()
                } finally {
                    if (portChanged) McpConfig.port = previousPort
                    try {
                        if (serviceStartAttempted) {
                            restoreTokenPreference(credentials, hadTokenPreference, previousTokenPreference)
                        }
                    } finally {
                        if (timeoutOverrideAttempted) restoreDeviceConfig(previousTimeout)
                    }
                }
            }
        }
    }

    private suspend fun awaitRunningOrError(): io.legado.app.service.McpStatus =
        withTimeout(START_TIMEOUT_MS) {
            McpService.status.first { it.running || it.error != null }
        }

    private suspend fun awaitServiceStopped(): io.legado.app.service.McpStatus =
        withTimeout(STOP_TIMEOUT_MS) {
            McpService.status.first { !it.running && !it.starting }
        }

    private suspend fun awaitActivityBackgrounded(scenario: ActivityScenario<ConfigActivity>) {
        withTimeout(BACKGROUND_TIMEOUT_MS) {
            while (scenario.state.isAtLeast(Lifecycle.State.STARTED)) delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun awaitPortClosed(port: Int): Boolean = withTimeout(STOP_TIMEOUT_MS) {
        while (isPortListening(port)) delay(POLL_INTERVAL_MS)
        true
    }

    private fun isPortListening(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(LOOPBACK, port), SOCKET_PROBE_TIMEOUT_MS)
        }
    }.isSuccess

    private fun restoreDeviceConfig(previousValue: String) {
        if (previousValue == UNSET_DEVICE_CONFIG_VALUE) {
            executeShellCommand("device_config delete $DEVICE_CONFIG_NAMESPACE $DEVICE_CONFIG_KEY")
        } else {
            // previousValue was checked to be numeric before any mutation.
            executeShellCommand("device_config put $DEVICE_CONFIG_NAMESPACE $DEVICE_CONFIG_KEY $previousValue")
        }
        assertEquals(previousValue, executeShellCommand(DEVICE_CONFIG_GET).trim())
    }

    private fun restoreTokenPreference(
        credentials: android.content.SharedPreferences,
        hadToken: Boolean,
        previousToken: String?
    ) {
        val editor = credentials.edit()
        if (hadToken) editor.putString(McpConfig.TOKEN_KEY, previousToken)
        else editor.remove(McpConfig.TOKEN_KEY)
        check(editor.commit()) { "Failed to restore MCP credential preference after test" }
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText().trim() }
    }

    private companion object {
        const val DEVICE_CONFIG_NAMESPACE = "activity_manager"
        const val DEVICE_CONFIG_KEY = "data_sync_fgs_timeout_duration"
        const val MCP_CREDENTIALS_PREFS = "mcp_credentials"
        const val TEST_TIMEOUT_MS = 3_000L
        const val START_TIMEOUT_MS = 20_000L
        const val STOP_TIMEOUT_MS = 12_000L
        const val BACKGROUND_TIMEOUT_MS = 8_000L
        const val POLL_INTERVAL_MS = 100L
        const val SOCKET_PROBE_TIMEOUT_MS = 300
        const val LOOPBACK = "127.0.0.1"
        const val UNSET_DEVICE_CONFIG_VALUE = "null"
        const val DEVICE_CONFIG_GET = "device_config get $DEVICE_CONFIG_NAMESPACE $DEVICE_CONFIG_KEY"
    }
}
