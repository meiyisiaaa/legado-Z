package io.legado.app.ui.config.compose

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.lib.prefs.SwitchPreference
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class ComposePreferenceScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingTwoStateRowUsesPreferenceClickAndChangeListeners() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        var preferenceClickCount = 0
        var proposedValue: Any? = null
        // API 26's styled-attribute reader requires a compiled XmlResourceParser.
        val attributes = context.resources.getXml(R.xml.pref_config_other).apply {
            while (next() != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && name == SwitchPreference::class.java.name) break
            }
            check(eventType == XmlPullParser.START_TAG && name == SwitchPreference::class.java.name)
        }
        val preference = SwitchPreference(context, attributes).apply {
            key = "compose.preference.test"
            title = "测试开关"
            isPersistent = false
            setOnPreferenceClickListener {
                preferenceClickCount++
                true
            }
            setOnPreferenceChangeListener { _, newValue ->
                proposedValue = newValue
                true
            }
        }
        attributes.close()
        screen.addPreference(preference)

        composeRule.setContent {
            LegadoTheme {
                ComposePreferenceScreen(screen)
            }
        }

        composeRule.onNodeWithText("测试开关").performClick()

        assertTrue(preference.isChecked)
        assertEquals(true, proposedValue)
        assertEquals(1, preferenceClickCount)
    }

    @Test
    fun revisionRefreshesMutablePreferenceSummaryAndContentDescription() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        val newSummary = "/storage/emulated/0/Download"
        val preference = Preference(context).apply {
            key = "compose.preference.summary"
            title = "备份路径"
            summary = "选择备份路径"
            isPersistent = false
        }
        screen.addPreference(preference)
        val revision = mutableIntStateOf(0)

        composeRule.setContent {
            LegadoTheme {
                ComposePreferenceScreen(screen, revision = revision.intValue)
            }
        }

        composeRule.onNodeWithText("选择备份路径").assertExists()
        composeRule.onNodeWithContentDescription("备份路径. 选择备份路径").assertExists()

        composeRule.runOnIdle {
            preference.summary = newSummary
            revision.intValue++
        }

        composeRule.onNodeWithText(newSummary).assertExists()
        composeRule.onNodeWithContentDescription("备份路径. $newSummary").assertExists()
    }
}
