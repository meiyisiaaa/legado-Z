package io.legado.app.ui.code

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import java.io.File
import java.util.UUID
import org.hamcrest.Matchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeEditSafeEditorTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun largeUtf8TextCanBeEditedSavedAndKeepsCursorAcrossRecreation() {
        val original = "<script>window.injected=1</script>" + "章节😀".repeat(38_000)
        assertTrue(original.toByteArray(Charsets.UTF_8).size > 256 * 1024)
        assertTrue(EditSafety.requiresSafeEditor(original))
        val existingFiles = returnedFiles().map { it.name }.toSet()
        val input = Intent(context, CodeEditActivity::class.java)
            .putExtra("cursorPosition", 17)
        val inputToken = CodeTextTransfer.setInput(context, input, original)!!
        val scenario = ActivityScenario.launchActivityForResult<CodeEditActivity>(input)
        var outputToken: String? = null
        var testFailure: Throwable? = null
        try {
            waitForEditor(scenario, original.length)
            val initialState = readEditorState(scenario)
            assertEquals(original, initialState.text)
            assertEquals(17, initialState.cursor)
            onView(nativeEditorMatcher()).check(matches(isDisplayed()))
            assertNativeEditorSettings(scenario)
            captureScreenshot("safe-editor.png")

            onView(nativeEditorMatcher()).perform(click())
            scenario.onActivity { activity -> nativeEditor(activity).setSelection(original.length) }
            onView(nativeEditorMatcher()).perform(typeTextIntoFocusedView("x"), closeSoftKeyboard())
            assertEquals(original + "x", readEditorState(scenario).text)

            scenario.onActivity { activity ->
                val editor = nativeEditor(activity)
                editor.setSelection(original.length, original.length + 1)
                activity.sendText("完成")
            }
            val expectedText = original + "完成"
            val edited = readEditorState(scenario)
            assertEquals(expectedText, edited.text)
            assertEquals(expectedText.length, edited.cursor)

            scenario.recreate()
            waitForEditor(scenario, expectedText.length)
            val restored = readEditorState(scenario)
            assertEquals(expectedText, restored.text)
            assertEquals(expectedText.length, restored.cursor)

            scenario.onActivity { activity ->
                val saveMenu = MenuBuilder(activity).add(0, R.id.menu_save, 0, "Save")
                activity.onCompatOptionsItemSelected(saveMenu)
            }

            val activityResult = scenario.result
            assertEquals(Activity.RESULT_OK, activityResult.resultCode)
            val resultIntent = checkNotNull(activityResult.resultData)
            assertEquals(expectedText.length, resultIntent.getIntExtra("cursorPosition", -1))
            outputToken = resultIntent.getStringExtra(CodeTextTransfer.EXTRA_TEXT_FILE_TOKEN)
            val savedOutputToken = checkNotNull(outputToken)
            val outputFile = File(context.filesDir, "code-edit-transfer/$savedOutputToken")
            assertTrue(outputFile.exists())
            val returned = CodeTextTransfer.readResult(context, resultIntent)
            assertEquals(expectedText, returned)
            assertFalse(outputFile.exists())
            waitForFileGone(inputToken)
            assertEquals(existingFiles, returnedFiles().map { it.name }.toSet())
        } catch (error: Throwable) {
            testFailure = error
            throw error
        } finally {
            cleanupAfterTest(scenario, testFailure, inputToken, outputToken)
        }
    }

    @Test
    fun readOnlyLargeTextRemainsSelectableAndCannotBeChangedBySendText() {
        val original = "只读大文本😀".repeat(32_000)
        assertTrue(original.toByteArray(Charsets.UTF_8).size > 256 * 1024)
        assertTrue(EditSafety.requiresSafeEditor(original))
        val existingFiles = returnedFiles().map { it.name }.toSet()
        val input = Intent(context, CodeEditActivity::class.java)
            .putExtra(CodeTextTransfer.EXTRA_READ_ONLY, true)
        val inputToken = CodeTextTransfer.setInput(context, input, original)!!
        val scenario = ActivityScenario.launch<CodeEditActivity>(input)
        var testFailure: Throwable? = null
        try {
            waitForEditor(scenario, original.length)
            scenario.onActivity { activity ->
                val editor = nativeEditor(activity)
                assertNull("read-only editor must not expose an editable key listener", editor.keyListener)
                assertTrue("read-only text remains selectable for copying", editor.isTextSelectable)
                editor.setSelection(0, "只读大文本".length)
                activity.sendText("replacement")
                assertEquals(original, editor.editableText.toString())
            }
            assertEquals(original, readEditorState(scenario).text)
            scenario.onActivity { activity -> activity.finish() }
            waitForFileGone(inputToken)
            assertEquals(existingFiles, returnedFiles().map { it.name }.toSet())
        } catch (error: Throwable) {
            testFailure = error
            throw error
        } finally {
            cleanupAfterTest(scenario, testFailure, inputToken)
        }
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
            waitForEditor(scenario, original.length)
            scenario.onActivity { activity ->
                nativeEditor(activity).setSelection(original.length)
                activity.sendText("保留草稿")
                activity.onBackPressedDispatcher.onBackPressed()
            }
            awaitExitPrompt()
            onView(withId(android.R.id.button1)).perform(click())

            assertEquals(expectedText, readEditorState(scenario).text)
            assertTrue("continue editing must retain the owned input", File(context.filesDir, "code-edit-transfer/$token").exists())
            scenario.onActivity { activity ->
                val editor = nativeEditor(activity)
                editor.setSelection(editor.length())
                activity.sendText("继续")
            }
            assertEquals(expectedText + "继续", readEditorState(scenario).text)

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

    private fun assertNativeEditorSettings(scenario: ActivityScenario<CodeEditActivity>) {
        scenario.onActivity { activity ->
            val editor = nativeEditor(activity)
            assertTrue(AppCompatEditText::class.java.isAssignableFrom(editor.javaClass))
            assertEquals(View.AUTOFILL_TYPE_NONE, editor.autofillType)
            assertNull("large code must never be exported through Autofill Binder values", editor.autofillValue)
            assertAccessibilityHistoryBoundary(editor)
            assertEquals(Typeface.MONOSPACE, editor.typeface)
            assertFalse("large text must not enter view-state Bundle", editor.isSaveEnabled)
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, editor.importantForAutofill)
            assertTrue((editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0)
            assertTrue((editor.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0)
            assertTrue(
                "IME text size follows the system font scale",
                kotlin.math.abs(
                    editor.textSize - TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        AppConfig.editFontScale.toFloat(),
                        context.resources.displayMetrics
                    )
                ) < 0.75f
            )
            assertEquals(!AppConfig.editAutoWrap, editor.isHorizontalScrollBarEnabled)
            if (AppConfig.editAutoWrap) {
                assertTrue("the long line should wrap within the editor", editor.lineCount > 1)
            } else {
                assertEquals("hard line breaks are absent from the fixture", 1, editor.lineCount)
            }
            assertNull("hint labels the field without replacing its accessible text", editor.contentDescription)
            assertEquals(context.getString(R.string.edit_code), editor.hint.toString())
            assertFalse(editor.isTextSelectable)
            assertTrue(
                "large-text content is displayed in the tagged native editor",
                activity.window.decorView.findViewWithTag<View>(CodeEditActivity.SAFE_EDITOR_TAG) is AppCompatEditText
            )
        }
    }

    private fun nativeEditorMatcher() = withTagValue(`is`(CodeEditActivity.SAFE_EDITOR_TAG))

    private fun assertAccessibilityHistoryBoundary(editor: AppCompatEditText) {
        val previousDelegate = ViewCompat.getAccessibilityDelegate(editor)
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED).apply {
            beforeText = "a".repeat(99_999) + "😀" + "b".repeat(50_000)
            fromIndex = 120_000
            removedCount = 1
            addedCount = 2
        }
        var intercepted = false
        try {
            ViewCompat.setAccessibilityDelegate(editor, object : AccessibilityDelegateCompat() {
                override fun sendAccessibilityEventUnchecked(host: View, received: AccessibilityEvent) {
                    intercepted = true
                    assertEquals(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED, received.eventType)
                    assertEquals("a".repeat(99_999), received.beforeText.toString())
                    assertEquals(120_000, received.fromIndex)
                    assertEquals(1, received.removedCount)
                    assertEquals(2, received.addedCount)
                }
            })
            editor.sendAccessibilityEventUnchecked(event)
            assertTrue("the event must still reach the accessibility delegate", intercepted)
        } finally {
            ViewCompat.setAccessibilityDelegate(editor, previousDelegate)
            event.recycle()
        }
    }

    private fun nativeEditor(activity: CodeEditActivity): AppCompatEditText =
        activity.window.decorView.findViewWithTag<AppCompatEditText>(CodeEditActivity.SAFE_EDITOR_TAG)
            ?: throw AssertionError("native large-text editor was not attached")

    private fun waitForEditor(scenario: ActivityScenario<CodeEditActivity>, expectedLength: Int) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val editor = activity.window.decorView.findViewWithTag<AppCompatEditText>(CodeEditActivity.SAFE_EDITOR_TAG)
                ready = editor != null && editor.length() == expectedLength
            }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("native large-text editor did not become ready")
    }

    private fun readEditorState(scenario: ActivityScenario<CodeEditActivity>): EditorState {
        var state: EditorState? = null
        scenario.onActivity { activity ->
            val editor = nativeEditor(activity)
            val text = editor.editableText.toString()
            val cursor = editor.selectionStart.takeIf { it >= 0 } ?: text.length
            state = EditorState(text, cursor)
        }
        return checkNotNull(state)
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

    private fun captureScreenshot(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
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

    private fun awaitExitPrompt() {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val visible = runCatching {
                onView(withText(R.string.exit_no_save)).check(matches(isDisplayed()))
                true
            }.getOrDefault(false)
            if (visible) return
            SystemClock.sleep(50)
        }
        throw AssertionError("exit confirmation dialog did not appear")
    }

    private fun cleanupAfterTest(
        scenario: ActivityScenario<CodeEditActivity>,
        testFailure: Throwable?,
        vararg tokens: String?
    ) {
        var cleanupFailure: Throwable? = null
        try {
            scenario.close()
        } catch (error: Throwable) {
            cleanupFailure = error
        }
        tokens.filterNotNull().forEach { token ->
            try {
                CodeTextTransfer.delete(context, token)
            } catch (error: Throwable) {
                if (cleanupFailure == null) cleanupFailure = error else cleanupFailure?.addSuppressed(error)
            }
        }
        cleanupFailure?.let { error ->
            if (testFailure != null) testFailure.addSuppressed(error) else throw error
        }
    }

    private data class EditorState(val text: String, val cursor: Int)
}
