package io.legado.app.ui.book.toc.rule

import android.text.Editable
import android.text.TextWatcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.compose.LegadoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TxtTocRuleEditorContent(
    rule: TxtTocRule?,
    loading: Boolean,
    error: String?,
    onRuleChange: (TxtTocRule) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClose: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val loadingLabel = stringResource(R.string.data_loading)
    Surface(
        modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.txt_toc_rule), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.regex),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.copy_rule)) }, onClick = { menuExpanded = false; onCopy() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.paste_rule)) }, onClick = { menuExpanded = false; onPaste() })
                    }
                }
                IconButton(onClick = onSave, enabled = rule != null && !loading) {
                    Icon(painterResource(R.drawable.ic_save), contentDescription = stringResource(R.string.action_save))
                }
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_baseline_close), contentDescription = stringResource(R.string.cancel))
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = loadingLabel }
                    )
                }
                rule != null -> Column(
                    Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = rule.name,
                        onValueChange = { onRuleChange(rule.copy(name = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = rule.rule,
                        onValueChange = { onRuleChange(rule.copy(rule = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.regex)) },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.replace_to_js), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CodeReplacementField(
                            value = rule.replacement,
                            onValueChange = { onRuleChange(rule.copy(replacement = it)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp)
                        )
                    }
                    OutlinedTextField(
                        value = rule.example.orEmpty(),
                        onValueChange = { onRuleChange(rule.copy(example = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.example)) },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp)
                    )
                    error?.let {
                        Text(
                            it,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        error ?: stringResource(R.string.error_get_data),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onSave, enabled = rule != null && !loading) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun CodeReplacementField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestValue = rememberUpdatedState(value)
    val latestCallback = rememberUpdatedState(onValueChange)
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            factory = {
                CodeView(context).apply {
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = android.graphics.Typeface.MONOSPACE
                    setTextColor(textColor)
                    addJsonPattern()
                    addJsPattern()
                    setText(value)
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            val updated = s?.toString().orEmpty()
                            if (updated != latestValue.value) latestCallback.value(updated)
                        }
                    })
                }
            },
            update = { editor ->
                editor.setTextColor(textColor)
                if (editor.text.toString() != value) {
                    val cursor = editor.selectionStart.coerceAtLeast(0)
                    editor.setText(value)
                    editor.setSelection(cursor.coerceAtMost(value.length))
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TxtTocRuleEditorPreview() {
    LegadoTheme {
        TxtTocRuleEditorContent(
            rule = TxtTocRule(name = "章节标题", rule = "第(.+?)章", replacement = "第$1章", example = "第十二章"),
            loading = false,
            error = null,
            onRuleChange = {}, onSave = {}, onCopy = {}, onPaste = {}, onClose = {}
        )
    }
}
