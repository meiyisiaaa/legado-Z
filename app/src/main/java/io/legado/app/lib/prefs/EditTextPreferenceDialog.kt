package io.legado.app.lib.prefs

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.PreferenceDialogFragmentCompat
import io.legado.app.ui.widget.compose.LegadoTheme

class EditTextPreferenceDialog : EditTextPreferenceDialogFragmentCompat() {

    private var editorValue by mutableStateOf(TextFieldValue(""))
    private var editorInputType by mutableIntStateOf(InputType.TYPE_CLASS_TEXT)
    private var editorSingleLine by mutableStateOf(false)

    companion object {

        private const val SAVE_STATE_TEXT = "composeEditTextPreferenceDialog.text"
        private const val SAVE_STATE_SELECTION_START = "composeEditTextPreferenceDialog.selectionStart"
        private const val SAVE_STATE_SELECTION_END = "composeEditTextPreferenceDialog.selectionEnd"

        fun newInstance(key: String): EditTextPreferenceDialog {
            val fragment = EditTextPreferenceDialog()
            val b = Bundle(1)
            b.putString(PreferenceDialogFragmentCompat.ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probeView = super.onCreateDialogView(requireContext())
            ?: FrameLayout(requireContext()).apply {
                addView(EditText(context).apply { id = android.R.id.edit })
            }
        super.onBindDialogView(probeView)
        val probe = probeView.findViewById<EditText>(android.R.id.edit)
            ?: throw IllegalStateException(
                "Dialog view must contain an EditText with id @android:id/edit"
            )
        editorInputType = probe.inputType
        editorSingleLine = probe.maxLines == 1
        val initialText = savedInstanceState?.getString(SAVE_STATE_TEXT)
            ?: probe.text?.toString().orEmpty()
        val selectionStart = savedInstanceState
            ?.takeIf { it.containsKey(SAVE_STATE_SELECTION_START) }
            ?.getInt(SAVE_STATE_SELECTION_START)
            ?: probe.selectionStart
        val selectionEnd = savedInstanceState
            ?.takeIf { it.containsKey(SAVE_STATE_SELECTION_END) }
            ?.getInt(SAVE_STATE_SELECTION_END)
            ?: probe.selectionEnd
        val safeSelectionStart = selectionStart.takeIf { it >= 0 }?.coerceAtMost(initialText.length)
            ?: initialText.length
        val safeSelectionEnd = selectionEnd.takeIf { it >= 0 }?.coerceAtMost(initialText.length)
            ?: safeSelectionStart
        editorValue = TextFieldValue(
            text = initialText,
            selection = TextRange(safeSelectionStart, safeSelectionEnd)
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVE_STATE_TEXT, editorValue.text)
        outState.putInt(SAVE_STATE_SELECTION_START, editorValue.selection.start)
        outState.putInt(SAVE_STATE_SELECTION_END, editorValue.selection.end)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        createPreferenceComposeDialog(requireContext()) {
            val preference = getPreference()
            EditTextPreferenceDialogContent(
                title = preference.dialogTitle?.toString().orEmpty(),
                message = preference.dialogMessage?.toString(),
                value = editorValue,
                inputType = editorInputType,
                singleLine = editorSingleLine,
                cancelLabel = preference.negativeButtonText?.toString(),
                confirmLabel = preference.positiveButtonText?.toString(),
                onValueChange = { editorValue = it },
                onCancel = { closePreferenceDialog(false) },
                onConfirm = { closePreferenceDialog(true) }
            )
        }

    override fun onStart() {
        super.onStart()
        configurePreferenceComposeDialog(dialog, requireContext())
        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val preference = getPreference() as androidx.preference.EditTextPreference
        if (preference.callChangeListener(editorValue.text)) {
            preference.text = editorValue.text
        }
    }

    private fun closePreferenceDialog(positiveResult: Boolean) {
        onClick(
            requireDialog(),
            if (positiveResult) android.content.DialogInterface.BUTTON_POSITIVE
            else android.content.DialogInterface.BUTTON_NEGATIVE
        )
        dismiss()
    }

}

@Composable
private fun EditTextPreferenceDialogContent(
    title: String,
    message: String?,
    value: TextFieldValue,
    inputType: Int,
    singleLine: Boolean,
    cancelLabel: String?,
    confirmLabel: String?,
    onValueChange: (TextFieldValue) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    val keyboardType = inputType.toComposeKeyboardType()
    val isPassword = inputType.isPasswordInput()

    PreferenceDialogSurface(title = title, onClose = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = singleLine,
                label = { if (title.isNotBlank()) Text(title) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = inputType.toComposeCapitalization(),
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                ),
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        PreferenceDialogActions(
            cancelLabel = cancelLabel,
            confirmLabel = confirmLabel,
            onCancel = onCancel,
            onConfirm = onConfirm
        )
    }
}

private fun Int.toComposeKeyboardType(): KeyboardType {
    val inputClass = this and InputType.TYPE_MASK_CLASS
    val variation = this and InputType.TYPE_MASK_VARIATION
    return when (inputClass) {
        InputType.TYPE_CLASS_NUMBER -> when {
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> KeyboardType.NumberPassword
            this and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0 -> KeyboardType.Decimal
            else -> KeyboardType.Number
        }
        InputType.TYPE_CLASS_PHONE -> KeyboardType.Phone
        InputType.TYPE_CLASS_DATETIME -> KeyboardType.Number
        InputType.TYPE_CLASS_TEXT -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> KeyboardType.Email
            InputType.TYPE_TEXT_VARIATION_URI -> KeyboardType.Uri
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> KeyboardType.Password
            else -> KeyboardType.Text
        }
        else -> KeyboardType.Text
    }
}

private fun Int.isPasswordInput(): Boolean {
    val inputClass = this and InputType.TYPE_MASK_CLASS
    val variation = this and InputType.TYPE_MASK_VARIATION
    return when (inputClass) {
        InputType.TYPE_CLASS_TEXT -> variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

private fun Int.toComposeCapitalization(): KeyboardCapitalization {
    return when {
        this and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> KeyboardCapitalization.Characters
        this and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> KeyboardCapitalization.Words
        this and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> KeyboardCapitalization.Sentences
        else -> KeyboardCapitalization.None
    }
}

@Preview(showBackground = true)
@Composable
private fun EditTextPreferenceDialogPreview() {
    LegadoTheme {
        EditTextPreferenceDialogContent(
            title = "WebDAV password",
            message = "Enter the password used for backup sync.",
            value = TextFieldValue(""),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            singleLine = true,
            cancelLabel = "Cancel",
            confirmLabel = "OK",
            onValueChange = {},
            onCancel = {},
            onConfirm = {}
        )
    }
}
