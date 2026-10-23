package io.legado.app.ui.book.toc.rule

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.localBook.TextFile
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ACache
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** TXT chapter-rule picker used by the reader's local-book TOC editor. */
class TxtTocRuleDialog() : BaseDialogFragment(0), TxtTocRuleEditDialog.Callback {

    constructor(tocRegex: String?) : this() {
        arguments = Bundle().apply { putString("tocRegex", tocRegex) }
    }

    private val importTocRuleKey = "tocRuleUrl"
    private val viewModel: TxtTocRuleViewModel by viewModels()
    private var rules by mutableStateOf<List<TxtTocRule>>(emptyList(), referentialEqualityPolicy())
    private var selectedName by mutableStateOf<String?>(null)
    private var durRegex: String? = null
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportTxtTocRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> showDialogFragment(ImportTxtTocRuleDialog(uri.toString())) }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.8f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        durRegex = arguments?.getString("tocRegex")
        return LegadoComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    TxtTocRulePickerContent(
                        rules = rules,
                        selectedName = selectedName,
                        onSelectedNameChange = { selectedName = it },
                        onAdd = { showDialogFragment(TxtTocRuleEditDialog()) },
                        onImport = ::handleImport,
                        onToggleEnabled = ::toggleEnabled,
                        onEdit = { showDialogFragment(TxtTocRuleEditDialog(it.id)) },
                        onDelete = ::confirmDelete,
                        onMove = ::moveRule,
                        onDragFinished = ::saveRuleOrder,
                        onCancel = { dismissAllowingStateLoss() },
                        onConfirm = ::confirmSelection
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.txtTocRuleDao.observeAll().catch {
                AppLog.put("TXT目录规则对话框获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { latest ->
                initSelectedName(latest)
                rules = latest
            }
        }
    }

    private fun initSelectedName(tocRules: List<TxtTocRule>) {
        if (selectedName == null && durRegex != null) {
            selectedName = tocRules.firstOrNull {
                durRegex == it.rule + TextFile.spaceChars + it.replacement
            }?.name ?: ""
        }
    }

    private fun handleImport(action: TxtTocRuleImportAction) {
        when (action) {
            TxtTocRuleImportAction.Local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }
            TxtTocRuleImportAction.Online -> showImportDialog()
            TxtTocRuleImportAction.QrCode -> qrCodeResult.launch()
            TxtTocRuleImportAction.Default -> viewModel.importDefault()
            TxtTocRuleImportAction.Help -> showHelp("txtTocRuleHelp")
        }
    }

    private fun toggleEnabled(rule: TxtTocRule, enabled: Boolean) {
        val updated = rule.copy(enable = enabled)
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) rules = rules.toMutableList().apply { this[index] = updated }
        viewModel.update(updated)
    }

    private fun confirmDelete(rule: TxtTocRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rule.name)
            noButton()
            yesButton { viewModel.del(rule) }
        }
    }

    private fun moveRule(from: Int, to: Int) {
        if (from !in rules.indices || to !in rules.indices || from == to) return
        rules = rules.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun saveRuleOrder() {
        rules.forEachIndexed { index, rule -> rule.serialNumber = index + 1 }
        viewModel.update(*rules.toTypedArray())
    }

    private fun confirmSelection() {
        val rule = rules.firstOrNull { selectedName == it.name } ?: return
        (activity as? CallBack)?.onTocRegexDialogResult(
            rule.rule + TextFile.spaceChars + rule.replacement
        )
        dismissAllowingStateLoss()
    }

    override fun saveTxtTocRule(txtTocRule: TxtTocRule) {
        viewModel.save(txtTocRule)
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val defaultUrl = "https://gitee.com/fisher52/YueDuJson/raw/master/myTxtChapterRule.json"
        val cacheUrls = aCache.getAsString(importTocRuleKey)
            ?.splitNotBlank(",")
            ?.toMutableList()
            ?: mutableListOf()
        if (!cacheUrls.contains(defaultUrl)) cacheUrls.add(0, defaultUrl)
        requireContext().alert(titleResource = R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(importTocRuleKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    if (url.isAbsUrl() && !cacheUrls.contains(url)) {
                        cacheUrls.add(0, url)
                        aCache.put(importTocRuleKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(ImportTxtTocRuleDialog(url))
                }
            }
            cancelButton()
        }
    }

    interface CallBack {
        fun onTocRegexDialogResult(tocRegex: String) {}
    }
}
