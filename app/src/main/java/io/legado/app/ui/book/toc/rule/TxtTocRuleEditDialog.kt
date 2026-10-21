package io.legado.app.ui.book.toc.rule

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getClipText
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TxtTocRuleEditDialog() : BaseDialogFragment(0) {

    constructor(id: Long?) : this() {
        id ?: return
        arguments = Bundle().apply { putLong("id", id) }
    }

    private val viewModel by viewModels<ViewModel>()
    private val callback get() = (parentFragment as? Callback) ?: activity as? Callback
    private var originalRule: TxtTocRule? = null
    private var draft by mutableStateOf<TxtTocRule?>(null)
    private var loaded by mutableStateOf(false)
    private var validationError by mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                TxtTocRuleEditorContent(
                    rule = draft,
                    loading = !loaded,
                    error = validationError,
                    onRuleChange = {
                        draft = it
                        validationError = null
                    },
                    onSave = ::save,
                    onCopy = ::copyRule,
                    onPaste = ::pasteRule,
                    onClose = { dismiss() }
                )
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val id = arguments?.takeIf { it.containsKey("id") }?.getLong("id")
        viewModel.initData(id) { loadedRule ->
            originalRule = loadedRule?.copy()
            draft = loadedRule?.copy() ?: TxtTocRule()
            loaded = true
        }
    }

    private fun save() {
        val rule = draft ?: return
        if (rule.name.isEmpty()) {
            validationError = "名称不能为空"
            requireContext().toastOnUi("名称不能为空")
            return
        }
        try {
            Pattern.compile(rule.rule, Pattern.MULTILINE)
        } catch (ex: PatternSyntaxException) {
            AppLog.put("正则语法错误或不支持(txt)：${ex.localizedMessage}", ex, true)
            validationError = ex.localizedMessage ?: "正则语法错误"
            return
        }
        callback?.saveTxtTocRule(rule)
        dismissAllowingStateLoss()
    }

    private fun copyRule() {
        draft?.let { requireContext().sendToClip(GSON.toJson(it)) }
    }

    private fun pasteRule() {
        viewModel.pasteRule { pasted ->
            draft = pasted.copy()
            validationError = null
        }
    }

    private fun isSame(): Boolean {
        val current = draft ?: return true
        val original = originalRule ?: return current.name.isEmpty()
        return original.name == current.name &&
            original.rule == current.rule &&
            original.replacement == current.replacement &&
            original.example == current.example
    }

    override fun dismiss() {
        if (!isSame()) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes) { super.dismiss() }
                negativeButton(R.string.no)
            }
        } else {
            super.dismiss()
        }
    }

    class ViewModel(application: Application) : BaseViewModel(application) {

        var tocRule: TxtTocRule? = null

        fun initData(id: Long?, finally: (tocRule: TxtTocRule?) -> Unit) {
            if (tocRule != null) return
            execute {
                if (id == null) return@execute
                tocRule = appDb.txtTocRuleDao.get(id)
            }.onFinally {
                finally.invoke(tocRule)
            }
        }

        fun pasteRule(success: (TxtTocRule) -> Unit) {
            execute(context = Dispatchers.Main) {
                val text = context.getClipText()
                if (text.isNullOrBlank()) throw NoStackTraceException("剪贴板为空")
                GSON.fromJsonObject<TxtTocRule>(text).getOrNull()
                    ?: throw NoStackTraceException("格式不对")
            }.onSuccess {
                success.invoke(it)
            }.onError {
                context.toastOnUi(it.localizedMessage ?: "Error")
                it.printOnDebug()
            }
        }
    }

    interface Callback {
        fun saveTxtTocRule(txtTocRule: TxtTocRule)
    }
}
