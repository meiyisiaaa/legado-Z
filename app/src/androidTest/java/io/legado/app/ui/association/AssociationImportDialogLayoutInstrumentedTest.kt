package io.legado.app.ui.association

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AssociationImportDialogLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun largeFontLongImportKeepsXmlFooterVisibleAndReachable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previousFontScale = executeShellCommand("settings get system font_scale").trim()
        check(previousFontScale.toFloatOrNull() != null) {
            "Could not read the device font scale: $previousFontScale"
        }

        val token = UUID.randomUUID().toString().replace("-", "")
        val sourceNames = (1..SOURCE_COUNT).map { index -> "Import layout $token source $index" }
        val sourceJson = GSON.toJson(
            sourceNames.mapIndexed { index, name ->
                BookSource(
                    bookSourceUrl = "https://import-layout-$token-${index + 1}.example/",
                    bookSourceName = name,
                    enabled = false,
                    enabledExplore = false
                )
            }
        )
        val groupName = "QA group"
        var scenario: ActivityScenario<MainActivity>? = null
        var importDialog: ImportBookSourceDialog? = null
        var failure: Throwable? = null
        fun recordFailure(error: Throwable) {
            val prior = failure
            if (prior == null) failure = error else prior.addSuppressed(error)
        }

        try {
            executeShellCommand("settings put system font_scale 2.0")
            assertEquals("2.0", executeShellCommand("settings get system font_scale").trim())

            val activeScenario = ActivityScenario.launch<MainActivity>(
                Intent(instrumentation.targetContext, MainActivity::class.java)
            )
            scenario = activeScenario
            var expectedFooterText = ""
            var expectedPartialFooterText = ""
            activeScenario.onActivity { activity ->
                assertEquals(2f, activity.resources.configuration.fontScale, 0.001f)
                expectedFooterText = activity.getString(
                    R.string.select_cancel_count,
                    SOURCE_COUNT,
                    SOURCE_COUNT
                )
                expectedPartialFooterText = activity.getString(
                    R.string.select_all_count,
                    SOURCE_COUNT - 1,
                    SOURCE_COUNT
                )
                importDialog = ImportBookSourceDialog(sourceJson).also {
                    it.show(activity.supportFragmentManager, IMPORT_DIALOG_TAG)
                }
            }

            composeRule.waitUntil(timeoutMillis = IMPORT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(sourceNames.first(), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(sourceNames.first(), useUnmergedTree = true).assertIsDisplayed()
            onView(withId(R.id.tv_footer_left)).check(matches(withText(expectedFooterText)))
            val initialDialog = checkNotNull(importDialog)
            var initialViewModel: ImportBookSourceViewModel? = null
            activeScenario.onActivity {
                initialViewModel = ViewModelProvider(initialDialog)[ImportBookSourceViewModel::class.java]
            }
            val importedViewModel = checkNotNull(initialViewModel)
            assertEquals(SOURCE_COUNT, importedViewModel.allSources.size)
            assertEquals(sourceNames, importedViewModel.allSources.map { it.bookSourceName })
            assertEquals(SOURCE_COUNT, importedViewModel.checkSources.size)
            assertEquals(SOURCE_COUNT, importedViewModel.selectStatus.size)

            composeRule.onNodeWithText(sourceNames.first())
                .assertIsOn()
                .performClick()
                .assertIsOff()
            assertFalse(importedViewModel.selectStatus.first())
            assertEquals(SOURCE_COUNT - 1, importedViewModel.selectCount)

            onView(withId(R.id.menu_new_group)).perform(click())
            onView(withId(R.id.edit_view)).perform(
                replaceText(groupName),
                closeSoftKeyboard()
            )
            onView(withId(R.id.sw_add_group)).perform(click()).check(matches(isChecked()))
            onView(withId(android.R.id.button1)).perform(click())

            var expectedGroupTitle = ""
            activeScenario.onActivity { activity ->
                expectedGroupTitle = "+" + activity.getString(
                    R.string.diy_edit_source_group_title,
                    groupName
                )
            }

            activeScenario.recreate()
            composeRule.waitUntil(timeoutMillis = IMPORT_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(sourceNames.first(), useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            var restoredDialog: ImportBookSourceDialog? = null
            activeScenario.onActivity { activity ->
                restoredDialog = findImportDialog(activity, sourceJson)
            }
            val recreatedDialog = checkNotNull(restoredDialog) {
                "Recreated import dialog with the fixture source was not found"
            }
            var restoredViewModel: ImportBookSourceViewModel? = null
            activeScenario.onActivity {
                restoredViewModel = ViewModelProvider(recreatedDialog)[ImportBookSourceViewModel::class.java]
            }
            val viewModel = checkNotNull(restoredViewModel)
            assertEquals(SOURCE_COUNT, viewModel.allSources.size)
            assertEquals(sourceNames, viewModel.allSources.map { it.bookSourceName })
            assertEquals(SOURCE_COUNT, viewModel.checkSources.size)
            assertEquals(SOURCE_COUNT, viewModel.selectStatus.size)
            assertEquals(SOURCE_COUNT - 1, viewModel.selectCount)
            assertFalse(viewModel.selectStatus.first())
            assertEquals(groupName, viewModel.groupName)
            assertTrue(viewModel.isAddGroup)
            composeRule.onNodeWithText(sourceNames.first()).assertIsOff()
            onView(withId(R.id.menu_new_group)).check(matches(withText(expectedGroupTitle)))

            onView(withId(R.id.menu_new_group)).perform(click())
            onView(withId(R.id.edit_view)).check(matches(withText(groupName)))
            onView(withId(R.id.sw_add_group)).check(matches(isChecked()))
            onView(withId(R.id.edit_view)).perform(closeSoftKeyboard())
            onView(withId(android.R.id.button2)).perform(click())
            onView(withId(R.id.edit_view)).check(doesNotExist())

            composeRule.onNode(
                hasScrollToIndexAction().and(hasAnyDescendant(hasText(sourceNames.first()))),
                useUnmergedTree = true
            )
                .performSemanticsAction(SemanticsActions.ScrollToIndex) { scrollToIndex ->
                    scrollToIndex(SOURCE_COUNT - 1)
                }
            composeRule.onNodeWithText(sourceNames.last(), useUnmergedTree = true).assertIsDisplayed()
            onView(withId(R.id.tv_footer_left))
                .check(matches(withText(expectedPartialFooterText)))
                .check(matches(isCompletelyDisplayed()))
                .check(matches(isClickable()))

            onView(withId(R.id.tv_cancel))
                .check(matches(isCompletelyDisplayed()))
                .check(matches(isClickable()))
            onView(withId(R.id.tv_ok))
                .check(matches(isCompletelyDisplayed()))
                .check(matches(isClickable()))
            onView(withId(R.id.tv_cancel)).perform(click())
            composeRule.waitUntil(timeoutMillis = IMPORT_TIMEOUT_MS) {
                var dialogAdded = false
                activeScenario.onActivity { activity ->
                    dialogAdded = findImportDialog(activity, sourceJson)?.isAdded == true
                }
                !dialogAdded
            }
            val importedSourcesInDatabase = runBlocking {
                withContext(Dispatchers.IO) {
                    sourceNames.indices.map { index ->
                        appDb.bookSourceDao.getBookSource(
                            "https://import-layout-$token-${index + 1}.example/"
                        )
                    }
                }
            }
            assertTrue(
                "Canceling import must not persist any fixture source",
                importedSourcesInDatabase.all { it == null }
            )
        } catch (error: Throwable) {
            recordFailure(error)
        } finally {
            try {
                scenario?.onActivity { activity ->
                    findImportDialog(activity, sourceJson)?.dismissAllowingStateLoss()
                }
                scenario?.close()
            } catch (error: Throwable) {
                recordFailure(error)
            }
            try {
                executeShellCommand("settings put system font_scale $previousFontScale")
                assertEquals(
                    previousFontScale,
                    executeShellCommand("settings get system font_scale").trim()
                )
            } catch (error: Throwable) {
                recordFailure(error)
            }
        }
        failure?.let { throw it }
    }

    private fun hasScrollToIndexAction(): SemanticsMatcher =
        SemanticsMatcher("Lazy list supports indexed scrolling") { node ->
            node.config.contains(SemanticsActions.ScrollToIndex)
        }

    private fun findImportDialog(
        activity: MainActivity,
        sourceJson: String
    ): ImportBookSourceDialog? = activity.supportFragmentManager.fragments
        .filterIsInstance<ImportBookSourceDialog>()
        .singleOrNull { it.arguments?.getString("source") == sourceJson }

    private fun executeShellCommand(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText().trim() }
    }

    private companion object {
        const val IMPORT_DIALOG_TAG = "association-import-layout"
        const val SOURCE_COUNT = 30
        const val IMPORT_TIMEOUT_MS = 20_000L
    }
}
