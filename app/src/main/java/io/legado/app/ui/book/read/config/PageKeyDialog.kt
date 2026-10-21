package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout

class PageKeyDialog(context: Context) : Dialog(context) {

    private var previousKeys by mutableStateOf(context.getPrefString(PreferKey.prevKeys).orEmpty())
    private var nextKeys by mutableStateOf(context.getPrefString(PreferKey.nextKeys).orEmpty())
    private var focusedPrevious by mutableStateOf<Boolean?>(null)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        setContentView(
            ComposeView(context).apply {
                id = R.id.legado_compose_host
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LegadoTheme {
                        PageKeyContent(
                            previousKeys = previousKeys,
                            nextKeys = nextKeys,
                            onPreviousChange = { previousKeys = it },
                            onNextChange = { nextKeys = it },
                            onFocusChange = { focusedPrevious = it },
                            onReset = {
                                previousKeys = ""
                                nextKeys = ""
                            },
                            onConfirm = {
                                context.putPrefString(PreferKey.prevKeys, previousKeys)
                                context.putPrefString(PreferKey.nextKeys, nextKeys)
                                dismiss()
                            }
                        )
                    }
                }
            }
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
            if (focusedPrevious == true) {
                previousKeys = appendKey(previousKeys, keyCode)
                return true
            }
            if (focusedPrevious == false) {
                nextKeys = appendKey(nextKeys, keyCode)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dismiss() {
        super.dismiss()
        currentFocus?.hideSoftInput()
    }

    private fun appendKey(current: String, keyCode: Int): String = when {
        current.isEmpty() || current.endsWith(",") -> current + keyCode
        else -> "$current,$keyCode"
    }
}
