package io.legado.app.ui.association

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone

internal data class AssociationImportRow(
    val name: String,
    val status: String,
    val selected: Boolean,
    val detail: String? = null
)

internal fun installAssociationImportContent(
    context: Context,
    recycler: android.view.View,
    rows: List<AssociationImportRow>,
    onSelectionChange: (Int, Boolean) -> Unit,
    onOpen: (Int) -> Unit
): ComposeView {
    val listContainer = recycler.parent as FrameLayout
    val index = listContainer.indexOfChild(recycler)
    recycler.gone()
    // The shared dialog layout is also used by full-height dialogs. Adjust only
    // this import instance so its list can size the WRAP_CONTENT dialog.
    val dialogRoot = listContainer.parent as ViewGroup
    val minFooterHeight = 48.dpToPx()
    listOf(R.id.tv_footer_left, R.id.tv_cancel, R.id.tv_ok).forEach { id ->
        dialogRoot.findViewById<View>(id)?.minimumHeight = minFooterHeight
    }
    fun resizeDialogRoot() {
        val params = checkNotNull(dialogRoot.layoutParams) {
            "Dialog root must have layout params after it is attached"
        }
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        dialogRoot.layoutParams = params
    }
    if (dialogRoot.isAttachedToWindow) {
        resizeDialogRoot()
    } else {
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                resizeDialogRoot()
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }
        dialogRoot.addOnAttachStateChangeListener(attachListener)
    }
    val containerParams = listContainer.layoutParams as LinearLayout.LayoutParams
    containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    // Keep the XML's weight so LinearLayout can reduce the list by exactly the
    // overflow after measuring the toolbar and footer at their real font size.
    containerParams.weight = 1f
    listContainer.layoutParams = containerParams
    return LegadoComposeView(context).also { host ->
        listContainer.addView(
            host,
            index,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        host.setContent {
            LegadoTheme {
                AssociationImportContent(rows, onSelectionChange, onOpen)
            }
        }
    }
}

@Composable
internal fun AssociationImportContent(
    rows: List<AssociationImportRow>,
    onSelectionChange: (Int, Boolean) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val systemBarPadding = WindowInsets.systemBars.asPaddingValues()
    val minListHeight = if (rows.isEmpty()) 72.dp else 0.dp
    val maxListHeight = (
        configuration.screenHeightDp.dp -
            systemBarPadding.calculateTopPadding() -
            systemBarPadding.calculateBottomPadding()
        ).coerceAtLeast(minListHeight)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minListHeight, max = maxListHeight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 4.dp)
                        .semantics(mergeDescendants = true) {
                            role = Role.Checkbox
                            toggleableState = if (row.selected) ToggleableState.On else ToggleableState.Off
                        }
                        .clickable { onSelectionChange(index, !row.selected) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = row.selected,
                        onCheckedChange = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = row.name,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = row.status,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { onOpen(index) }) {
                        Text(stringResource(R.string.open))
                    }
                }
                row.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
