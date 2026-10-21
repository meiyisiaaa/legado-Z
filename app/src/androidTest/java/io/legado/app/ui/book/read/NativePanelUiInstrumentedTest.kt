package io.legado.app.ui.book.read

import android.content.Intent
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.removeLocalUriCache
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.mcp.McpConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.localBook.TextFile
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.McpService
import io.legado.app.ui.book.read.config.RoleTtsDialog
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.config.McpSessionDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class NativePanelUiInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun roleTtsAndMcpPanelsRemainReachableInCompactLandscapeReader() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var hasExistingReaderState = false
        var previousInBookshelf = false
        var previousIsComic = false
        instrumentation.runOnMainSync {
            hasExistingReaderState = ReadBook.callBack != null || ReadBook.book != null
            previousInBookshelf = ReadBook.inBookshelf
            previousIsComic = ReadBookConfig.isComic
        }
        assumeTrue("Skip to avoid replacing an existing reader owner/state", !hasExistingReaderState)
        assumeTrue(
            "Skip to avoid interacting with an existing read-aloud service",
            !BaseReadAloudService.isRun
        )
        assumeTrue(
            "Skip to avoid changing the port of an existing MCP session",
            !McpService.status.value.running && !McpService.status.value.starting
        )

        val context = instrumentation.targetContext
        val originalMcpPort = McpConfig.port
        val fixtureDir = File(context.cacheDir, "native-panel-smoke-${UUID.randomUUID()}")
        val fixtureFile = File(fixtureDir, "local-reader-${UUID.randomUUID()}.txt")
        val marker = "NATIVE_PANEL_READER_MARKER"
        var importedBook: Book? = null
        var scenario: ActivityScenario<ReadBookActivity>? = null
        var previousRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var orientationCaptured = false

        try {
            assertTrue("Could not create private fixture directory", fixtureDir.mkdirs())
            fixtureFile.writeText(
                buildString {
                    appendLine("第一章 原生面板验收")
                    appendLine(marker)
                    appendLine("用于面板交互验收的本地正文。".repeat(100))
                    appendLine("第二章 结束")
                    appendLine("第二章正文。".repeat(80))
                },
                Charsets.UTF_8
            )
            importedBook = LocalBook.importFile(Uri.fromFile(fixtureFile))
            importedBook!!.apply {
                // Require the heading separator so body lines beginning with "第二章正文" are not TOC entries.
                tocUrl = "^第[一二]章[ \\t]+.*\$${TextFile.spaceChars}"
                save()
            }

            scenario = ActivityScenario.launch<ReadBookActivity>(
                Intent(context, ReadBookActivity::class.java)
                    .putExtra("bookUrl", importedBook!!.bookUrl)
                    .putExtra("inBookshelf", true)
            )
            awaitReader(scenario!!, importedBook!!.bookUrl, marker)
            await("fixture table of contents") {
                appDb.bookChapterDao.getChapterList(importedBook!!.bookUrl).size == 2
            }

            scenario!!.onActivity { activity ->
                previousRequestedOrientation = activity.requestedOrientation
                orientationCaptured = true
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            await("reader enters landscape") {
                var isLandscape = false
                scenario!!.onActivity { activity ->
                    isLandscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                isLandscape
            }
            awaitReader(scenario!!, importedBook!!.bookUrl, marker)

            // The activity applies the user's app language independently of targetContext.
            lateinit var uiContext: Context
            scenario!!.onActivity { uiContext = it }
            scenario!!.onActivity { activity ->
                RoleTtsDialog().show(activity.supportFragmentManager, ROLE_TTS_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }
            awaitDialog<RoleTtsDialog>(scenario!!, ROLE_TTS_TAG)
            setDialogHeight<RoleTtsDialog>(scenario!!, heightDp = 250)

            composeRule.onNodeWithTag("role_tts_enabled").performScrollTo().assertIsDisplayed()
            val narratorRow = composeRule.onAllNodesWithTag("role_tts_role")[0].performScrollTo()
            val narratorDescription = narratorRow.fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].joinToString("\n")
            assertTrue(
                "A newly imported book should show the narrator's unbound-provider state",
                narratorDescription.contains(uiContext.getString(R.string.role_tts_unbound))
            )
            narratorRow.performClick()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_rename)).assertIsDisplayed()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_add_alias)).assertIsDisplayed()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_bind_voice)).assertIsDisplayed()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_add_alias)).performClick()
            composeRule.onNodeWithTag("role_tts_input").performTextInput("native-panel-alias")
            closeSoftKeyboard()
            composeRule.onNodeWithText(uiContext.getString(R.string.ok)).performClick()
            val updatedNarratorDescription = composeRule.onAllNodesWithTag("role_tts_role")[0]
                .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString("\n")
            assertTrue(
                "Adding an alias should update the role's accessible description",
                updatedNarratorDescription.contains("native-panel-alias")
            )
            composeRule.onAllNodesWithTag("role_tts_role")[0].performScrollTo().performClick()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_rename)).assertIsDisplayed()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_add_alias)).assertIsDisplayed()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_bind_voice)).assertIsDisplayed()
            pressBack()

            composeRule.onNodeWithTag("role_tts_add_role").performScrollTo().assertIsDisplayed().performClick()
            composeRule.onNode(hasText(uiContext.getString(R.string.role_tts_create_role))
                .and(hasSetTextAction().not())).assertIsDisplayed()
            composeRule.onNodeWithText(uiContext.getString(R.string.cancel)).performClick()

            composeRule.onNodeWithTag("role_tts_generate").performScrollTo().assertIsDisplayed()
            captureScreenshot("role-tts-landscape.png")
            composeRule.onNodeWithTag("role_tts_generate").performClick()
            composeRule.onNodeWithText(uiContext.getString(R.string.role_tts_chapter_picker)).assertIsDisplayed()
            composeRule.onNodeWithTag("role_tts_generate_selected").assertIsNotEnabled()
            composeRule.onNodeWithTag("role_tts_chapter_0").assertIsOff().performClick().assertIsOn()
            composeRule.onNodeWithTag("role_tts_generate_selected").assertIsEnabled()
            composeRule.onNodeWithTag("role_tts_chapter_0").performClick().assertIsOff()
            composeRule.onNodeWithTag("role_tts_generate_selected").assertIsNotEnabled()
            pressBack()
            composeRule.onNodeWithTag("role_tts_root").assertIsDisplayed()
            pressBack()
            awaitDialogClosed<RoleTtsDialog>(scenario!!, ROLE_TTS_TAG)
            awaitReader(scenario!!, importedBook!!.bookUrl, marker)

            scenario!!.onActivity { activity ->
                McpSessionDialog().show(activity.supportFragmentManager, MCP_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }
            awaitDialog<McpSessionDialog>(scenario!!, MCP_TAG)
            setDialogHeight<McpSessionDialog>(scenario!!, heightDp = 280)

            listOf("mcp_start", "mcp_stop", "mcp_copy_token", "mcp_regenerate", "mcp_status").forEach { tag ->
                composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            }

            composeRule.onNodeWithTag("mcp_port").performScrollTo().performClick()
            composeRule.onNodeWithTag("mcp_port").performTextClearance()
            composeRule.onNodeWithTag("mcp_port").performTextInput("80")
            closeSoftKeyboard()
            composeRule.onNodeWithText("80").assertIsDisplayed()
            composeRule.onNodeWithTag("mcp_start").performScrollTo().performClick()

            composeRule.onNodeWithText(uiContext.getString(R.string.mcp_port_invalid)).assertIsDisplayed()
            composeRule.onNodeWithTag("mcp_port").performScrollTo().assertIsDisplayed()
            captureScreenshot("mcp-session-invalid-port-landscape.png")

            pressBack()
            awaitDialogClosed<McpSessionDialog>(scenario!!, MCP_TAG)
            awaitReader(scenario!!, importedBook!!.bookUrl, marker)
        } finally {
            scenario?.let { activeScenario ->
                runCatching {
                    activeScenario.onActivity { activity ->
                        activity.supportFragmentManager.fragments
                            .filter { it is RoleTtsDialog || it is McpSessionDialog }
                            .filterIsInstance<DialogFragment>()
                            .forEach { it.dismissAllowingStateLoss() }
                        if (orientationCaptured) {
                            activity.requestedOrientation = previousRequestedOrientation
                        }
                    }
                }
                activeScenario.close()
            }
            McpConfig.port = originalMcpPort
            importedBook?.let { ownedBook ->
                appDb.bookChapterDao.delByBook(ownedBook.bookUrl)
                appDb.readRecordDao.deleteByName(ownedBook.name)
                appDb.bookDao.getBook(ownedBook.bookUrl)?.let { persistedBook ->
                    LocalBook.deleteBook(persistedBook, deleteOriginal = true)
                    persistedBook.delete()
                }
                ownedBook.removeLocalUriCache()
                fixtureFile.delete()
                TextFile.clear()
            } ?: fixtureFile.delete()
            fixtureDir.delete()
            instrumentation.runOnMainSync {
                if (ReadBook.callBack == null) {
                    if (ReadBook.book?.bookUrl == importedBook?.bookUrl) ReadBook.book = null
                    ReadBook.clearTextChapter()
                    ReadBook.chapterSize = 0
                    ReadBook.simulatedChapterSize = 0
                    ReadBook.durChapterIndex = 0
                    ReadBook.durChapterPos = 0
                    ReadBook.bookSource = null
                    ReadBook.contentProcessor = null
                    ReadBook.inBookshelf = previousInBookshelf
                    ReadBookConfig.isComic = previousIsComic
                }
            }
        }
    }

    private fun awaitReader(scenario: ActivityScenario<ReadBookActivity>, bookUrl: String, marker: String) {
        await("reader content remains visible") {
            var ready = false
            scenario.onActivity { activity ->
                val readView = activity.findViewById<ReadView>(R.id.read_view)
                val page = readView.curPage.textPage
                ready = ReadBook.book?.bookUrl == bookUrl &&
                        ReadBook.curTextChapter?.isCompleted == true &&
                        page.isCompleted && page.text.contains(marker)
            }
            ready
        }
    }

    private inline fun <reified T : DialogFragment> awaitDialog(
        scenario: ActivityScenario<ReadBookActivity>,
        label: String
    ) {
        try {
            await("$label dialog shown") {
                var shown = false
                scenario.onActivity { activity ->
                    val dialog = activity.findDialogFragment<T>()
                    shown = dialog?.isAdded == true &&
                            dialog.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            dialog.dialog?.isShowing == true &&
                            dialog.dialog?.window?.decorView?.hasWindowFocus() == true
                }
                shown
            }
        } catch (error: AssertionError) {
            var state = "unavailable"
            scenario.onActivity { activity ->
                val fragments = activity.supportFragmentManager.fragments.joinToString(
                    prefix = "[",
                    postfix = "]"
                ) { fragment ->
                    val dialog = fragment as? DialogFragment
                    "${fragment.javaClass.simpleName}(tag=${fragment.tag}, added=${fragment.isAdded}, " +
                        "removing=${fragment.isRemoving}, lifecycle=${fragment.lifecycle.currentState}, " +
                        "showing=${dialog?.dialog?.isShowing}, " +
                        "windowFocus=${dialog?.dialog?.window?.decorView?.hasWindowFocus()})"
                }
                state = "bookUrl=${ReadBook.book?.bookUrl}, " +
                    "activity=${activity.lifecycle.currentState}, " +
                    "orientation=${activity.resources.configuration.orientation}, fragments=$fragments"
            }
            throw AssertionError("${error.message}; state=$state")
        }
    }

    private inline fun <reified T : DialogFragment> awaitDialogClosed(
        scenario: ActivityScenario<ReadBookActivity>,
        label: String
    ) {
        await("$label dialog closed") {
            var closed = false
            scenario.onActivity { activity ->
                closed = activity.supportFragmentManager.fragments
                    .filterIsInstance<T>()
                    .none { it.isAdded || it.isRemoving || it.dialog?.isShowing == true }
            }
            closed
        }
    }

    private inline fun <reified T : DialogFragment> setDialogHeight(
        scenario: ActivityScenario<ReadBookActivity>,
        heightDp: Int
    ) {
        scenario.onActivity { activity ->
            val dialog = activity.findDialogFragment<T>()
                ?: throw AssertionError("Expected ${T::class.java.simpleName} to be active")
            val heightPx = (activity.resources.displayMetrics.density * heightDp).roundToInt()
            val window = dialog.requireDialog().window
            assertNotNull("Expected a window for ${T::class.java.simpleName}", window)
            window?.let { dialogWindow ->
                dialogWindow.attributes = dialogWindow.attributes.apply { height = heightPx }
            }
            assertEquals(heightPx, dialog.requireDialog().window?.attributes?.height ?: -1)
        }
    }

    private fun captureScreenshot(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = context.getExternalFilesDir("porting-validation")
            ?: throw AssertionError("App external files directory is unavailable")
        assertTrue("Could not create screenshot directory", directory.exists() || directory.mkdirs())
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("UiAutomation did not capture a screenshot")
        try {
            File(directory, fileName).outputStream().buffered().use { output ->
                assertTrue("Could not save $fileName", screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun await(label: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $label")
    }

    private inline fun <reified T : DialogFragment> ReadBookActivity.findDialogFragment(): T? =
        supportFragmentManager.fragments
            .filterIsInstance<T>()
            .lastOrNull { it.isAdded && !it.isRemoving }

    private companion object {
        const val ROLE_TTS_TAG = "nativePanelRoleTts"
        const val MCP_TAG = "nativePanelMcp"
    }
}
