package io.legado.app.ui.code

import android.content.Intent
import android.os.SystemClock
import android.webkit.WebView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeEditSafeEditorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun largeUtf8TextCanBeEditedSavedAndKeepsCursorAcrossRecreation() {
        val original = "<script>window.injected=1</script>" + "章节😀".repeat(38_000)
        assertTrue(EditSafety.requiresSafeEditor(original))
        val existingFiles = returnedFiles().map { it.name }.toSet()
        val input = Intent(context, CodeEditActivity::class.java)
            .putExtra("cursorPosition", 17)
        val inputToken = CodeTextTransfer.setInput(context, input, original)!!
        val scenario = ActivityScenario.launch<CodeEditActivity>(input)
        var outputToken: String? = null
        var testFailure: Throwable? = null
        try {
            waitForEditor(scenario)
            val expectedText = original + "完成"
            scenario.onActivity { activity ->
                val editor = activity.window.decorView.findViewWithTag<WebView>(CodeEditActivity.SAFE_EDITOR_TAG)
                assertNotNull(editor)
                assertFalse(editor.settings.allowFileAccess)
                assertFalse(editor.settings.allowContentAccess)
                assertFalse(editor.settings.domStorageEnabled)
                assertTrue(editor.settings.blockNetworkLoads)
                assertTrue(editor.settings.supportZoom())
                assertTrue(editor.settings.builtInZoomControls)
                assertFalse(editor.settings.displayZoomControls)
                editor.evaluateJavascript(
                    "(()=>{const e=document.getElementById('editor');e.value=e.value+'完成';e.setSelectionRange(e.value.length-2,e.value.length-2);e.dispatchEvent(new Event('input',{bubbles:true}));return window.readEditorState()})()",
                    null
                )
            }
            val csp = evaluate(scenario, "document.querySelector('meta[http-equiv=\\\"Content-Security-Policy\\\"]').content")
            assertTrue(csp?.contains("default-src 'none'") == true)
            assertTrue(csp?.contains("connect-src 'none'") == true)
            val viewport = evaluate(scenario, "document.querySelector('meta[name=\\\"viewport\\\"]').content")
            assertFalse(viewport?.contains("maximum-scale") == true)
            val renderedFontSize = evaluate(scenario, "getComputedStyle(document.getElementById('editor')).fontSize")
                ?.removeSuffix("px")?.toFloat()
            assertNotNull(renderedFontSize)
            assertEquals(
                safeEditorFontSizeCssPx(AppConfig.editFontScale, context.resources.configuration.fontScale),
                renderedFontSize!!,
                0.5f
            )
            assertEquals("safe", evaluate(scenario, "window.injected === undefined ? 'safe' : 'executed'"))
            val stateBeforeRecreate = awaitState(scenario)
            assertEquals(expectedText, stateBeforeRecreate.text)
            assertEquals(expectedText.length - 2, stateBeforeRecreate.cursor)
            SystemClock.sleep(450)

            scenario.recreate()
            val stateAfterRecreate = awaitState(scenario)
            assertEquals(expectedText, stateAfterRecreate.text)
            assertEquals(expectedText.length - 2, stateAfterRecreate.cursor)

            scenario.onActivity { activity ->
                val saveMenu = MenuBuilder(activity).add(0, R.id.menu_save, 0, "Save")
                activity.onCompatOptionsItemSelected(saveMenu)
            }

            val outputFile = waitForReturnedTextFile(inputToken, existingFiles)
            outputToken = outputFile.name
            assertEquals(expectedText, CodeTextTransfer.read(context, outputToken!!))
            val returned = CodeTextTransfer.readResult(
                context,
                Intent().putExtra(CodeTextTransfer.EXTRA_TEXT_FILE_TOKEN, outputToken)
                    .putExtra("cursorPosition", expectedText.length - 2)
            )
            assertEquals(expectedText, returned)
            assertFalse(outputFile.exists())
            waitForFileGone(inputToken)
        } catch (error: Throwable) {
            testFailure = error
            throw error
        } finally {
            cleanupAfterTest(scenario, testFailure, inputToken, outputToken)
        }
    }

    @Test
    fun editorFontSizeUsesTheSystemFontScale() {
        assertEquals(AppConfig.editFontScale * 1.5f, safeEditorFontSizeCssPx(AppConfig.editFontScale, 1.5f), 0.01f)
    }

    @Test
    fun backPromptCanContinueEditingThenDiscardAndCleansOwnedInput() {
        val original = "大文本😀".repeat(40_000)
        val expectedText = original + "保留草稿"
        val before = returnedFiles().map { it.name }.toSet()
        val input = Intent(context, CodeEditActivity::class.java)
        val token = CodeTextTransfer.setInput(context, input, original)!!
        val scenario = ActivityScenario.launch<CodeEditActivity>(input)
        var testFailure: Throwable? = null
        try {
            waitForEditor(scenario)
            assertEquals(
                "edited",
                evaluate(
                    scenario,
                    "(()=>{const e=document.getElementById('editor');e.value=e.value+'保留草稿';e.dispatchEvent(new Event('input',{bubbles:true}));return 'edited'})()"
                )
            )
            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            awaitExitPrompt()
            onView(withId(android.R.id.button1)).perform(click())

            assertEquals(expectedText, awaitState(scenario).text)
            assertTrue("continue editing must retain the owned input", File(context.filesDir, "code-edit-transfer/$token").exists())

            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            awaitExitPrompt()
            onView(withId(android.R.id.button2)).perform(click())
            waitForFileGone(token)
            assertEquals(before, returnedFiles().map { it.name }.toSet())
        } catch (error: Throwable) {
            testFailure = error
            throw error
        } finally {
            cleanupAfterTest(scenario, testFailure, token)
        }
    }

    private fun waitForEditor(scenario: ActivityScenario<CodeEditActivity>) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            val result = evaluate(scenario, "document.getElementById('editor') ? 'ready' : 'loading'")
            if (result == "ready") return
            SystemClock.sleep(50)
        }
        throw AssertionError("safe WebView editor did not finish loading")
    }

    private fun awaitState(scenario: ActivityScenario<CodeEditActivity>): EditorState {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            val encoded = evaluate(scenario, "window.readEditorState ? window.readEditorState() : null")
            val value = encoded?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (value != null && value.has("text")) {
                return EditorState(value.getString("text"), value.getInt("cursor"))
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("safe WebView editor state was unavailable")
    }

    private fun evaluate(scenario: ActivityScenario<CodeEditActivity>, script: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        scenario.onActivity { activity ->
            activity.window.decorView.findViewWithTag<WebView>(CodeEditActivity.SAFE_EDITOR_TAG)
                ?.evaluateJavascript(script) {
                    result = runCatching { JSONTokener(it).nextValue() as? String }.getOrNull()
                    latch.countDown()
                } ?: latch.countDown()
        }
        assertTrue("JavaScript evaluation timed out", latch.await(3, TimeUnit.SECONDS))
        return result
    }

    private fun waitForReturnedTextFile(inputToken: String, existingFiles: Set<String>): File {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            val file = returnedFiles().firstOrNull { it.name != inputToken && it.name !in existingFiles }
            if (file != null && file.length() > 0) return file
            SystemClock.sleep(50)
        }
        throw AssertionError("saved output transfer file was not created")
    }

    private fun waitForFileGone(token: String) {
        val file = File(context.filesDir, "code-edit-transfer/$token")
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (file.exists() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertFalse("owned input transfer should be removed", file.exists())
    }

    private fun returnedFiles(): List<File> =
        File(context.filesDir, "code-edit-transfer").listFiles()
            ?.filter { it.isFile && runCatching { UUID.fromString(it.name) }.isSuccess } ?: emptyList()

    private fun cleanupAfterTest(
        scenario: ActivityScenario<CodeEditActivity>,
        testFailure: Throwable?,
        vararg tokens: String?
    ) {
        var cleanupFailure: Throwable? = null
        fun attemptCleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                if (cleanupFailure == null) cleanupFailure = error else cleanupFailure?.addSuppressed(error)
            }
        }

        attemptCleanup {
            if (!isExitPromptVisible()) {
                val backDispatched = runCatching {
                    scenario.onActivity { activity ->
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            activity.onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }.isSuccess
                if (backDispatched) awaitPromptOrActivityFinished(scenario)
            }
            if (isExitPromptVisible()) {
                onView(withId(android.R.id.button2)).perform(click())
            }
            scenario.close()
        }
        tokens.filterNotNull().forEach { token ->
            attemptCleanup { CodeTextTransfer.delete(context, token) }
        }

        cleanupFailure?.let { error ->
            if (testFailure != null) {
                testFailure.addSuppressed(error)
            } else {
                throw error
            }
        }
    }

    private fun isExitPromptVisible(): Boolean = runCatching {
        onView(withText(R.string.exit_no_save)).check(matches(isDisplayed()))
        true
    }.getOrDefault(false)

    private fun awaitExitPrompt() {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (isExitPromptVisible()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("exit confirmation dialog did not appear")
    }

    private fun awaitPromptOrActivityFinished(scenario: ActivityScenario<CodeEditActivity>) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (isExitPromptVisible()) return
            val activityRunning = runCatching {
                var running = false
                scenario.onActivity { activity -> running = !activity.isFinishing && !activity.isDestroyed }
                running
            }.getOrDefault(false)
            if (!activityRunning) return
            SystemClock.sleep(50)
        }
    }

    private data class EditorState(val text: String, val cursor: Int)
}
