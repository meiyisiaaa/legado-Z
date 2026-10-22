package io.legado.app.ui.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AboutContentSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun identityAndSectionsExposeHeadingsAndTheRuntimeVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LegadoTheme {
                AboutContent(AboutUiState(versionName = "test-version"), onAction = {})
            }
        }

        compose.onNodeWithText(context.getString(R.string.about_product_name))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.version_name, "test-version"))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.about_project_summary))
            .assertIsDisplayed()

        listOf(
            R.string.about_product_name,
            R.string.about_project_section,
            R.string.about_diagnostics_section,
            R.string.about_legal_section
        ).forEach { headingResource ->
            val heading = context.getString(headingResource)
            compose.onNodeWithTag(ABOUT_CONTENT_LIST_TEST_TAG)
                .performScrollToNode(hasText(heading))
            val node = compose.onNodeWithText(heading).fetchSemanticsNode()
            assertTrue("$heading should be exposed as a heading", node.config.contains(SemanticsProperties.Heading))
        }
    }

    @Test
    fun everyAboutEntryHasAReadableNameAndClickAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selectedAction: AboutAction? = null
        compose.setContent {
            LegadoTheme {
                AboutContent(
                    state = AboutUiState(versionName = "test-version"),
                    onAction = { selectedAction = it }
                )
            }
        }

        val entries = listOf(
            R.string.about_repository to AboutAction.OpenRepository,
            R.string.about_user_help to AboutAction.OpenHelp,
            R.string.about_api_documentation to AboutAction.OpenApiDocumentation,
            R.string.about_update_history to AboutAction.OpenUpdateHistory,
            R.string.crash_log to AboutAction.ShowCrashLogs,
            R.string.save_log to AboutAction.SaveLogs,
            R.string.create_heap_dump to AboutAction.CreateHeapDump,
            R.string.privacy_policy to AboutAction.ShowPrivacyPolicy,
            R.string.license to AboutAction.ShowLicense,
            R.string.disclaimer to AboutAction.ShowDisclaimer
        )

        entries.forEach { (titleResource, expectedAction) ->
            val title = context.getString(titleResource)
            compose.onNodeWithTag(ABOUT_CONTENT_LIST_TEST_TAG)
                .performScrollToNode(hasText(title))
            val row = compose.onNodeWithText(title)
            row
                .assertIsDisplayed()
                .assertHasClickAction()
            val rowHeightPx = row.fetchSemanticsNode().boundsInRoot.height
            val minimumTouchHeightPx = 48f * context.resources.displayMetrics.density
            assertTrue("$title should have at least a 48dp touch target", rowHeightPx >= minimumTouchHeightPx)
            row
                .performClick()
            compose.runOnIdle {
                assertEquals(expectedAction, selectedAction)
            }
        }
    }

    @Test
    fun fontScaleTwoKeepsTheLastEntryReachableAndTouchSized() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selectedAction: AboutAction? = null
        compose.setContent {
            LegadoTheme {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(currentDensity.density, fontScale = 2f)
                ) {
                    AboutContent(
                        state = AboutUiState(versionName = "test-version"),
                        onAction = { selectedAction = it }
                    )
                }
            }
        }

        val disclaimerTitle = context.getString(R.string.disclaimer)
        compose.onNodeWithTag(ABOUT_CONTENT_LIST_TEST_TAG)
            .performScrollToNode(hasText(disclaimerTitle))
        val disclaimer = compose.onNodeWithText(disclaimerTitle)
        disclaimer.assertIsDisplayed().assertHasClickAction()
        val minimumTouchHeightPx = 48f * context.resources.displayMetrics.density
        assertTrue(
            "The last About entry should retain a 48dp touch target at 2x font scale",
            disclaimer.fetchSemanticsNode().boundsInRoot.height >= minimumTouchHeightPx
        )
        disclaimer.performClick()
        compose.runOnIdle {
            assertEquals(AboutAction.ShowDisclaimer, selectedAction)
        }
    }
}
