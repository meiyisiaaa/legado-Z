package io.legado.app.ui.widget

import android.content.Context
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.ComposePopupView

class PopupAction(private val context: Context) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val items = mutableStateOf(emptyList<SelectItem<String>>())
    var onActionClick: ((action: String) -> Unit)? = null

    init {
        val composeView = ComposePopupView(context)
        contentView = composeView
        composeView.setContent {
            LegadoTheme {
                PopupActionContent(
                    items = items.value,
                    onActionClick = { action -> onActionClick?.invoke(action) }
                )
            }
        }

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = true
    }

    fun setItems(items: List<SelectItem<String>>) {
        this.items.value = items.toList()
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PopupActionContent(
    items: List<SelectItem<String>>,
    onActionClick: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        FlowRow(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button) { onActionClick(item.value) }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
