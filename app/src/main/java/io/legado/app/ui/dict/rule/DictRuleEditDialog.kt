package io.legado.app.ui.dict.rule

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi

private data class DictRuleDraft(
    val name: TextFieldValue = TextFieldValue(),
    val urlRule: TextFieldValue = TextFieldValue(),
    val showRule: TextFieldValue = TextFieldValue()
)

class DictRuleEditDialog() : BaseDialogFragment(R.layout.dialog_dict_rule_edit, true) {

    val viewModel by viewModels<DictRuleEditViewModel>()
    private var draft by mutableStateOf(DictRuleDraft())
    private var focusedField by mutableStateOf<String?>(null)
    private var pendingFullEditField: String? = null

    constructor(name: String) : this() {
        arguments = Bundle().apply { putString("name", name) }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draftName", draft.name.text)
        outState.putInt("draftNameCursor", draft.name.selection.start)
        outState.putString("draftUrlRule", draft.urlRule.text)
        outState.putInt("draftUrlRuleCursor", draft.urlRule.selection.start)
        outState.putString("draftShowRule", draft.showRule.text)
        outState.putInt("draftShowRuleCursor", draft.showRule.selection.start)
        outState.putString("focusedField", focusedField)
        super.onSaveInstanceState(outState)
    }

    private val textEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val editedText = CodeTextTransfer.readResult(requireContext(), result.data) {
            toastOnUi(R.string.error_read_file)
        }
        if (result.resultCode == Activity.RESULT_OK && editedText != null) {
            val cursor = result.data?.getIntExtra("cursorPosition", -1)
                ?.coerceIn(0, editedText.length) ?: editedText.length
            val value = TextFieldValue(editedText, TextRange(cursor))
            draft = when (pendingFullEditField) {
                "name" -> draft.copy(name = value)
                "urlRule" -> draft.copy(urlRule = value)
                "showRule" -> draft.copy(showRule = value)
                else -> draft
            }
        }
        pendingFullEditField = null
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val composeView = view as ComposeView
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            LegadoTheme {
                DictRuleEditorContent(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onFocus = { focusedField = it },
                    onFullEdit = ::onFullEditClicked,
                    onSave = ::saveRule,
                    onCopy = { viewModel.copyRule(getDictRule()) },
                    onPaste = {
                        viewModel.pasteRule(::upRuleView)
                    }
                )
            }
        }
        viewModel.initData(arguments?.getString("name")) {
            val restored = savedInstanceState
            if (restored != null && restored.containsKey("draftName")) {
                draft = DictRuleDraft(
                    restored.textFieldValue("draftName", "draftNameCursor"),
                    restored.textFieldValue("draftUrlRule", "draftUrlRuleCursor"),
                    restored.textFieldValue("draftShowRule", "draftShowRuleCursor")
                )
                focusedField = restored.getString("focusedField")
            } else {
                upRuleView(viewModel.dictRule)
            }
        }
    }

    private fun Bundle.textFieldValue(textKey: String, cursorKey: String): TextFieldValue {
        val text = getString(textKey).orEmpty()
        val cursor = getInt(cursorKey, text.length).coerceIn(0, text.length)
        return TextFieldValue(text, TextRange(cursor))
    }

    private fun onFullEditClicked(field: String, value: TextFieldValue) {
        if (field !in setOf("name", "urlRule", "showRule")) {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
            return
        }
        focusedField = field
        pendingFullEditField = field
        val title = when (field) {
            "name" -> getString(R.string.name)
            "urlRule" -> getString(R.string.url_rule)
            else -> getString(R.string.show_rule)
        }
        val intent = Intent(requireActivity(), CodeEditActivity::class.java).apply {
            putExtra("title", title)
            putExtra("cursorPosition", value.selection.start)
        }
        CodeTextTransfer.launch(requireContext(), intent, value.text) {
            textEditLauncher.launch(it)
        }
    }

    private fun saveRule() {
        viewModel.save(getDictRule()) { dismissAllowingStateLoss() }
    }

    private fun upRuleView(dictRule: DictRule?) {
        draft = DictRuleDraft(
            name = TextFieldValue(dictRule?.name.orEmpty()),
            urlRule = TextFieldValue(dictRule?.urlRule.orEmpty()),
            showRule = TextFieldValue(dictRule?.showRule.orEmpty())
        )
    }

    private fun getDictRule(): DictRule {
        val dictRule = viewModel.dictRule?.copy() ?: DictRule()
        dictRule.name = draft.name.text
        dictRule.urlRule = draft.urlRule.text
        dictRule.showRule = draft.showRule.text
        return dictRule
    }

    private fun isSame(): Boolean {
        val dictRule = viewModel.dictRule ?: return draft.name.text.isEmpty()
        return dictRule.name == draft.name.text &&
            dictRule.urlRule == draft.urlRule.text &&
            dictRule.showRule == draft.showRule.text
    }

    override fun dismiss() {
        if (!isSame()) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) { super.dismiss() }
            }
        } else {
            super.dismiss()
        }
    }

    @Composable
    private fun DictRuleEditorContent(
        draft: DictRuleDraft,
        onDraftChange: (DictRuleDraft) -> Unit,
        onFocus: (String) -> Unit,
        onFullEdit: (String, TextFieldValue) -> Unit,
        onSave: () -> Unit,
        onCopy: () -> Unit,
        onPaste: () -> Unit
    ) {
        val latestDraft by rememberUpdatedState(draft)
        val latestOnDraftChange by rememberUpdatedState(onDraftChange)
        val latestOnFocus by rememberUpdatedState(onFocus)
        var menuExpanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dict_rule),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge
                        )
                        IconButton(
                            onClick = { onFullEdit(focusedField ?: "", focusedValue(draft)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_code),
                                contentDescription = stringResource(R.string.edit_content)
                            )
                        }
                        IconButton(onClick = onSave, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_save),
                                contentDescription = stringResource(R.string.action_save)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_more_vert),
                                    contentDescription = stringResource(R.string.more_menu)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_rule)) },
                                    onClick = { menuExpanded = false; onCopy() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.paste_rule)) },
                                    onClick = { menuExpanded = false; onPaste() }
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = draft.name,
                            onValueChange = { onDraftChange(draft.copy(name = it)) },
                            label = { Text(stringResource(R.string.name)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) onFocus("name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draft.urlRule,
                            onValueChange = { onDraftChange(draft.copy(urlRule = it)) },
                            label = { Text(stringResource(R.string.url_rule)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) onFocus("urlRule") },
                            minLines = 2
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.show_rule),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { context ->
                                    CodeView(context).apply {
                                        addLegadoPattern()
                                        addJsonPattern()
                                        addJsPattern()
                                        setText(draft.showRule.text)
                                        addTextChangedListener(object : TextWatcher {
                                            override fun beforeTextChanged(
                                                s: CharSequence?, start: Int, count: Int, after: Int
                                            ) = Unit

                                            override fun onTextChanged(
                                                s: CharSequence?, start: Int, before: Int, count: Int
                                            ) {
                                                val text = s?.toString().orEmpty()
                                                if (text != latestDraft.showRule.text) {
                                                    val cursor = selectionStart.coerceIn(0, text.length)
                                                    latestOnDraftChange(
                                                        latestDraft.copy(
                                                            showRule = TextFieldValue(text, TextRange(cursor))
                                                        )
                                                    )
                                                }
                                            }

                                            override fun afterTextChanged(s: Editable?) = Unit
                                        })
                                        setOnFocusChangeListener { _, hasFocus ->
                                            if (hasFocus) latestOnFocus("showRule")
                                        }
                                    }
                                },
                                update = { editor ->
                                    if (editor.text.toString() != draft.showRule.text) {
                                        editor.setText(draft.showRule.text)
                                        editor.setSelection(draft.showRule.selection.start.coerceIn(0, editor.text.length))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun focusedValue(draft: DictRuleDraft): TextFieldValue = when (focusedField) {
        "name" -> draft.name
        "urlRule" -> draft.urlRule
        "showRule" -> draft.showRule
        else -> TextFieldValue()
    }

    class DictRuleEditViewModel(application: Application) : BaseViewModel(application) {

        var dictRule: DictRule? = null

        fun initData(name: String?, onFinally: () -> Unit) {
            execute {
                if (dictRule == null && name != null) {
                    dictRule = appDb.dictRuleDao.getByName(name)
                }
            }.onFinally { onFinally() }
        }

        fun save(newDictRule: DictRule, onFinally: () -> Unit) {
            execute {
                dictRule?.let { appDb.dictRuleDao.delete(it) }
                appDb.dictRuleDao.insert(newDictRule)
                dictRule = newDictRule
            }.onFinally { onFinally() }
        }

        fun copyRule(dictRule: DictRule) {
            context.sendToClip(GSON.toJson(dictRule))
        }

        fun pasteRule(success: (DictRule) -> Unit) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                context.toastOnUi("剪贴板没有内容")
                return
            }
            execute {
                GSON.fromJsonObject<DictRule>(text).getOrThrow()
            }.onSuccess(block = { rule -> success(rule) })
                .onError { context.toastOnUi("格式不对") }
        }
    }
}

@Composable
private fun focusedValue(draft: DictRuleDraft, field: String?): TextFieldValue = when (field) {
    "name" -> draft.name
    "urlRule" -> draft.urlRule
    "showRule" -> draft.showRule
    else -> TextFieldValue()
}
