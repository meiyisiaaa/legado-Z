package io.legado.app.ui.widget.dialog

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal fun ComposeDialogView(context: Context) = ComposeWindowHost(context)

/**
 * Popup windows do not inherit the activity's ViewTree owners. Install the real activity owners
 * on the Compose root before it is attached so lifecycle-aware Compose APIs work in that window.
 */
internal fun ComposePopupView(context: Context) = ComposeWindowHost(context)

/**
 * Plain Dialog and PopupWindow roots have no owners. Compose resolves its recomposer from that
 * window root, so install missing owners before attaching the child, preserving DialogFragment's.
 */
internal class ComposeWindowHost(context: Context) : FrameLayout(context) {

    private val activity = context.findComponentActivity()
        ?: error("Compose window requires a ComponentActivity context")

    private val composeView = ComposeView(context).apply {
        id = View.generateViewId()
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    init {
        id = View.generateViewId()
        addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }

    override fun onAttachedToWindow() {
        rootView.apply {
            if (findViewTreeLifecycleOwner() == null) setViewTreeLifecycleOwner(activity)
            if (findViewTreeSavedStateRegistryOwner() == null) setViewTreeSavedStateRegistryOwner(activity)
            if (findViewTreeViewModelStoreOwner() == null) setViewTreeViewModelStoreOwner(activity)
        }
        super.onAttachedToWindow()
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context = this
    while (true) {
        if (current is ComponentActivity) return current
        if (current !is ContextWrapper) return null
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
}
