package io.legado.app.ui.book.read.config

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi

class HttpTtsEditDialog() : BaseDialogFragment(R.layout.dialog_http_tts_edit, true) {

    constructor(id: Long) : this() {
        arguments = Bundle().apply { putLong("id", id) }
    }

    private val viewModel by viewModels<HttpTtsEditViewModel>()
    private var draft by mutableStateOf(HttpTTS())
    private var menuExpanded by mutableStateOf(false)
    private var unsavedDialog by mutableStateOf(false)
    private var loginHeader by mutableStateOf<String?>(null)
    private var showLoginHeader by mutableStateOf(false)
    private var focusedEditText: EditText? = null

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val editedText = CodeTextTransfer.readResult(requireContext(), result.data) {
            toastOnUi(R.string.error_read_file)
        }
        if (result.resultCode == RESULT_OK) {
            val view = focusedEditText
            if (view == null) {
                toastOnUi(R.string.focus_lost_on_textbox)
                return@registerForActivityResult
            }
            view.requestFocus()
            editedText?.let { view.setText(it) }
            result.data?.getIntExtra("cursorPosition", -1)
                ?.takeIf { it in 0..(view.text?.length ?: 0) }
                ?.let(view::setSelection)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    HttpTtsEditContent(
                        draft = draft,
                        menuExpanded = menuExpanded,
                        unsavedDialog = unsavedDialog,
                        loginHeader = loginHeader,
                        showLoginHeader = showLoginHeader,
                        onDraftChange = { draft = it },
                        onMenuExpandedChange = { menuExpanded = it },
                        onMenuAction = ::onMenuAction,
                        onUnsavedDialogChange = { unsavedDialog = it },
                        onDiscard = ::discardChanges,
                        onLoginHeaderDismiss = { showLoginHeader = false }
                    )
                }
            }
        }
        viewModel.initData(arguments) { draft = it.copy() }
    }

    private fun onMenuAction(action: HttpTtsMenuAction) {
        menuExpanded = false
        when (action) {
            HttpTtsMenuAction.FULL_EDIT -> onFullEditClicked()
            HttpTtsMenuAction.SAVE -> saveDraft()
            HttpTtsMenuAction.LOGIN -> {
                val item = dataFromDraft()
                if (item.loginUrl.isNullOrBlank()) {
                    toastOnUi("登录url不能为空")
                } else {
                    viewModel.save(item) {
                        startActivity<SourceLoginActivity> {
                            putExtra("type", "httpTts")
                            putExtra("key", item.id.toString())
                        }
                    }
                }
            }
            HttpTtsMenuAction.SHOW_LOGIN_HEADER -> {
                loginHeader = dataFromDraft().getLoginHeader()
                showLoginHeader = true
            }
            HttpTtsMenuAction.DELETE_LOGIN_HEADER -> {
                val item = dataFromDraft()
                item.removeLoginHeader()
                draft = item
            }
            HttpTtsMenuAction.COPY_SOURCE -> requireContext().sendToClip(GSON.toJson(dataFromDraft()))
            HttpTtsMenuAction.PASTE_SOURCE -> viewModel.importFromClip { draft = it.copy(id = viewModel.id ?: draft.id) }
            HttpTtsMenuAction.LOG -> showDialogFragment<AppLogDialog>()
            HttpTtsMenuAction.HELP -> showHelp("httpTTSHelp")
        }
    }

    private fun onFullEditClicked() {
        val view = dialog?.window?.decorView?.findFocus()
        if (view is CodeView) {
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            focusedEditText = view
            val intent = Intent(requireActivity(), CodeEditActivity::class.java).apply {
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
            }
            CodeTextTransfer.launch(requireContext(), intent, view.text?.toString().orEmpty()) {
                textEditLauncher.launch(it)
            }
        } else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    private fun dataFromDraft(): HttpTTS = draft.copy(id = viewModel.id ?: System.currentTimeMillis())

    private fun saveDraft() {
        viewModel.save(dataFromDraft()) {
            dismissAllowingStateLoss()
            toastOnUi("保存成功")
        }
    }

    private fun isSame(): Boolean {
        val original = viewModel.httpTTS ?: return dataFromDraft().equal(HttpTTS(id = dataFromDraft().id))
        return dataFromDraft().equal(original)
    }

    override fun dismiss() {
        if (!isSame()) {
            unsavedDialog = true
        } else {
            super.dismiss()
        }
    }

    private fun discardChanges() {
        unsavedDialog = false
        super.dismiss()
    }
}
