package io.legado.app.ui.rss.read

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.widget.TitleBar
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReadRssTitleBarInstrumentedTest {

    @Test
    fun localHtmlSurvivesActivityRecreationAndConfigurationChange() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val token = UUID.randomUUID().toString()
        val sourceUrl = "https://read-rss-$token.invalid/"
        val marker = "READ_RSS_LOCAL_HTML_$token"
        val source = RssSource(sourceUrl = sourceUrl, sourceName = "本地 RSS 回归 $token")
        val html = "<!doctype html><html><head><title>local test</title></head><body><p>$marker</p></body></html>"
        var scenario: ActivityScenario<ReadRssActivity>? = null

        withContext(IO) {
            assertNull("Fixture RSS source already exists", appDb.rssSourceDao.getByKey(sourceUrl))
            appDb.rssSourceDao.insert(source)
        }
        try {
            val intent = Intent(context, ReadRssActivity::class.java)
                .putExtra("origin", sourceUrl)
                .putExtra("title", source.sourceName)
                .putExtra("startHtml", html)
            val activityScenario = ActivityScenario.launch<ReadRssActivity>(intent)
            scenario = activityScenario
            assertBodyContains(activityScenario, marker)
            activityScenario.onActivity { activity ->
                assertTrue(
                    "ReadRssActivity title_bar should remain a ComposeView",
                    activity.findViewById<View>(R.id.title_bar) is ComposeView
                )
            }

            activityScenario.recreate()
            assertBodyContains(activityScenario, marker)
            activityScenario.onActivity { activity ->
                val nextOrientation = if (activity.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT
                else Configuration.ORIENTATION_LANDSCAPE
                activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply {
                    orientation = nextOrientation
                })
            }
            assertBodyContains(activityScenario, marker)
        } finally {
            try {
                scenario?.close()
            } finally {
                withContext(IO) { appDb.rssSourceDao.delete(sourceUrl) }
            }
        }
    }

    @Test
    fun sharedBaseStillAcceptsNativeActivityAndComposeFragmentTitleBars() {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(
                    "AboutActivity should retain its native TitleBar",
                    activity.findViewById<View>(R.id.title_bar) is TitleBar
                )
                activity.supportFragmentManager.commitNow {
                    replace(R.id.fl_fragment, ComposeTitleBarFragment(), "compose-title-bar")
                }
                val fragment = activity.supportFragmentManager
                    .findFragmentByTag("compose-title-bar") as ComposeTitleBarFragment
                val composeTitleBar = fragment.requireView().findViewById<View>(R.id.title_bar)
                assertTrue("The test fragment should expose a ComposeView title_bar", composeTitleBar is ComposeView)

                fragment.onMultiWindowModeChanged(activity.isInMultiWindow)
                val nextOrientation = if (activity.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT
                else Configuration.ORIENTATION_LANDSCAPE
                activity.onConfigurationChanged(Configuration(activity.resources.configuration).apply {
                    orientation = nextOrientation
                })

                assertSame(composeTitleBar, fragment.requireView().findViewById<View>(R.id.title_bar))
                assertTrue(activity.findViewById<View>(R.id.title_bar) is TitleBar)
            }
        }
    }

    private fun assertBodyContains(
        scenario: ActivityScenario<ReadRssActivity>,
        marker: String
    ) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val body = AtomicReference<String?>()
            val completed = CountDownLatch(1)
            scenario.onActivity { activity ->
                val container = activity.findViewById<ViewGroup>(R.id.web_view_container)
                val webView = (0 until container.childCount)
                    .mapNotNull { container.getChildAt(it) as? WebView }
                    .firstOrNull()
                if (webView == null) {
                    completed.countDown()
                } else {
                    webView.evaluateJavascript("document.body.innerText") {
                        body.set(it)
                        completed.countDown()
                    }
                }
            }
            if (completed.await(1, TimeUnit.SECONDS) && body.get()?.contains(marker) == true) return
            SystemClock.sleep(100)
        }
        throw AssertionError("ReadRssActivity did not render local HTML marker: $marker")
    }

    class ComposeTitleBarFragment : BaseFragment(R.layout.activity_rss_read) {
        override fun onFragmentCreated(view: View, savedInstanceState: android.os.Bundle?) = Unit
    }
}
