package io.legado.app.ui.book.group

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GroupContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manageSwitchAndEditActionNameTheirGroupAndExposeVisibility() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val group = BookGroup(groupId = 1L, groupName = "待读", show = false)
        var edited: BookGroup? = null

        composeRule.setContent {
            LegadoTheme {
                GroupManageContent(
                    groups = listOf(group),
                    onAdd = {},
                    onEdit = { edited = it },
                    onShowChange = { _, _ -> },
                    onMove = { _, _ -> },
                    onDragFinished = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("${group.groupName}, ${context.getString(R.string.enable)}")
            .assertIsOff()
        composeRule.onNodeWithContentDescription("${group.groupName}, ${context.getString(R.string.edit)}")
            .performClick()
        composeRule.runOnIdle { assertEquals(group, edited) }
    }

    @Test
    fun groupSelectionCheckboxNamesTheGroupAndReportsSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val group = BookGroup(groupId = 1L, groupName = "待读")
        var selectedId = 1L

        composeRule.setContent {
            LegadoTheme {
                GroupSelectContent(
                    groups = listOf(group),
                    selectedGroupId = selectedId,
                    onSelectedGroupIdChange = { selectedId = it },
                    onAdd = {},
                    onEdit = {},
                    onMove = { _, _ -> },
                    onDragFinished = {},
                    onCancel = {},
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(group.groupName).assertIsOn()
        composeRule.onNodeWithContentDescription("${group.groupName}, ${context.getString(R.string.edit)}")
            .assertExists()
    }

    @Test
    fun groupEditOptionsAndCoverActionAreNamed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var coverClicked = false

        composeRule.setContent {
            LegadoTheme {
                GroupEditContent(
                    initialName = "待读",
                    initialSort = -1,
                    initialEnableRefresh = true,
                    initialOnlyRead = false,
                    canDelete = true,
                    onCoverClick = { coverClicked = true },
                    onCancel = {},
                    onDelete = {},
                    onSave = { _, _, _, _ -> },
                    coverPreview = { androidx.compose.material3.Text("封面") }
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.allow_drop_down_refresh))
            .assertIsOn()
        composeRule.onNodeWithContentDescription(context.getString(R.string.only_update_read))
            .assertIsOff()
        composeRule.onNodeWithContentDescription(context.getString(R.string.select_image))
            .performClick()
        composeRule.runOnIdle { assertEquals(true, coverClicked) }
    }
}
