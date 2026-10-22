package io.legado.app.ui.main

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.ViewPager
import io.legado.app.R
import io.legado.app.help.config.LocalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run with TalkBack enabled; ordinary semantics tests do not prove the platform bridge. */
class TalkBackNavigationInstrumentedTest {
    @Test fun mainDestinationsSwitchDirectlyWithoutTalkBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val privacyWasPresent = LocalConfig.contains("privacyPolicyOk")
        val privacyWasAccepted = LocalConfig.privacyPolicyOk
        LocalConfig.privacyPolicyOk = true
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val scrollStates = mutableListOf<Int>()
                scenario.onActivity { activity ->
                    activity.findViewById<ViewPager>(R.id.view_pager_main).addOnPageChangeListener(
                        object : ViewPager.SimpleOnPageChangeListener() {
                            override fun onPageScrollStateChanged(state: Int) {
                                scrollStates += state
                            }
                        }
                    )
                }
                val labels = listOf(R.string.bookshelf, R.string.discovery, R.string.rss, R.string.my)
                    .map(context::getString)
                val selectedIndex = labels.indexOfFirst { controlGroup(awaitNode(automation, it))?.isSelected == true }
                assertTrue("No selected main destination", selectedIndex >= 0)
                val destinations = labels.drop(selectedIndex + 1) + labels.take(selectedIndex + 1)
                for (label in destinations) {
                    val target = requireNotNull(controlGroup(awaitNode(automation, label)))
                    assertFalse("Destination must be unselected before activation: $label", target.isSelected)
                    scenario.onActivity { scrollStates.clear() }
                    assertTrue("Cannot activate $label", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    await("Selected state was not exposed for $label") {
                        controlGroup(findNode(automation.rootInActiveWindow, label))?.isSelected == true
                    }
                    scenario.onActivity {
                        assertFalse("Tab activation must not scroll across intermediate pages: $label",
                            scrollStates.contains(ViewPager.SCROLL_STATE_SETTLING))
                    }
                }
            }
        } finally {
            if (privacyWasPresent) LocalConfig.privacyPolicyOk = privacyWasAccepted
            else LocalConfig.edit().remove("privacyPolicyOk").apply()
        }
    }

    @Test fun realServiceCanFocusAndActivateEachMainDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        assumeTrue("Enable TalkBack for this device acceptance test", manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" })

        val privacyWasPresent = LocalConfig.contains("privacyPolicyOk")
        val privacyWasAccepted = LocalConfig.privacyPolicyOk
        LocalConfig.privacyPolicyOk = true
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val scrollStates = mutableListOf<Int>()
                scenario.onActivity { activity ->
                    activity.findViewById<ViewPager>(R.id.view_pager_main).addOnPageChangeListener(
                        object : ViewPager.SimpleOnPageChangeListener() {
                            override fun onPageScrollStateChanged(state: Int) {
                                scrollStates += state
                            }
                        }
                    )
                }
                assertTrue("TalkBack was suppressed while launching the Activity", manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
                    .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" })
                assertTrue("TalkBack touch exploration is not active", manager.isTouchExplorationEnabled)
                val labels = listOf(R.string.bookshelf, R.string.discovery, R.string.rss, R.string.my)
                    .map(context::getString)
                val selectedIndex = labels.indexOfFirst { controlGroup(awaitNode(automation, it))?.isSelected == true }
                assertTrue("No selected main destination", selectedIndex >= 0)
                val destinations = labels.drop(selectedIndex + 1) + labels.take(selectedIndex + 1)
                for (label in destinations) {
                    val target = requireNotNull(controlGroup(awaitNode(automation, label))) {
                        "No focusable named navigation group: $label"
                    }
                    assertFalse("Destination must be unselected before activation: $label", target.isSelected)
                    assertTrue("Cannot accessibility-focus $label", target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                    await("Accessibility focus did not reach $label") {
                        findNode(automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY), label) != null
                    }
                    scenario.onActivity { scrollStates.clear() }
                    assertTrue("Cannot activate $label", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    await("Selected state was not exposed for $label") {
                        controlGroup(findNode(automation.rootInActiveWindow, label))?.isSelected == true
                    }
                    scenario.onActivity {
                        assertFalse("Tab activation must not scroll across intermediate pages: $label",
                            scrollStates.contains(ViewPager.SCROLL_STATE_SETTLING))
                    }
                }
            }
        } finally {
            if (privacyWasPresent) LocalConfig.privacyPolicyOk = privacyWasAccepted
            else LocalConfig.edit().remove("privacyPolicyOk").apply()
        }
    }

    private fun awaitNode(automation: UiAutomation, label: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        try {
            await("No named navigation node: $label") { findNode(automation.rootInActiveWindow, label).also { found = it } != null }
        } catch (failure: AssertionError) {
            val nodes = mutableListOf<String>()
            fun describe(node: AccessibilityNodeInfo?) {
                if (node == null || nodes.size >= 80) return
                if (node.text != null || node.contentDescription != null || node.isClickable) {
                    nodes += "${node.packageName}/${node.className}: text=${node.text}, description=${node.contentDescription}, clickable=${node.isClickable}, visible=${node.isVisibleToUser}"
                }
                for (index in 0 until node.childCount) describe(node.getChild(index))
            }
            describe(automation.rootInActiveWindow)
            throw AssertionError("${failure.message}\nActive window:\n${nodes.joinToString("\n")}", failure)
        }
        return requireNotNull(found)
    }

    private fun findNode(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString() == label) return node
        for (index in 0 until node.childCount) findNode(node.getChild(index), label)?.let { return it }
        return null
    }

    private fun controlGroup(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            // Selected tabs retain focus and state while the platform removes their click action.
            if (current.isFocusable) return current
            current = current.parent
        }
        return null
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }
}
