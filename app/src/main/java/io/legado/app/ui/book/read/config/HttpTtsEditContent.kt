package io.legado.app.ui.book.read.config

import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.data.entities.HttpTTS
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.TintHelper
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.text.TextInputLayout

internal enum class HttpTtsMenuAction {
    FULL_EDIT, SAVE, LOGIN, SHOW_LOGIN_HEADER, DELETE_LOGIN_HEADER,
    COPY_SOURCE, PASTE_SOURCE, LOG, HELP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HttpTtsEditContent(
    draft: HttpTTS,
    menuExpanded: Boolean,
    unsavedDialog: Boolean,
    loginHeader: String?,
    showLoginHeader: Boolean,
    onDraftChange: (HttpTTS) -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMenuAction: (HttpTtsMenuAction) -> Unit,
    onUnsavedDialogChange: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onLoginHeaderDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.speak_engine)) },
            actions = {
                IconButton(onClick = { onMenuAction(HttpTtsMenuAction.FULL_EDIT) }) {
                    Icon(painterResource(R.drawable.ic_code), contentDescription = stringResource(R.string.edit_content))
                }
                IconButton(onClick = { onMenuAction(HttpTtsMenuAction.SAVE) }) {
                    Icon(painterResource(R.drawable.ic_save), contentDescription = stringResource(R.string.action_save))
                }
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                    EditMenuItem(R.string.login) { onMenuAction(HttpTtsMenuAction.LOGIN) }
                    EditMenuItem(R.string.show_login_header) { onMenuAction(HttpTtsMenuAction.SHOW_LOGIN_HEADER) }
                    EditMenuItem(R.string.del_login_header) { onMenuAction(HttpTtsMenuAction.DELETE_LOGIN_HEADER) }
                    EditMenuItem(R.string.copy_source) { onMenuAction(HttpTtsMenuAction.COPY_SOURCE) }
                    EditMenuItem(R.string.paste_source) { onMenuAction(HttpTtsMenuAction.PASTE_SOURCE) }
                    EditMenuItem(R.string.log) { onMenuAction(HttpTtsMenuAction.LOG) }
                    EditMenuItem(R.string.help) { onMenuAction(HttpTtsMenuAction.HELP) }
                }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onDraftChange(draft.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true
                )
            }
            item {
                HttpTtsCodeField("URL", draft.url, CodeSyntax.ALL) {
                    onDraftChange(draft.copy(url = it))
                }
            }
            item {
                HttpTtsCodeField("Content-Type", draft.contentType.orEmpty(), CodeSyntax.NONE) {
                    onDraftChange(draft.copy(contentType = it))
                }
            }
            item {
                HttpTtsCodeField(stringResource(R.string.concurrent_rate), draft.concurrentRate.orEmpty(), CodeSyntax.NONE) {
                    onDraftChange(draft.copy(concurrentRate = it))
                }
            }
            item {
                HttpTtsCodeField(stringResource(R.string.login_url), draft.loginUrl.orEmpty(), CodeSyntax.ALL) {
                    onDraftChange(draft.copy(loginUrl = it))
                }
            }
            item {
                HttpTtsCodeField(stringResource(R.string.login_ui), draft.loginUi.orEmpty(), CodeSyntax.JSON) {
                    onDraftChange(draft.copy(loginUi = it))
                }
            }
            item {
                HttpTtsCodeField(stringResource(R.string.login_check_js), draft.loginCheckJs.orEmpty(), CodeSyntax.JS) {
                    onDraftChange(draft.copy(loginCheckJs = it))
                }
            }
            item {
                HttpTtsCodeField(stringResource(R.string.source_http_header), draft.header.orEmpty(), CodeSyntax.ALL) {
                    onDraftChange(draft.copy(header = it))
                }
            }
            item {
                HttpTtsCodeField("jsLib", draft.jsLib.orEmpty(), CodeSyntax.ALL) {
                    onDraftChange(draft.copy(jsLib = it))
                }
            }
        }
    }
    if (unsavedDialog) {
        AlertDialog(
            onDismissRequest = { onUnsavedDialogChange(false) },
            title = { Text(stringResource(R.string.exit)) },
            text = { Text(stringResource(R.string.exit_no_save)) },
            confirmButton = {
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { onUnsavedDialogChange(false) }) { Text(stringResource(R.string.no)) }
            }
        )
    }
    if (showLoginHeader) {
        AlertDialog(
            onDismissRequest = onLoginHeaderDismiss,
            title = { Text(stringResource(R.string.login_header)) },
            text = { Text(loginHeader.orEmpty()) },
            confirmButton = { TextButton(onClick = onLoginHeaderDismiss) { Text(stringResource(R.string.ok)) } }
        )
    }
}

