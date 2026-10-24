package io.legado.app.ui.file

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileManageActivityInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<FileManageActivity>()

    @Test
    fun parentEntryAndBreadcrumbStayInSyncWhileNavigatingExternalCache() {
        val activity = composeRule.activity
        val cacheDir = checkNotNull(activity.externalCacheDir)
        val fixtureDir = File(cacheDir, "file-manage-${UUID.randomUUID()}")
        assertTrue("Could not create private file-manager fixture", fixtureDir.mkdir())

        try {
            composeRule.onNodeWithContentDescription(activity.getString(R.string.search))
                .assertIsDisplayed()

            waitForText("cache")
            composeRule.onNodeWithText("cache").performClick()
            waitForText(fixtureDir.name)
            composeRule.onNodeWithText(fixtureDir.name).performClick()

            waitForText("..")
            composeRule.onNodeWithText(fixtureDir.name).assertIsDisplayed()
            composeRule.onNodeWithText("..").assertIsDisplayed()

            composeRule.activityRule.scenario.recreate()
            waitForText(fixtureDir.name)
            waitForText("..")
            composeRule.onNodeWithText("root").assertIsDisplayed()
            composeRule.onNodeWithText("cache").assertIsDisplayed()
            composeRule.onNodeWithText("..").assertIsDisplayed()

            composeRule.onNodeWithText("..").performClick()

            waitForText(fixtureDir.name)
            composeRule.onNodeWithText("root").assertIsDisplayed()
            composeRule.onNodeWithText("cache").assertIsDisplayed()
            composeRule.onNodeWithText(fixtureDir.name).assertIsDisplayed()

            composeRule.onNodeWithText("root").performClick()
            waitForText("cache")
            composeRule.onNodeWithText("root").assertIsDisplayed()
            composeRule.onNodeWithText("cache").assertIsDisplayed()
            composeRule.onNodeWithText("..").assertDoesNotExist()
        } finally {
            fixtureDir.deleteRecursively()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
