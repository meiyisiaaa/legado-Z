package io.legado.app.ui.replace.edit

import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import io.legado.app.R
import io.legado.app.lib.theme.view.ThemeEditText
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.text.TextInputLayout

internal data class ReplaceEditDraft(
    val name: String = "",
    val group: String = "",
    val pattern: String = "",
    val isRegex: Boolean = false,
    val replacement: String = "",
    val scopeTitle: Boolean = false,
    val scopeContent: Boolean = true,
    val scope: String = "",
    val excludeScope: String = "",
    val timeout: String = "3000"
)

@Composable
internal fun ReplaceEditContent(
    draft: ReplaceEditDraft,
    onDraftChange: (ReplaceEditDraft) -> Unit,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val regexLabel = stringResource(R.string.use_regex)
    val scopeTitleLabel = stringResource(R.string.scope_title)
    val scopeContentLabel = stringResource(R.string.scope_content)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        ReplaceEditTextField(
            label = stringResource(R.string.replace_rule_summary),
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) }
        )
        ReplaceEditTextField(
            label = stringResource(R.string.group),
            value = draft.group,
            onValueChange = { onDraftChange(draft.copy(group = it)) }
        )
        ReplaceEditTextField(
            label = stringResource(R.string.replace_rule),
            value = draft.pattern,
            onValueChange = { onDraftChange(draft.copy(pattern = it)) },
            multiline = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = draft.isRegex,
                onCheckedChange = { onDraftChange(draft.copy(isRegex = it)) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = regexLabel }
            )
            Text(
                text = stringResource(R.string.use_regex),
                modifier = Modifier.weight(1f).clickable {
                    onDraftChange(draft.copy(isRegex = !draft.isRegex))
                },
                style = MaterialTheme.typography.bodyLarge
            )
            IconButton(onClick = onHelp) {
                Icon(
                    painter = painterResource(R.drawable.ic_help),
                    contentDescription = stringResource(R.string.help),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        ReplaceEditTextField(
            label = stringResource(R.string.replace_to),
            value = draft.replacement,
            onValueChange = { onDraftChange(draft.copy(replacement = it)) },
            multiline = true
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = draft.scopeTitle,
                onCheckedChange = { onDraftChange(draft.copy(scopeTitle = it)) },
                modifier = Modifier.size(48.dp).semantics { contentDescription = scopeTitleLabel }
            )
            Text(
                stringResource(R.string.scope_title),
                modifier = Modifier.clickable {
                    onDraftChange(draft.copy(scopeTitle = !draft.scopeTitle))
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Checkbox(
                checked = draft.scopeContent,
                onCheckedChange = { onDraftChange(draft.copy(scopeContent = it)) },
                modifier = Modifier.padding(start = 6.dp).size(48.dp).semantics { contentDescription = scopeContentLabel }
            )
            Text(
                stringResource(R.string.scope_content),
                modifier = Modifier.clickable {
                    onDraftChange(draft.copy(scopeContent = !draft.scopeContent))
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
        ReplaceEditTextField(
            label = stringResource(R.string.replace_scope),
            value = draft.scope,
            onValueChange = { onDraftChange(draft.copy(scope = it)) },
            multiline = true
        )
        ReplaceEditTextField(
            label = stringResource(R.string.replace_exclude_scope),
            value = draft.excludeScope,
            onValueChange = { onDraftChange(draft.copy(excludeScope = it)) },
            multiline = true
        )
        ReplaceEditTextField(
            label = stringResource(R.string.timeout_millisecond),
            value = draft.timeout,
            onValueChange = { onDraftChange(draft.copy(timeout = it)) },
            inputType = InputType.TYPE_CLASS_NUMBER
        )
    }
}

@Composable
private fun ReplaceEditTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
) {
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        factory = {
            TextInputLayout(context, null).apply {
                hint = label
                addView(
                    ThemeEditText(context).apply {
                        this.inputType = inputType
                        setSingleLine(!multiline)
                        if (multiline) {
                            minLines = 2
                            maxLines = 5
                        }
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        addTextChangedListener { currentOnValueChange.value(it?.toString().orEmpty()) }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        },
        update = { layout ->
            val editText = layout.editText
            if (editText != null && editText.text?.toString() != currentValue.value) {
                editText.setText(currentValue.value)
                editText.setSelection(editText.text?.length ?: 0)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ReplaceEditContentPreview() {
    LegadoTheme {
        ReplaceEditContent(draft = ReplaceEditDraft(), onDraftChange = {}, onHelp = {})
    }
}
