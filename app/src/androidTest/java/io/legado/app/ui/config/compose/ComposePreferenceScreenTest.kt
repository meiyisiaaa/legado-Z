package io.legado.app.ui.config.compose

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.lib.prefs.SeekBarPreference
import io.legado.app.lib.prefs.SwitchPreference
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
    fun groupedSettingsKeepHeadingOrderAndPreferenceActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        var clickCount = 0
        val readingGroup = PreferenceCategory(context).apply { title = "阅读设置" }
        val dataGroup = PreferenceCategory(context).apply { title = "数据管理" }
        screen.addPreference(readingGroup)
        screen.addPreference(dataGroup)
        readingGroup.addPreference(Preference(context).apply {
            title = "阅读偏好"
            isPersistent = false
            setOnPreferenceClickListener {
                clickCount++
                true
            }
        })
        dataGroup.addPreference(Preference(context).apply {
            title = "不可用的备份"
            isPersistent = false
            isEnabled = false
            setOnPreferenceClickListener {
                clickCount++
                true
            }
        })
        val hiddenGroup = PreferenceCategory(context).apply {
            title = "隐藏分组"
            isVisible = false
        }
        screen.addPreference(hiddenGroup)
        hiddenGroup.addPreference(Preference(context).apply { title = "隐藏入口" })

        composeRule.setContent {
            LegadoTheme { ComposePreferenceScreen(screen) }
        }

        val readingHeading = composeRule.onNodeWithText("阅读设置")
        val dataHeading = composeRule.onNodeWithText("数据管理")
        readingHeading.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertHasNoClickAction()
        dataHeading.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertHasNoClickAction()
        val readingRow = composeRule.onNodeWithText("阅读偏好")
        val dataRow = composeRule.onNodeWithText("不可用的备份").assertIsNotEnabled()
        assertTrue(readingHeading.fetchSemanticsNode().boundsInRoot.bottom <=
            readingRow.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(readingRow.fetchSemanticsNode().boundsInRoot.bottom <=
            dataHeading.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(dataHeading.fetchSemanticsNode().boundsInRoot.bottom <=
            dataRow.fetchSemanticsNode().boundsInRoot.top)
        composeRule.onNodeWithText("隐藏分组").assertDoesNotExist()
        composeRule.onNodeWithText("隐藏入口").assertDoesNotExist()
        readingRow.performClick()
        assertEquals(1, clickCount)
        dataRow.performClick()
        assertEquals(1, clickCount)
    }

    @Test
    fun myXmlKeepsOriginalKeysOrderAndDefaultsWithinFourGroups() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val groups = mutableListOf<Int>()
        val groupedKeys = mutableListOf<MutableList<String>>()
        val defaults = mutableMapOf<String, String>()
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        context.resources.getXml(R.xml.pref_main).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.depth == 2) {
                    assertEquals("io.legado.app.lib.prefs.PreferenceCategory", parser.name)
                    groups += parser.getAttributeResourceValue(androidNamespace, "title", 0)
                    groupedKeys.add(mutableListOf())
                } else if (parser.depth == 3) {
                    val key = parser.getAttributeValue(androidNamespace, "key")
                    groupedKeys.last().add(key)
                    parser.getAttributeValue(androidNamespace, "defaultValue")?.let {
                        defaults[key] = it
                    }
                }
            }
        }
        assertEquals(listOf(
            R.string.my_content_and_rules, R.string.my_appearance_and_services,
            R.string.setting, R.string.other
        ), groups)
        assertEquals(listOf(
            listOf("bookSourceManage", "txtTocRuleManage", "replaceManage", "dictRuleManage"),
            listOf("themeMode", "webService"),
            listOf("web_dav_setting", "theme_setting", "setting"),
            listOf("bookmark", "readRecord", "fileManage", "about", "exit")
        ), groupedKeys)
        assertEquals(mapOf("themeMode" to "0", "webService" to "false"), defaults)
    }

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

    @Test
    fun seekBarAccessibilityUsesThePreferenceTitle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = PreferenceManager(context)
        val screen = manager.createPreferenceScreen(context)
        val attributes = context.resources.getXml(R.xml.pref_config_welcome).apply {
            while (next() != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && name == SeekBarPreference::class.java.name) break
            }
            check(eventType == XmlPullParser.START_TAG && name == SeekBarPreference::class.java.name)
        }
        val preference = SeekBarPreference(context, attributes).apply {
            key = "compose.preference.seekbar"
            title = "滑块值"
            isPersistent = false
        }
        attributes.close()
        screen.addPreference(preference)

        composeRule.setContent {
            LegadoTheme {
                ComposePreferenceScreen(screen)
            }
        }

        composeRule.onNodeWithContentDescription("滑块值").assertExists()
    }
}
