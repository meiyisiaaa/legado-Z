package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.IntentData
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.disableEdit
import io.legado.app.utils.setLayout

class CodeDialog() : BaseDialogFragment(0) {

    constructor(code: String, disableEdit: Boolean = true, requestId: String? = null) : this() {
        arguments = Bundle().apply {
            putBoolean("disableEdit", disableEdit)
            putString("code", IntentData.put(code))
            putString("requestId", requestId)
        }
    }

    private var codeView: CodeView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                val args = arguments
                val disableEdit = args?.getBoolean("disableEdit") == true
                val title = if (disableEdit) "code view" else "code edit"
                CodeDialogContent(
                    title = title,
                    code = IntentData.get<String>(args?.getString("code")).orEmpty(),
                    disableEdit = disableEdit,
                    onCodeViewCreated = { codeView = it },
                    onSave = {
                        codeView?.text?.toString()?.let { code ->
                            val requestId = args?.getString("requestId")
                            (parentFragment as? Callback)?.onCodeSave(code, requestId)
                                ?: (activity as? Callback)?.onCodeSave(code, requestId)
                        }
                        dismiss()
                    },
                    onClose = ::dismiss
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    override fun onDestroyView() {
        codeView = null
        super.onDestroyView()
    }

    interface Callback {
        fun onCodeSave(code: String, requestId: String?)
    }
}

@Composable
internal fun CodeDialogContent(
    title: String,
    code: String,
    disableEdit: Boolean,
    onCodeViewCreated: (CodeView) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column {
            LegadoDialogHeader(title, onClose) {
                if (!disableEdit) {
                    androidx.compose.material3.TextButton(onClick = onSave) {
                        androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.action_save))
                    }
                }
            }
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                factory = { context ->
                    CodeView(context).apply {
                        id = R.id.code_view
                        setPadding(
                            (12 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt()
                        )
                        addLegadoPattern()
                        addJsonPattern()
                        addJsPattern()
                        if (disableEdit) this.disableEdit()
                        setText(code)
                        onCodeViewCreated(this)
                    }
                },
                update = { view ->
                    if (view.text?.toString() != code) view.setText(code)
                    if (disableEdit) view.disableEdit()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CodeDialogPreview() {
    LegadoTheme { CodeDialogContent("code view", "const value = 1", true, {}, {}, {}) }
}
