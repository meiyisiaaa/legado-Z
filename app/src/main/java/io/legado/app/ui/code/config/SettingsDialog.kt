package io.legado.app.ui.code.config

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_LINE_SEPARATOR
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_INNER
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_LEADING
import io.github.rosemoe.sora.widget.CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt

class SettingsDialog(private val context: Context, private val callBack: CallBack) :
    BaseDialogFragment(R.layout.dialog_edit_settings) {

    private val initialEditNonPrintable = AppConfig.editNonPrintable
    private var fontSize by mutableStateOf(AppConfig.editFontScale)
    private var autoComplete by mutableStateOf(AppConfig.editAutoComplete)
    private var drawLeading by mutableStateOf(initialEditNonPrintable and FLAG_DRAW_WHITESPACE_LEADING != 0)
    private var drawInner by mutableStateOf(initialEditNonPrintable and FLAG_DRAW_WHITESPACE_INNER != 0)
    private var drawTrailing by mutableStateOf(initialEditNonPrintable and FLAG_DRAW_WHITESPACE_TRAILING != 0)
    private var drawEmptyLine by mutableStateOf(initialEditNonPrintable and FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE != 0)
    private var drawLineSeparator by mutableStateOf(initialEditNonPrintable and FLAG_DRAW_LINE_SEPARATOR != 0)
    private var drawInSelection by mutableStateOf(initialEditNonPrintable and FLAG_DRAW_WHITESPACE_IN_SELECTION != 0)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ComposeView>(R.id.composeView).setContent {
            LegadoTheme {
                CodeSettingsContent(
                    fontSizeText = "${fontSize}sp",
                    autoComplete = autoComplete,
                    drawLeading = drawLeading,
                    drawInner = drawInner,
                    drawTrailing = drawTrailing,
                    drawEmptyLine = drawEmptyLine,
                    drawLineSeparator = drawLineSeparator,
                    drawInSelection = drawInSelection,
                    onFontSize = ::showFontSizePicker,
                    onAutoComplete = {
                        autoComplete = it
                        putPrefBoolean(PreferKey.editAutoComplete, it)
                        callBack.upEdit(autoComplete = it)
                    },
                    onDrawLeading = { drawLeading = it },
                    onDrawInner = { drawInner = it },
                    onDrawTrailing = { drawTrailing = it },
                    onDrawEmptyLine = { drawEmptyLine = it },
                    onDrawLineSeparator = { drawLineSeparator = it },
                    onDrawInSelection = { drawInSelection = it }
                )
            }
        }
    }

    private fun showFontSizePicker() {
        NumberPickerDialog(requireContext())
            .setTitle(getString(R.string.font_scale))
            .setMaxValue(36)
            .setMinValue(9)
            .setValue(AppConfig.editFontScale)
            .setCustomButton(R.string.btn_default_s) {
                putPrefInt(PreferKey.editFontScale, 16)
                callBack.upEdit(fontSize = 16)
                fontSize = 16
            }
            .show {
                putPrefInt(PreferKey.editFontScale, it)
                callBack.upEdit(fontSize = it)
                fontSize = it
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        var editNonPrintable = 0
        if (drawLeading) editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_LEADING
        if (drawInner) editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_INNER
        if (drawTrailing) editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_TRAILING
        if (drawEmptyLine) editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE
        if (drawLineSeparator) editNonPrintable = editNonPrintable or FLAG_DRAW_LINE_SEPARATOR
        if (drawInSelection) editNonPrintable = editNonPrintable or FLAG_DRAW_WHITESPACE_IN_SELECTION
        if (editNonPrintable != initialEditNonPrintable) {
            putPrefInt(PreferKey.editNonPrintable, editNonPrintable)
            callBack.upEdit(editNonPrintable = editNonPrintable)
        }
    }

    interface CallBack {
        fun upEdit(
            fontSize: Int? = null,
            autoComplete: Boolean? = null,
            autoWarp: Boolean? = null,
            editNonPrintable: Int? = null
        )
    }
}

@Composable
private fun CodeSettingsContent(
    fontSizeText: String,
    autoComplete: Boolean,
    drawLeading: Boolean,
    drawInner: Boolean,
    drawTrailing: Boolean,
    drawEmptyLine: Boolean,
    drawLineSeparator: Boolean,
    drawInSelection: Boolean,
    onFontSize: () -> Unit,
    onAutoComplete: (Boolean) -> Unit,
    onDrawLeading: (Boolean) -> Unit,
    onDrawInner: (Boolean) -> Unit,
    onDrawTrailing: (Boolean) -> Unit,
    onDrawEmptyLine: (Boolean) -> Unit,
    onDrawLineSeparator: (Boolean) -> Unit,
    onDrawInSelection: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.config_settings),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onFontSize)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.font_scale),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                fontSizeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CodeSettingCheckRow(R.string.auto_complete, autoComplete, onAutoComplete)
        Text(
            androidx.compose.ui.res.stringResource(R.string.non_printable_set),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CodeWhitespaceToggle(R.string.whitespace_leading, drawLeading, onDrawLeading, Modifier.weight(1f))
            CodeWhitespaceToggle(R.string.whitespace_inner, drawInner, onDrawInner, Modifier.weight(1f))
            CodeWhitespaceToggle(R.string.whitespace_trailing, drawTrailing, onDrawTrailing, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CodeWhitespaceToggle(R.string.whitespace_empty, drawEmptyLine, onDrawEmptyLine, Modifier.weight(1f))
            CodeWhitespaceToggle(R.string.line_separator, drawLineSeparator, onDrawLineSeparator, Modifier.weight(1f))
            CodeWhitespaceToggle(R.string.whitespace_selection, drawInSelection, onDrawInSelection, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CodeSettingCheckRow(title: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            androidx.compose.ui.res.stringResource(title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun CodeWhitespaceToggle(
    title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            androidx.compose.ui.res.stringResource(title),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(48.dp)
        )
    }
}
