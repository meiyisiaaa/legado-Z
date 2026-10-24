package io.legado.app.ui.main.explore

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExploreContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun categoryLoadingFailureIsAnnouncedAndCanBeRetried() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = BookSourcePart(
            bookSourceUrl = "https://example.invalid",
            bookSourceName = "示例书源",
            bookSourceGroup = "测试",
            hasLoginUrl = false
        )
        val state = mutableStateOf(
            ExploreUiState(
                sources = listOf(source),
                expandedSourceUrl = source.bookSourceUrl,
                loadingSourceUrls = setOf(source.bookSourceUrl),
                isResumed = true
            )
        )
        var action: ExploreSourceAction? = null

        composeRule.setContent {
            LegadoTheme {
                ExploreContent(
                    state = state.value,
                    onToggleSource = {},
                    onSourceAction = { _, value -> action = value },
                    onOpenExplore = { _, _, _ -> },
                    onRetrySources = {},
                    onRetryGroups = {},
                    kindContent = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.onNodeWithText(source.bookSourceName)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                context.getString(R.string.accessibility_expanded)))

        composeRule.runOnIdle {
            state.value = state.value.copy(
                loadingSourceUrls = emptySet(),
                kindsBySource = mapOf(source.bookSourceUrl to emptyList()),
                kindErrorsBySource = mapOf(source.bookSourceUrl to "分类加载失败")
            )
        }

        composeRule.onNodeWithText("分类加载失败").assertIsDisplayed().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        composeRule.runOnIdle { assertEquals(ExploreSourceAction.Refresh, action) }
    }

    @Test
    fun sourceListLoadingEmptyAndErrorCanBeRetried() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = mutableStateOf(
            ExploreUiState(query = "no match", isLoadingSources = true)
        )
        var retryCount = 0

        composeRule.setContent {
            LegadoTheme {
                ExploreContent(
                    state = state.value,
                    onToggleSource = {},
                    onSourceAction = { _, _ -> },
                    onOpenExplore = { _, _, _ -> },
                    onRetrySources = { retryCount++ },
                    onRetryGroups = {},
                    kindContent = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.runOnIdle {
            state.value = state.value.copy(isLoadingSources = false)
        }
        composeRule.onNodeWithText(context.getString(R.string.empty))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.runOnIdle {
            state.value = state.value.copy(
                sourcesError = context.getString(R.string.error_get_data)
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.error_get_data))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun groupFlowLoadingAndErrorCanBeRetried() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = mutableStateOf(ExploreUiState(isLoadingGroups = true))
        var retryCount = 0

        composeRule.setContent {
            LegadoTheme {
                ExploreContent(
                    state = state.value,
                    onToggleSource = {},
                    onSourceAction = { _, _ -> },
                    onOpenExplore = { _, _, _ -> },
                    onRetrySources = {},
                    onRetryGroups = { retryCount++ },
                    kindContent = { _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.data_loading))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.runOnIdle {
            state.value = state.value.copy(
                isLoadingGroups = false,
                groupsError = context.getString(R.string.error_get_data)
            )
        }
        composeRule.onNodeWithText(context.getString(R.string.error_get_data))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun providerErrorKindRemainsReachableAlongsideRetry() {
        val source = BookSourcePart(
            bookSourceUrl = "https://example.invalid",
            bookSourceName = "示例书源",
            bookSourceGroup = "测试",
            hasLoginUrl = false
        )
        val errorKind = ExploreKind(title = "ERROR:provider failed", url = "provider stack trace")
        val state = ExploreUiState(
            sources = listOf(source),
            expandedSourceUrl = source.bookSourceUrl,
            kindsBySource = mapOf(source.bookSourceUrl to listOf(errorKind)),
            kindErrorsBySource = mapOf(source.bookSourceUrl to "provider failed"),
            isResumed = true
        )
        var action: ExploreSourceAction? = null
        var openedStackTrace: String? = null

        composeRule.setContent {
            LegadoTheme {
                ExploreContent(
                    state = state,
                    onToggleSource = {},
                    onSourceAction = { _, value -> action = value },
                    onOpenExplore = { _, _, _ -> },
                    onRetrySources = {},
                    onRetryGroups = {},
                    kindContent = { _, kinds, _, _ ->
                        kinds.forEach { kind ->
                            TextButton(onClick = { openedStackTrace = kind.url }) {
                                Text(kind.title)
                            }
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithText("provider failed").assertIsDisplayed()
        composeRule.onNodeWithText(errorKind.title).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(errorKind.url, openedStackTrace) }
        composeRule.onNodeWithText(
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.retry)
        ).performClick()
        composeRule.runOnIdle { assertEquals(ExploreSourceAction.Refresh, action) }
    }
}