@Composable
private fun EditMenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

private enum class CodeSyntax { NONE, JSON, JS, ALL }

private data class CodeEditorState(
    var suppress: Boolean,
    var onChange: (String) -> Unit,
    val watcher: TextWatcher
)

@Composable
private fun HttpTtsCodeField(
    label: String,
    value: String,
    syntax: CodeSyntax,
    onValueChange: (String) -> Unit
) {
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { viewContext ->
            val accentColor = ThemeStore.accentColor(viewContext)
            val isDark = AppConfig.isNightTheme
            val normalColor = ContextCompat.getColor(
                viewContext,
                if (isDark) R.color.ate_control_normal_dark else R.color.ate_control_normal_light
            )
            val disabledColor = ContextCompat.getColor(
                viewContext,
                if (isDark) R.color.ate_text_disabled_dark else R.color.ate_text_disabled_light
            )
            val fieldColors = Selector.colorBuild()
                .setDefaultColor(normalColor)
                .setPressedColor(accentColor)
                .setFocusedColor(accentColor)
                .setDisabledColor(disabledColor)
                .create()
            val layout = TextInputLayout(viewContext, null).apply {
                hint = label
                defaultHintTextColor = fieldColors
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val editor = CodeView(viewContext).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setSingleLine(false)
                minLines = 2
                maxLines = 7
                setPadding(
                    (12 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt()
                )
                when (syntax) {
                    CodeSyntax.NONE -> Unit
                    CodeSyntax.JSON -> addJsonPattern()
                    CodeSyntax.JS -> addJsPattern()
                    CodeSyntax.ALL -> {
                        addLegadoPattern()
                        addJsonPattern()
                        addJsPattern()
                    }
                }
                setText(value)
                isFocusable = true
                isFocusableInTouchMode = true
            }
            lateinit var state: CodeEditorState
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!state.suppress) state.onChange(s?.toString().orEmpty())
                }
            }
            state = CodeEditorState(false, { currentOnValueChange.value(it) }, watcher)
            editor.addTextChangedListener(watcher)
            editor.setTag(R.id.tag1, state)
            layout.addView(editor, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            editor.supportBackgroundTintList = fieldColors
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                editor.textCursorDrawable?.mutate()?.let { cursor ->
                    DrawableCompat.setTint(cursor, accentColor)
                    editor.textCursorDrawable = cursor
                }
            } else {
                TintHelper.setCursorTint(editor, accentColor)
            }
            layout
        },
        update = { layout ->
            val editor = layout.editText as? CodeView ?: return@AndroidView
            val state = editor.getTag(R.id.tag1) as? CodeEditorState ?: return@AndroidView
            state.onChange = { currentOnValueChange.value(it) }
            if (editor.text?.toString() != value) {
                val selection = editor.selectionStart.coerceAtLeast(0)
                state.suppress = true
                editor.setText(value)
                editor.setSelection(selection.coerceAtMost(editor.text?.length ?: 0))
                state.suppress = false
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun HttpTtsEditPreview() {
    LegadoTheme {
        HttpTtsEditContent(
            draft = HttpTTS(name = "Sample"), menuExpanded = false, unsavedDialog = false,
            loginHeader = null, showLoginHeader = false, onDraftChange = {}, onMenuExpandedChange = {},
            onMenuAction = {}, onUnsavedDialogChange = {}, onDiscard = {}, onLoginHeaderDismiss = {}
        )
    }
}
