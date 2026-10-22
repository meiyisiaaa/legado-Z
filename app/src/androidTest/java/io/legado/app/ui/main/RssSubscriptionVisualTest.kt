package io.legado.app.ui.main

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.main.rss.RssSubscriptionGrid
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoTopAppBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Plain UI fixtures keep the RSS database, Fragment and network outside visual tests. */
class RssSubscriptionVisualTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.let { target ->
        target.createConfigurationContext(Configuration(target.resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        })
    }
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val sources = listOf(
        RssSource(sourceUrl = "visual://letters", sourceName = "远方来信", loginUrl = "visual://login"),
        RssSource(sourceUrl = "visual://library", sourceName = "在漫长的旅途中寻找一座没有名字的图书馆与那些未曾寄出的信"),
        RssSource(sourceUrl = "visual://radio", sourceName = "夜航电台"),
        RssSource(sourceUrl = "visual://paper", sourceName = "纸间拾光"),
        RssSource(sourceUrl = "visual://mountain", sourceName = "山野观察手记")
    )

    private fun render(entries: List<RssSource> = sources, width: Dp = 10_000.dp,
                       fontScale: Float? = null, onRuleSubscription: () -> Unit = {},
                       onAction: (String, RssSource) -> Unit = { _, _ -> }) {
        val localizedContext = context
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localizedContext,
                LocalDensity provides Density(density.density, fontScale
                    ?: arguments.getString("visualFontScale")?.toFloat() ?: density.fontScale)) {
                LegadoTheme {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = width).fillMaxSize()) {
                            LegadoTopAppBar(title = { Text(context.getString(R.string.rss)) }, actions = {
                                IconButton(onClick = {}) {
                                    Icon(painterResource(R.drawable.ic_search), "搜索")
                                }
                                IconButton(onClick = {}) {
                                    Icon(painterResource(R.drawable.ic_more_vert), "更多")
                                }
                            })
                            RssSubscriptionGrid(entries, onRuleSubscription,
                                { onAction("open", it) }, { onAction("edit", it) },
                                { onAction("top", it) }, { onAction("login", it) },
                                { onAction("delete", it) }, { onAction("disable", it) },
                                modifier = Modifier.weight(1f)) {
                                Image(painterResource(R.drawable.image_rss), null, Modifier.fillMaxSize())
                            }
                            MainNavigation(listOf(0, 1, 2, 3), 2, 0, {})
                        }
                    }
                }
            }
        }
    }

    private fun record(state: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(3_500, 10_000)
        val directory = File(context.getExternalFilesDir(null), "rss-visual").apply { mkdirs() }
        val file = File(directory, "${arguments.getString("visualPrefix") ?: "current"}-$state.png")
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        assertTrue(file.length() > 0)
    }

    @Test fun normalSourcesAndActions() {
        var ruleOpens = 0
        val actions = mutableListOf<Pair<String, RssSource>>()
        render(onRuleSubscription = { ruleOpens++ }, onAction = { action, source ->
            actions += action to source
        })
        record("normal")
        compose.onNodeWithText(context.getString(R.string.rule_subscription))
            .assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, ruleOpens)
        compose.onNodeWithText(sources[0].sourceName).performClick()
        assertEquals("open" to sources[0], actions.single())
        listOf(R.string.edit to "edit", R.string.to_top to "top", R.string.login to "login",
            R.string.disable_source to "disable", R.string.delete to "delete").forEachIndexed { index, (label, action) ->
            compose.onNodeWithText(sources[0].sourceName)
                .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
            if (index == 0) record("menu")
            compose.onNodeWithText(context.getString(label)).performClick()
            assertEquals(action to sources[0], actions.last())
            compose.onNodeWithText(context.getString(label)).assertDoesNotExist()
        }
        assertEquals(6, actions.size)
    }

    @Test fun sourceWithoutLoginOmitsLoginAction() {
        render()
        compose.onNodeWithText(sources[2].sourceName)
            .performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText(context.getString(R.string.edit)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.login)).assertDoesNotExist()
    }

    @Test fun narrowSourcesWithLargeType() {
        render(width = 320.dp, fontScale = 1.3f)
        compose.onNodeWithText(context.getString(R.string.rule_subscription))
            .assertWidthIsAtLeast(280.dp).assertHeightIsAtLeast(56.dp)
        val first = compose.onNodeWithText(sources[0].sourceName).assertIsDisplayed()
            .assertWidthIsAtLeast(104.dp * 1.3f).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText(sources[1].sourceName).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText(sources[2].sourceName).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(first.right <= second.left)
        assertTrue(third.top >= first.bottom)
        compose.onNodeWithText(sources[1].sourceName, useUnmergedTree = true)
            .assertTextEquals(sources[1].sourceName)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                val layout = layouts.single()
                assertEquals(2, layout.lineCount)
                // didOverflowHeight includes intentional maxLines truncation; check the rendered line box.
                assertTrue(layout.getLineBottom(layout.lineCount - 1) <= layout.size.height + 1f)
                assertTrue(layout.isLineEllipsized(layout.lineCount - 1))
            }
        record("narrow-large-type")
    }

    @Test fun emptySourcesKeepRuleEntry() {
        var opens = 0
        render(entries = emptyList(), onRuleSubscription = { opens++ })
        compose.onNodeWithText(context.getString(R.string.rss_source_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.rule_subscription)).performClick()
        assertEquals(1, opens)
        record("empty")
    }
}
