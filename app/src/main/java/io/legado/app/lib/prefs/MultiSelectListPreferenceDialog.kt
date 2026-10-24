package io.legado.app.lib.prefs

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import io.legado.app.ui.widget.compose.LegadoTheme

class MultiSelectListPreferenceDialog : ComposePreferenceDialogFragmentCompat() {

    private var entries by mutableStateOf<List<String>>(emptyList())
    private var entryValues by mutableStateOf<List<String>>(emptyList())
    private var selectedValues by mutableStateOf<Set<String>>(emptySet())

    companion object {
        private const val STATE_ENTRIES = "composeMultiSelectPreferenceDialog.entries"
        private const val STATE_ENTRY_VALUES = "composeMultiSelectPreferenceDialog.entryValues"
        private const val STATE_SELECTED_VALUES = "composeMultiSelectPreferenceDialog.selectedValues"

        fun newInstance(key: String?): MultiSelectListPreferenceDialog =
            MultiSelectListPreferenceDialog().apply {
                arguments = Bundle(1).apply {
                    putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preference = getPreference() as MultiSelectListPreference
        entries = savedInstanceState?.getStringArrayList(STATE_ENTRIES)
            ?: preference.entries?.map(CharSequence::toString).orEmpty()
        entryValues = savedInstanceState?.getStringArrayList(STATE_ENTRY_VALUES)
            ?: preference.entryValues?.map(CharSequence::toString).orEmpty()
        selectedValues = savedInstanceState?.getStringArrayList(STATE_SELECTED_VALUES)?.toSet()
            ?: preference.values.toSet()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_ENTRIES, ArrayList(entries))
        outState.putStringArrayList(STATE_ENTRY_VALUES, ArrayList(entryValues))
        outState.putStringArrayList(STATE_SELECTED_VALUES, ArrayList(selectedValues))
    }

    @Composable
    override fun DialogContent() {
        val preference = getPreference() as MultiSelectListPreference
        PreferenceMultiSelectDialogContent(
            title = preference.dialogTitle?.toString().orEmpty(),
            message = preference.dialogMessage?.toString(),
            entries = entries,
            entryValues = entryValues,
            selectedValues = selectedValues,
            cancelLabel = preference.negativeButtonText?.toString(),
            confirmLabel = preference.positiveButtonText?.toString(),
            onToggle = { value ->
                selectedValues = if (value in selectedValues) {
                    selectedValues - value
                } else {
                    selectedValues + value
                }
            },
            onCancel = { closePreferenceDialog(positiveResult = false) },
            onConfirm = { closePreferenceDialog(positiveResult = true) }
        )
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val preference = getPreference() as MultiSelectListPreference
        val newValues = selectedValues.toSet()
        if (preference.callChangeListener(newValues)) {
            preference.values = newValues
        }
    }
}

@Composable
private fun PreferenceMultiSelectDialogContent(
    title: String,
    message: String?,
    entries: List<String>,
    entryValues: List<String>,
    selectedValues: Set<String>,
    cancelLabel: String?,
    confirmLabel: String?,
    onToggle: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    PreferenceDialogSurface(title = title, onClose = onCancel) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            itemsIndexed(entryValues, key = { index, value -> "$index:$value" }) { index, value ->
                PreferenceMultiChoiceRow(
                    title = entries.getOrNull(index) ?: value,
                    checked = value in selectedValues,
                    onToggle = { onToggle(value) }
                )
            }
        }
        PreferenceDialogActions(
            cancelLabel = cancelLabel,
            confirmLabel = confirmLabel,
            onCancel = onCancel,
            onConfirm = onConfirm
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceMultiSelectDialogPreview() {
    LegadoTheme {
        PreferenceMultiSelectDialogContent(
            title = "Download sources",
            message = "Choose one or more sources.",
            entries = listOf("Source A", "Source B", "Source C"),
            entryValues = listOf("a", "b", "c"),
            selectedValues = setOf("a", "c"),
            cancelLabel = "Cancel",
            confirmLabel = "Save",
            onToggle = {},
            onCancel = {},
            onConfirm = {}
        )
    }
}
