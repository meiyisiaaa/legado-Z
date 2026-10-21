package io.legado.app.ui.book.source.edit

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.widget.code.CodeView
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.lifecycle.Lifecycle
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SourceEditorUiInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun saveActionPersistsEditedSourceName() {
        val sourceUrl = uniqueSourceUrl()
        val originalName = "SourceEditorOriginal${UUID.randomUUID().toString().take(8)}"
        val editedName = "SourceEditorSaved${UUID.randomUUID().toString().take(8)}"
        val fixture = BookSource(bookSourceUrl = sourceUrl, bookSourceName = originalName)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helpPreference = suppressFirstRunRuleHelp(context)
        var fixtureInserted = false
        var scenario: ActivityScenario<BookSourceEditActivity>? = null
        var testFailure: Throwable? = null

        try {
            assertNull("Fixture URL unexpectedly exists", appDb.bookSourceDao.getBookSource(sourceUrl))
            appDb.bookSourceDao.insert(fixture)
            fixtureInserted = true

            val editorScenario = launchEditor(context, sourceUrl)
            scenario = editorScenario
            awaitSourceNameDisplayed(editorScenario, originalName, "source name editor")
            val saveDescription = activityString(editorScenario, R.string.action_save)

            onView(sourceNameEditor(originalName)).perform(click())
            // typeText passes characters through Gboard, which may transliterate ASCII fixture IDs.
            // replaceText updates this EditText directly and still triggers its TextWatcher.
            onView(allOf(isAssignableFrom(EditText::class.java), hasFocus())).perform(
                replaceText(editedName),
                closeSoftKeyboard()
            )
            awaitSourceNameDisplayed(editorScenario, editedName, "edited source name")

            composeRule.onNodeWithContentDescription(saveDescription).assertIsDisplayed().performClick()
            await("saved source name") {
                appDb.bookSourceDao.getBookSource(sourceUrl)?.bookSourceName == editedName
            }
            assertEquals(editedName, appDb.bookSourceDao.getBookSource(sourceUrl)?.bookSourceName)
        } catch (error: Throwable) {
            testFailure = error
            throw error
        } finally {
            cleanupAfterTest(scenario, context, helpPreference, sourceUrl, fixtureInserted, testFailure)
        }
    }

    @Test
    fun backActionKeepsExitPromptAndDiscardDoesNotPersistDraft() {
        val sourceUrl = uniqueSourceUrl()
        val originalName = "SourceEditorOriginal${UUID.randomUUID().toString().take(8)}"
        val draftName = "SourceEditorDraft${UUID.randomUUID().toString().take(8)}"
        val fixture = BookSource(bookSourceUrl = sourceUrl, bookSourceName = originalName)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val helpPreference = suppressFirstRunRuleHelp(context)
        var fixtureInserted = false
        var scenario: ActivityScenario<BookSourceEditActivity>? = null
        var testFailure: Throwable? = null

        try {
            assertNull("Fixture URL unexpectedly exists", appDb.bookSourceDao.getBookSource(sourceUrl))
            appDb.bookSourceDao.insert(fixture)
            fixtureInserted = true

            val editorScenario = launchEditor(context, sourceUrl)
            scenario = editorScenario
            awaitSourceNameDisplayed(editorScenario, originalName, "source name editor")
            val backDescription = activityString(editorScenario, R.string.back)
            val exitMessage = activityString(editorScenario, R.string.exit_no_save)

            onView(sourceNameEditor(originalName)).perform(click())
            onView(allOf(isAssignableFrom(EditText::class.java), hasFocus())).perform(
                replaceText(draftName),
                closeSoftKeyboard()
            )
            awaitSourceNameDisplayed(editorScenario, draftName, "draft source name")

            composeRule.onNodeWithContentDescription(backDescription).assertIsDisplayed().performClick()
            assertDisplayed(withText(exitMessage))
            onView(withId(android.R.id.button1)).perform(click())
            assertConfirmationDismissed()
            assertEquals(originalName, appDb.bookSourceDao.getBookSource(sourceUrl)?.bookSourceName)

            composeRule.onNodeWithContentDescription(backDescription).performClick()
            assertDisplayed(withText(exitMessage))
            onView(withId(android.R.id.button2)).perform(click())
            await("editor activity exit") { editorScenario.state == Lifecycle.State.DESTROYED }
            assertEquals(originalName, appDb.bookSourceDao.getBookSource(sourceUrl)?.bookSourceName)
        } catch (error: Throwable) {
            testFailure = error
            throw error
        } finally {
            cleanupAfterTest(scenario, context, helpPreference, sourceUrl, fixtureInserted, testFailure)
        }
    }

    private fun cleanupAfterTest(
        scenario: ActivityScenario<BookSourceEditActivity>?,
        context: Context,
        helpPreference: HelpPreferenceSnapshot,
        sourceUrl: String,
        fixtureInserted: Boolean,
        testFailure: Throwable?
    ) {
        var cleanupFailure: Throwable? = null
        try {
            scenario?.close()
        } catch (error: Throwable) {
            cleanupFailure = error
        }
        try {
            if (fixtureInserted) appDb.bookSourceDao.delete(sourceUrl)
        } catch (error: Throwable) {
            if (cleanupFailure == null) cleanupFailure = error else cleanupFailure?.addSuppressed(error)
        }
        try {
            helpPreference.restore(context)
        } catch (error: Throwable) {
            if (cleanupFailure == null) cleanupFailure = error else cleanupFailure?.addSuppressed(error)
        }
        cleanupFailure?.let { error ->
            if (testFailure != null) testFailure.addSuppressed(error) else throw error
        }
    }

    private fun launchEditor(
        context: Context,
        sourceUrl: String
    ): ActivityScenario<BookSourceEditActivity> = ActivityScenario.launch<BookSourceEditActivity>(
        Intent(context, BookSourceEditActivity::class.java).putExtra("sourceUrl", sourceUrl)
    )

    private fun activityString(
        scenario: ActivityScenario<BookSourceEditActivity>,
        resourceId: Int
    ): String {
        lateinit var value: String
        scenario.onActivity { activity -> value = activity.getString(resourceId) }
        return value
    }

    private fun sourceNameEditor(text: String): Matcher<View> = allOf(
        isAssignableFrom(EditText::class.java),
        withText(text)
    )

    private fun awaitSourceNameDisplayed(
        scenario: ActivityScenario<BookSourceEditActivity>,
        expectedText: String,
        description: String
    ) {
        await(description) {
            var displayed = false
            scenario.onActivity { activity ->
                displayed = findSourceNameEditor(activity.window.decorView, expectedText)?.isShown == true
            }
            displayed
        }
    }

    private fun findSourceNameEditor(view: View, expectedText: String): CodeView? {
        if (view is CodeView && view.text.toString() == expectedText) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findSourceNameEditor(view.getChildAt(index), expectedText)?.let { return it }
            }
        }
        return null
    }

    private fun assertDisplayed(matcher: Matcher<View>) {
        onView(matcher).check(matches(isDisplayed()))
    }

    private fun assertConfirmationDismissed() {
        onView(withId(android.R.id.button1)).check(doesNotExist())
    }

    private fun await(description: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private fun uniqueSourceUrl(): String = "https://ui-test.invalid/source-${UUID.randomUUID()}"

    private fun suppressFirstRunRuleHelp(context: Context): HelpPreferenceSnapshot {
        val preferences = context.getSharedPreferences("local", Context.MODE_PRIVATE)
        val existed = preferences.contains(RULE_HELP_VERSION_KEY)
        val originalValue = preferences.getInt(RULE_HELP_VERSION_KEY, 0)
        check(preferences.edit().putInt(RULE_HELP_VERSION_KEY, 1).commit())
        return HelpPreferenceSnapshot(existed, originalValue)
    }

    private data class HelpPreferenceSnapshot(val existed: Boolean, val value: Int) {
        fun restore(context: Context) {
            val editor = context.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            if (existed) editor.putInt(RULE_HELP_VERSION_KEY, value) else editor.remove(RULE_HELP_VERSION_KEY)
            check(editor.commit())
        }
    }

    private companion object {
        const val RULE_HELP_VERSION_KEY = "ruleHelpVersion"
    }
}
