package io.legado.app.ui.book.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderMenuContentSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun automaticBrightnessDescriptionReportsItsEnabledState() {
        val enabledDescription = context.getString(
            R.string.brightness_auto,
            context.getString(R.string.enabled)
        )
        val disabledDescription = context.getString(
            R.string.brightness_auto,
            context.getString(R.string.disabled)
        )
        var brightnessAuto by mutableStateOf(true)
        var toggledAutoMode: Boolean? = null

        compose.setContent {
            ReadMenuForTest(
                state = ReadMenuUiState(
                    brightnessAuto = brightnessAuto,
                    brightnessEnabled = !brightnessAuto,
                    animate = false
                ),
                onAction = { action, _ ->
                    if (action == ReaderMenuAction.ToggleBrightnessAuto) {
                        brightnessAuto = !brightnessAuto
                        toggledAutoMode = brightnessAuto
                    }
                }
            )
        }

        compose.onNodeWithContentDescription(enabledDescription).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(disabledDescription).assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, toggledAutoMode) }
    }

    @Test
    fun readingProgressSliderHasLocalizedNameAndAcceptsAccessibilityAdjustment() {
        var state by mutableStateOf(
            ReadMenuUiState(
                title = "测试书",
                progress = 2,
                maxProgress = 9,
                showBrightness = false,
                animate = false
            )
        )
        var previewedProgress: Int? = null
        var committedProgress: Int? = null
        var actionAccepted = false

        compose.setContent {
            ReadMenuForTest(
                state = state,
                onAction = { action, value ->
                    if (action == ReaderMenuAction.PreviewPage) {
                        previewedProgress = value
                        state = state.copy(progress = value)
                    } else if (action == ReaderMenuAction.SeekPage) {
                        committedProgress = value
                    }
                }
            )
        }

        val description = context.getString(R.string.progress_show, "测试书", 3, 10)
        compose.onNodeWithContentDescription(description)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(2f, 0f..9f)
                )
            )
            .performSemanticsAction(SemanticsActions.SetProgress) {
                actionAccepted = it(5f)
            }

        val updatedDescription = context.getString(R.string.progress_show, "测试书", 6, 10)
        compose.onNodeWithContentDescription(updatedDescription)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(5f, 0f..9f)
                )
            )
        compose.runOnIdle {
            assertTrue(actionAccepted)
            assertEquals(5, previewedProgress)
            assertEquals(5, committedProgress)
        }
    }

    @Test
    fun readingProgressSliderIsDisabledWhenThereIsOnlyOnePosition() {
        compose.setContent {
            ReadMenuForTest(
                state = ReadMenuUiState(
                    title = "测试书",
                    progress = 0,
                    maxProgress = 0,
                    showBrightness = false,
                    animate = false
                )
            )
        }

        val description = context.getString(R.string.progress_show, "测试书", 1, 1)
        compose.onNodeWithContentDescription(description)
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0f, 0f..0f)
                )
            )
    }

    @Test
    fun mangaProgressSliderUsesPageCountRangeAndAcceptsAccessibilityAdjustment() {
        var progress by mutableStateOf(2)
        val seekedPages = mutableListOf<Int>()
        var actionAccepted = false

        compose.setContent {
            LegadoTheme {
                MangaMenuContent(
                    visible = true,
                    animate = false,
                    title = "测试漫画",
                    chapterName = "",
                    chapterUrl = "",
                    sourceName = "",
                    showTitleBarAddition = false,
                    progress = progress,
                    pageCount = 31,
                    onAction = { action, value ->
                        if (action == ReaderMenuAction.SeekPage) {
                            seekedPages += value
                            progress = value
                        }
                    },
                    onClosed = {}
                )
            }
        }

        val description = context.getString(R.string.progress_show, "测试漫画", 3, 31)
        compose.onNodeWithContentDescription(description)
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(2f, 0f..30f)
                )
            )
            .performSemanticsAction(SemanticsActions.SetProgress) {
                actionAccepted = it(7f)
            }

        val updatedDescription = context.getString(R.string.progress_show, "测试漫画", 8, 31)
        compose.onNodeWithContentDescription(updatedDescription)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(7f, 0f..30f)
                )
            )
        compose.runOnIdle {
            assertTrue(actionAccepted)
            assertEquals(7, progress)
            assertEquals(listOf(7), seekedPages)
        }
    }

    @Test
    fun mangaProgressSliderIsDisabledWhenThereAreNoPages() {
        assertDisabledMangaProgressSlider(pageCount = 0, currentPageDescription = 0)
    }

    @Test
    fun mangaProgressSliderIsDisabledWhenThereIsOnlyOnePage() {
        assertDisabledMangaProgressSlider(pageCount = 1, currentPageDescription = 1)
    }

    @Test
    fun brightnessSliderHasLocalizedNameAndAcceptsAccessibilityAdjustment() {
        var changedBrightness: Int? = null
        var committedBrightness: Int? = null
        var actionAccepted = false

        compose.setContent {
            ReadMenuForTest(
                state = ReadMenuUiState(
                    brightness = 128,
                    brightnessAuto = false,
                    brightnessEnabled = true,
                    animate = false
                ),
                onBrightnessChange = { changedBrightness = it },
                onBrightnessFinished = { committedBrightness = it }
            )
        }

        compose.onNodeWithContentDescription(context.getString(R.string.brightness))
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(128f, 0f..255f)
                )
            )
            .performSemanticsAction(SemanticsActions.SetProgress) {
                actionAccepted = it(192f)
            }
        compose.runOnIdle {
            assertTrue(actionAccepted)
            assertEquals(192, changedBrightness)
            assertEquals(192, committedBrightness)
        }
    }

    @Test
    fun readMenuExposesNamedCloseActionAndKeepsBackdropTapDismissal() {
        var dismissed = 0
        val closeLabel = context.getString(R.string.close)

        compose.setContent {
            ReadMenuForTest(
                state = ReadMenuUiState(showBrightness = false, animate = false),
                onAction = { action, _ -> if (action == ReaderMenuAction.Dismiss) dismissed++ }
            )
        }

        assertPaneTitle(context.getString(R.string.menu))
        val closeAction = findCustomAction(closeLabel)
        assertTrue(closeAction.action())
        compose.runOnIdle { assertEquals(1, dismissed) }

        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val clickNodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertFalse(clickNodes.any { it.boundsInRoot == rootBounds })

        compose.onRoot().performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, dismissed) }
    }

    @Test
    fun mangaMenuExposesNamedCloseAction() {
        var dismissed = false
        val closeLabel = context.getString(R.string.close)

        compose.setContent {
            LegadoTheme {
                MangaMenuContent(
                    visible = true,
                    animate = false,
                    title = "测试漫画",
                    chapterName = "",
                    chapterUrl = "",
                    sourceName = "",
                    showTitleBarAddition = false,
                    progress = 0,
                    pageCount = 10,
                    onAction = { action, _ -> dismissed = action == ReaderMenuAction.Dismiss },
                    onClosed = {}
                )
            }
        }

        assertPaneTitle(context.getString(R.string.menu))
        assertTrue(findCustomAction(closeLabel).action())
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun searchMenuKeepsItsExplicitNamedExitAction() {
        var exitCount = 0

        compose.setContent {
            LegadoTheme {
                SearchMenuContent(
                    visible = true,
                    chapterTitle = "测试章节",
                    resultCount = 0,
                    hasResults = false,
                    onPrevious = {},
                    onNext = {},
                    onSearchResults = {},
                    onMainMenu = {},
                    onExit = { exitCount++ },
                    onClosed = {}
                )
            }
        }

        assertPaneTitle(context.getString(R.string.search_content))
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val clickNodes = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertFalse(clickNodes.any { it.boundsInRoot == rootBounds })

        compose.onRoot().performTouchInput { click(Offset(1f, 1f)) }
        compose.runOnIdle { assertEquals(1, exitCount) }

        compose.onNodeWithText("退出").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, exitCount) }
    }

    private fun findCustomAction(label: String) = compose.onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        useUnmergedTree = true
    ).fetchSemanticsNodes()
        .flatMap { it.config[SemanticsActions.CustomActions] }
        .single { it.label == label }

    private fun assertPaneTitle(title: String) {
        val matchingNodes = compose.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title),
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertEquals(1, matchingNodes.size)
    }

    private fun assertDisabledMangaProgressSlider(pageCount: Int, currentPageDescription: Int) {
        compose.setContent {
            LegadoTheme {
                MangaMenuContent(
                    visible = true,
                    animate = false,
                    title = "测试漫画",
                    chapterName = "",
                    chapterUrl = "",
                    sourceName = "",
                    showTitleBarAddition = false,
                    progress = 0,
                    pageCount = pageCount,
                    onAction = { _, _ -> error("不可切页时不应触发进度操作") },
                    onClosed = {}
                )
            }
        }

        val description = context.getString(
            R.string.progress_show,
            "测试漫画",
            currentPageDescription,
            pageCount
        )
        compose.onNodeWithContentDescription(description)
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0f, 0f..0f)
                )
            )
    }
}

@Composable
private fun ReadMenuForTest(
    state: ReadMenuUiState,
    onAction: (ReaderMenuAction, Int) -> Unit = { _, _ -> },
    onBrightnessChange: (Int) -> Unit = {},
    onBrightnessFinished: (Int) -> Unit = {}
) {
    LegadoTheme {
        ReadMenuContent(
            state = state,
            visible = true,
            onAction = onAction,
            onBrightnessChange = onBrightnessChange,
            onBrightnessFinished = onBrightnessFinished,
            onClosed = {},
            onHelp = {},
            onOpenUrlPreference = {}
        )
    }
}
