package io.legado.app.ui.widget.dialog

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout

class VariableDialog() : BaseDialogFragment(0, true) {

    private val viewModel by viewModels<ViewModel>()
    private var titleText by mutableStateOf("")
    private var commentText by mutableStateOf("")
    private var variableText by mutableStateOf("")

    constructor(title: String, key: String, variable: String?, comment: String) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("key", key)
            putString("variable", variable)
            putString("comment", comment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                VariableDialogContent(
                    title = titleText,
                    variable = variableText,
                    comment = commentText,
                    onVariableChange = { variableText = it },
                    onClose = ::dismiss,
                    onSave = {
                        callback?.setVariable(viewModel.key ?: "", variableText)
                        dismissAllowingStateLoss()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments ?: run {
            dismiss()
            return
        }
        titleText = args.getString("title").orEmpty()
        viewModel.init(args) {
            commentText = viewModel.comment.orEmpty()
            variableText = viewModel.variable.orEmpty()
        }
    }

    val callback get() = (parentFragment as? Callback) ?: (activity as? Callback)

    class ViewModel(application: Application) : BaseViewModel(application) {

        var key: String? = null
        var comment: String? = null
        var variable: String? = null

        fun init(arguments: Bundle, onFinally: () -> Unit) {
            if (key != null) return
            execute {
                key = arguments.getString("key")
                comment = arguments.getString("comment")
                variable = arguments.getString("variable")
            }.onFinally {
                onFinally.invoke()
            }
        }
    }

    interface Callback {
        fun setVariable(key: String, variable: String?)
    }
}

@Composable
internal fun VariableDialogContent(
    title: String,
    variable: String,
    comment: String,
    onVariableChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var editedVariable by rememberSaveable { mutableStateOf(variable) }
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(variable) {
        if (!hasUserEdited) editedVariable = variable
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .imePadding()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegadoDialogHeader(title, onClose) {
                    TextButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onSave()
                        }
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.action_save))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "variable",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                    OutlinedTextField(
                        value = editedVariable,
                        onValueChange = {
                            editedVariable = it
                            hasUserEdited = true
                            onVariableChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        keyboardOptions = KeyboardOptions.Default,
                        minLines = 1
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.variable_comment),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    SelectionContainer {
                        Text(
                            text = comment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VariableDialogPreview() {
    LegadoTheme {
        VariableDialogContent("Set variable", "", "变量说明", {}, {}, {})
    }
}
