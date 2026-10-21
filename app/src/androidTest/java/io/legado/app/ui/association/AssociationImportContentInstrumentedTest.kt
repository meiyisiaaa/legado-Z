package io.legado.app.ui.association

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssociationImportContentInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun shortListWrapsAtLargeFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val largeFontConfiguration = Configuration(context.resources.configuration).apply {
            fontScale = 2f
        }
        compose.setContent {
            LegadoTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalConfiguration provides largeFontConfiguration,
                    LocalDensity provides Density(density.density, fontScale = 2f)
                ) {
                    Box(Modifier.width(320.dp).height(280.dp).testTag("short_list_viewport")) {
                        AssociationImportContent(
                            rows = listOf(AssociationImportRow("Only source", "新增", selected = true)),
                            onSelectionChange = { _, _ -> },
                            onOpen = {},
                            modifier = Modifier.testTag("import_list")
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val shortListHeight = compose.onNodeWithTag("import_list")
            .fetchSemanticsNode().boundsInRoot.height
        val shortViewportHeight = compose.onNodeWithTag("short_list_viewport")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("A single imported row should not fill the dialog viewport", shortListHeight < shortViewportHeight)
    }

    @Test
    fun longListScrollKeepsFooterReachableAtLargeFont() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val largeFontConfiguration = Configuration(context.resources.configuration).apply {
            fontScale = 2f
        }
        var confirmClicks = 0
        compose.setContent {
            LegadoTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalConfiguration provides largeFontConfiguration,
                    LocalDensity provides Density(density.density, fontScale = 2f)
                ) {
                    Column(Modifier.width(320.dp).height(280.dp).testTag("long_list_viewport")) {
                        AssociationImportContent(
                            rows = (1..40).map { index ->
                                AssociationImportRow("Source $index", "新增", selected = true)
                            },
                            onSelectionChange = { _, _ -> },
                            onOpen = {},
                            modifier = Modifier.weight(1f, fill = false).testTag("import_list")
                        )
                        TextButton(
                            onClick = { confirmClicks++ },
                            modifier = Modifier.testTag("confirm_import")
                        ) {
                            Text("Confirm import")
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("import_list")
            .performSemanticsAction(SemanticsActions.ScrollToIndex) { scrollToIndex -> scrollToIndex(39) }
        compose.onNodeWithText("Source 40", useUnmergedTree = true).assertIsDisplayed()
        val longViewportBottom = compose.onNodeWithTag("long_list_viewport")
            .fetchSemanticsNode().boundsInRoot.bottom
        val listBottom = compose.onNodeWithTag("import_list")
            .fetchSemanticsNode().boundsInRoot.bottom
        val footerBounds = compose.onNodeWithTag("confirm_import")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The list must end before the footer", listBottom <= footerBounds.top)
        assertTrue("The footer must remain inside the bounded viewport", footerBounds.bottom <= longViewportBottom)
        compose.onNodeWithTag("confirm_import").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, confirmClicks) }
    }
}
