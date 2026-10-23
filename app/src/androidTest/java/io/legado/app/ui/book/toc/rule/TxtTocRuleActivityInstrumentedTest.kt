package io.legado.app.ui.book.toc.rule

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TxtTocRuleActivityInstrumentedTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun editingAndTogglingSameIdSurviveActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = UUID.randomUUID().toString()
        val rule = TxtTocRule(
            id = Long.MAX_VALUE - System.nanoTime(),
            name = "TXT-$token", rule = "第.+章", serialNumber = Int.MIN_VALUE
        )
        val changedName = "已编辑-$token"
        val changedRegex = "第[0-9]+节"
        var inserted = false
        var scenario: ActivityScenario<TxtTocRuleActivity>? = null
        try {
            runBlocking(Dispatchers.IO) {
                check(appDb.txtTocRuleDao.get(rule.id) == null) { "Fixture ID already exists" }
                appDb.txtTocRuleDao.insert(rule)
                inserted = true
            }
            scenario = ActivityScenario.launch<TxtTocRuleActivity>(Intent(context, TxtTocRuleActivity::class.java))
            waitForText(rule.name)
            composeRule.onNodeWithContentDescription("${rule.name}, ${context.getString(R.string.edit)}")
                .performClick()
            val nameField = hasSetTextAction() and hasText(context.getString(R.string.name))
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodes(nameField).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(nameField).performTextReplacement(changedName)
            composeRule.onNode(nameField).assertTextContains(changedName)
            val regexField = hasSetTextAction() and hasText(context.getString(R.string.regex))
            composeRule.onNode(regexField).performTextReplacement(changedRegex)
            composeRule.onNode(regexField).assertTextContains(changedRegex)
            composeRule.onNodeWithContentDescription(context.getString(R.string.action_save)).performClick()
            waitForText(changedName)
            composeRule.onNodeWithContentDescription("$changedName, ${context.getString(R.string.enable)}")
                .performClick()
            composeRule.waitUntil(10_000) {
                runBlocking(Dispatchers.IO) { appDb.txtTocRuleDao.get(rule.id)?.enable == false }
            }
            runBlocking(Dispatchers.IO) {
                val saved = checkNotNull(appDb.txtTocRuleDao.get(rule.id))
                assertEquals(changedName, saved.name)
                assertEquals(changedRegex, saved.rule)
            }
            scenario.recreate()
            waitForText(changedName)
            composeRule.onNodeWithText(changedName).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("$changedName, ${context.getString(R.string.enable)}")
                .assertIsOff()
        } finally {
            try {
                scenario?.close()
            } finally {
                if (inserted) runBlocking(Dispatchers.IO) { appDb.txtTocRuleDao.delete(rule) }
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
