package io.legado.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.legado.app.ui.widget.compose.LegadoTheme

internal fun ComposeView.setLegadoContent(content: @Composable () -> Unit) {
    setContent { LegadoTheme(content = content) }
}

@Composable
internal fun <T> LiveData<T>.asComposeState(): State<T?> {
    val owner = LocalLifecycleOwner.current
    val state = remember(this) { mutableStateOf(value) }
    DisposableEffect(this, owner) {
        val observer = Observer<T> { state.value = it }
        observe(owner, observer)
        onDispose { removeObserver(observer) }
    }
    return state
}
