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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.ListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import io.legado.app.ui.widget.compose.LegadoTheme

class ListPreferenceDialog : ComposePreferenceDialogFragmentCompat() {

    private var entries by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private var entryValues by androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())
    private var selectedIndex by mutableIntStateOf(-1)

    companion object {
        private const val STATE_ENTRIES = "composeListPreferenceDialog.entries"
        private const val STATE_ENTRY_VALUES = "composeListPreferenceDialog.entryValues"
        private const val STATE_SELECTED_INDEX = "composeListPreferenceDialog.selectedIndex"

        fun newInstance(key: String?): ListPreferenceDialog = ListPreferenceDialog().apply {
            arguments = Bundle(1).apply {
                putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preference = getPreference() as ListPreference
        entries = savedInstanceState?.getStringArrayList(STATE_ENTRIES)
            ?: preference.entries?.map(CharSequence::toString).orEmpty()
        entryValues = savedInstanceState?.getStringArrayList(STATE_ENTRY_VALUES)
            ?: preference.entryValues?.map(CharSequence::toString).orEmpty()
        selectedIndex = savedInstanceState?.getInt(STATE_SELECTED_INDEX)
            ?: preference.findIndexOfValue(preference.value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_ENTRIES, ArrayList(entries))
        outState.putStringArrayList(STATE_ENTRY_VALUES, ArrayList(entryValues))
        outState.putInt(STATE_SELECTED_INDEX, selectedIndex)
    }

    @Composable
    override fun DialogContent() {
        val preference = getPreference() as ListPreference
        PreferenceListDialogContent(
            title = preference.dialogTitle?.toString().orEmpty(),
            message = preference.dialogMessage?.toString(),
            entries = entries,
            entryValues = entryValues,
            selectedIndex = selectedIndex,
            cancelLabel = preference.negativeButtonText?.toString(),
            onSelect = { index ->
                selectedIndex = index
                closePreferenceDialog(positiveResult = true)
            },
            onCancel = { closePreferenceDialog(positiveResult = false) }
        )
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult || selectedIndex !in entryValues.indices) return
        val preference = getPreference() as ListPreference
        val newValue = entryValues[selectedIndex]
        if (preference.callChangeListener(newValue)) {
            preference.value = newValue
        }
    }
}

@Composable
private fun PreferenceListDialogContent(
    title: String,
    message: String?,
    entries: List<String>,
    entryValues: List<String>,
    selectedIndex: Int,
    cancelLabel: String?,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit
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
                PreferenceSingleChoiceRow(
                    title = entries.getOrNull(index) ?: value,
                    selected = index == selectedIndex,
                    onSelect = { onSelect(index) }
                )
            }
        }
        PreferenceDialogActions(
            cancelLabel = cancelLabel,
            confirmLabel = null,
            onCancel = onCancel,
            onConfirm = onCancel
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceListDialogPreview() {
    LegadoTheme {
        PreferenceListDialogContent(
            title = "Reading direction",
            message = null,
            entries = listOf("Left to right", "Right to left"),
            entryValues = listOf("ltr", "rtl"),
            selectedIndex = 0,
            cancelLabel = "Cancel",
            onSelect = {},
            onCancel = {}
        )
    }
}
