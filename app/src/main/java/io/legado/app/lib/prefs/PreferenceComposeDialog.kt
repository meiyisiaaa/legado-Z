package io.legado.app.lib.prefs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.preference.PreferenceDialogFragmentCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.ComposeDialogView
import io.legado.app.utils.dpToPx

/** AndroidX still owns preference lookup and close dispatch; Compose owns the visible dialog. */
abstract class ComposePreferenceDialogFragmentCompat : PreferenceDialogFragmentCompat() {

    @Composable
    protected abstract fun DialogContent()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        createPreferenceComposeDialog(requireContext()) { DialogContent() }

    override fun onStart() {
        super.onStart()
        configurePreferenceComposeDialog(dialog, requireContext())
    }

    protected fun closePreferenceDialog(positiveResult: Boolean) {
        onClick(
            requireDialog(),
            if (positiveResult) DialogInterface.BUTTON_POSITIVE else DialogInterface.BUTTON_NEGATIVE
        )
        dismiss()
    }
}

internal fun createPreferenceComposeDialog(
    context: Context,
    content: @Composable () -> Unit
): Dialog {
    val composeView = ComposeDialogView(context).apply {
        setContent {
            LegadoTheme {
                content()
            }
        }
    }
    return AlertDialog.Builder(context)
        .setView(composeView, 0, 0, 0, 0)
        .create()
}

internal fun configurePreferenceComposeDialog(dialog: Dialog?, context: Context) {
    val window = dialog?.window ?: return
    window.setBackgroundDrawableResource(R.color.transparent)
    val maxWidthPx = 560.dpToPx()
    val widthPx = (context.resources.displayMetrics.widthPixels * 0.9f)
        .toInt()
        .coerceAtMost(maxWidthPx)
    window.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)

    if (!AppConfig.isEInkMode) return

    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    val attributes = window.attributes.apply {
        dimAmount = 0f
        windowAnimations = 0
    }
    window.attributes = attributes
    when (attributes.gravity) {
        Gravity.TOP -> window.decorView.setBackgroundResource(R.drawable.bg_eink_border_bottom)
        Gravity.BOTTOM -> window.decorView.setBackgroundResource(R.drawable.bg_eink_border_top)
        else -> {
            val padding = 2.dpToPx()
            window.decorView.setPadding(padding, padding, padding, padding)
            window.decorView.setBackgroundResource(R.drawable.bg_eink_border_dialog)
        }
    }
}

@Composable
internal fun PreferenceDialogSurface(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            LegadoDialogHeader(title, onClose)
            content()
        }
    }
}

@Composable
internal fun PreferenceDialogActions(
    cancelLabel: String?,
    confirmLabel: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    if (cancelLabel == null && confirmLabel == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cancelLabel != null) {
            TextButton(onClick = onCancel) { Text(cancelLabel) }
        }
        if (cancelLabel != null && confirmLabel != null) Spacer(Modifier.width(8.dp))
        if (confirmLabel != null) {
            Button(onClick = onConfirm) { Text(confirmLabel) }
        }
    }
}

@Composable
internal fun PreferenceSingleChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
internal fun PreferenceMultiChoiceRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
