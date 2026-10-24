package io.legado.app.ui.widget.dialog

import android.view.Gravity
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.ui.widget.PopupAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PopupActionComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun plainWaitDialogInstallsWindowOwnersAndCanBeShownAgain() {
        var dialog: WaitDialog? = null
        var canceled = false
        try {
            composeRule.runOnUiThread {
                dialog = WaitDialog(ContextThemeWrapper(composeRule.activity, io.legado.app.R.style.AppTheme_Light))
                    .setText("Backup in progress")
                    .apply {
                        setOnCancelListener { canceled = true }
                        show()
                    }
            }
            composeRule.onNodeWithText("Backup in progress").assertIsDisplayed()
            composeRule.runOnIdle {
                val root = checkNotNull(dialog?.window?.decorView)
                assertSame(composeRule.activity, root.findViewTreeLifecycleOwner())
                assertSame(composeRule.activity, root.findViewTreeSavedStateRegistryOwner())
                assertSame(composeRule.activity, root.findViewTreeViewModelStoreOwner())
                dialog?.setText("Restoring")
            }
            composeRule.onNodeWithText("Restoring").assertIsDisplayed()
            composeRule.runOnUiThread { dialog?.cancel() }
            composeRule.runOnIdle { assertTrue(canceled) }
            composeRule.onNodeWithText("Restoring").assertDoesNotExist()
            composeRule.runOnUiThread { dialog?.show() }
            composeRule.onNodeWithText("Restoring").assertIsDisplayed()
        } finally {
            composeRule.runOnUiThread { dialog?.dismiss() }
        }
    }

    @Test
    fun popupWindowInstallsActivityOwnersAndDispatchesAction() {
        var popup: PopupAction? = null
        var selectedAction: String? = null

        try {
            composeRule.runOnUiThread {
                val actionPopup = PopupAction(composeRule.activity)
                popup = actionPopup
                actionPopup.setItems(listOf(SelectItem("Show image", "show")))
                actionPopup.onActionClick = { action ->
                    selectedAction = action
                    actionPopup.dismiss()
                }
                actionPopup.showAtLocation(
                    composeRule.activity.window.decorView,
                    Gravity.CENTER,
                    0,
                    0
                )
            }
            val shownPopup = checkNotNull(popup)
            composeRule.onNodeWithText("Show image").assertIsDisplayed()
            composeRule.runOnIdle {
                val popupRoot = shownPopup.contentView.rootView
                assertSame(composeRule.activity, popupRoot.findViewTreeLifecycleOwner())
                assertSame(composeRule.activity, popupRoot.findViewTreeSavedStateRegistryOwner())
                assertSame(composeRule.activity, popupRoot.findViewTreeViewModelStoreOwner())
            }
            composeRule.onNodeWithText("Show image").performClick()
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals("show", selectedAction)
                assertFalse(shownPopup.isShowing)
            }
            composeRule.onNodeWithText("Show image").assertDoesNotExist()
        } finally {
            popup?.let { ownedPopup ->
                composeRule.runOnUiThread {
                    if (ownedPopup.isShowing) ownedPopup.dismiss()
                }
            }
        }
    }
}
