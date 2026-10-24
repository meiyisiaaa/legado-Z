package io.legado.app.ui.book.import

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImportBrowserAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fileIsOneNamedCheckboxAndWholeRowChangesItsCheckedStateOnce() {
        var selected by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            LegadoTheme {
                ImportBrowserContent(
                    items = listOf(ImportBrowserItem("book", "本地书籍.txt", "txt", "1 KB", false, false, selected)),
                    emptyMessage = "空", onItemClick = { clicks++; selected = !selected }
                )
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("本地书籍.txt").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun localDirectoryHasNoNoOpLongClickAndKeepsItsOpenAction() {
        var opened = -1
        compose.setContent {
            LegadoTheme {
                ImportBrowserContent(
                    items = listOf(ImportBrowserItem("folder", "书籍目录", "", "", true, false, false)),
                    emptyMessage = "空", onItemClick = { opened = it }
                )
            }
        }
        compose.onNodeWithText("书籍目录")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            .performClick()
        compose.runOnIdle { assertEquals(0, opened) }
    }
}
