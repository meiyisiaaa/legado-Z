package io.legado.app.ui.book.read

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
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
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matcher
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class NativePanelUiInstrumentedTest {

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

            scenario!!.onActivity { activity ->
                RoleTtsDialog().show(activity.supportFragmentManager, ROLE_TTS_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }
            awaitDialog<RoleTtsDialog>(scenario!!, ROLE_TTS_TAG)
            setDialogHeight<RoleTtsDialog>(scenario!!, heightDp = 250)

            onDialogView(withId(R.id.role_tts_enabled)).perform(scrollTo()).check(matches(isDisplayed()))
            var narratorDescription = ""
            scenario!!.onActivity { activity ->
                val dialog = activity.findDialogFragment<RoleTtsDialog>()
                    ?: throw AssertionError("Expected the role TTS dialog to be active")
                val list = dialog.requireView().findViewById<ViewGroup>(R.id.role_tts_role_list)
                assertTrue("The narrator role should be rendered", list.childCount > 0)
                narratorDescription = list.getChildAt(0).contentDescription.toString()
                assertTrue("The role row should have an accessible action target", list.getChildAt(0).isClickable)
            }
            onDialogView(withContentDescription(narratorDescription))
                .perform(scrollTo(), click())
            onDialogView(withText(R.string.role_tts_rename)).check(matches(isDisplayed()))
            onDialogView(withText(R.string.role_tts_add_alias)).check(matches(isDisplayed()))
            onDialogView(withText(R.string.role_tts_bind_voice)).check(matches(isDisplayed()))
            pressBack()

            onDialogView(withId(R.id.role_tts_add_role)).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
            onDialogView(withText(R.string.role_tts_create_role)).check(matches(isDisplayed()))
            onDialogView(withText(R.string.cancel)).perform(click())

            onDialogView(withId(R.id.role_tts_generate)).perform(scrollTo()).check(matches(isDisplayed()))
            captureScreenshot("role-tts-landscape.png")
            onDialogView(withId(R.id.role_tts_generate)).perform(click())
            onDialogView(withText(R.string.role_tts_chapter_picker)).check(matches(isDisplayed()))
            pressBack()
            onDialogView(withId(R.id.role_tts_root)).check(matches(isDisplayed()))
            pressBack()
            awaitDialogClosed<RoleTtsDialog>(scenario!!, ROLE_TTS_TAG)
            awaitReader(scenario!!, importedBook!!.bookUrl, marker)

            scenario!!.onActivity { activity ->
                McpSessionDialog().show(activity.supportFragmentManager, MCP_TAG)
                activity.supportFragmentManager.executePendingTransactions()
            }
            awaitDialog<McpSessionDialog>(scenario!!, MCP_TAG)
            setDialogHeight<McpSessionDialog>(scenario!!, heightDp = 280)

            listOf(R.id.start, R.id.stop, R.id.copy_token, R.id.regenerate).forEach { id ->
                onDialogView(withId(id)).perform(scrollTo()).check(matches(isDisplayed()))
            }

            onDialogView(withId(R.id.port)).perform(
                scrollTo(),
                click(),
                clearText(),
                typeTextIntoFocusedView("80"),
                closeSoftKeyboard()
            )
            onDialogView(withId(R.id.port)).check(matches(isDisplayed())).check(matches(withText("80")))
            onDialogView(withId(R.id.start)).perform(scrollTo(), click())

            var portError: String? = null
            scenario!!.onActivity { activity ->
                val dialog = activity.findDialogFragment<McpSessionDialog>()
                    ?: throw AssertionError("Expected the MCP session dialog to be active")
                portError = dialog.requireView()
                    .findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.port)
                    .error?.toString()
            }
            assertEquals(context.getString(R.string.mcp_port_invalid), portError)
            onDialogView(withId(R.id.port)).perform(scrollTo()).check(matches(isDisplayed()))
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

    private fun onDialogView(matcher: Matcher<View>) = onView(matcher).inRoot(isDialog())

    private companion object {
        const val ROLE_TTS_TAG = "nativePanelRoleTts"
        const val MCP_TAG = "nativePanelMcp"
    }
}
