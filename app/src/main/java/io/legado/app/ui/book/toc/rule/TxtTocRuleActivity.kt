package io.legado.app.ui.book.toc.rule

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.databinding.ActivityTxtTocRuleBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.StringUtils
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class TxtTocRuleActivity : VMBaseActivity<ActivityTxtTocRuleBinding, TxtTocRuleViewModel>(),
    TxtTocRuleEditDialog.Callback {

    override val viewModel by viewModels<TxtTocRuleViewModel>()
    override val binding by viewBinding(ActivityTxtTocRuleBinding::inflate)
    private val rules = mutableStateListOf<TxtTocRule>()
    private val selectedIds = mutableStateListOf<Long>()
    private val importTocRuleKey = "tocRuleUrl"
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportTxtTocRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> showDialogFragment(ImportTxtTocRuleDialog(uri.toString())) }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri -> showExportResult(uri.toString()) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                TxtTocRuleManageContent(
                    rules = rules.toList(),
                    selectedIds = selectedIds.toSet(),
                    onAdd = { showDialogFragment(TxtTocRuleEditDialog()) },
                    onImport = ::handleImport,
                    onSelectionChange = ::changeSelection,
                    onSelectAll = ::selectAll,
                    onReverseSelection = ::revertSelection,
                    onToggleEnabled = ::toggleEnabled,
                    onEdit = { showDialogFragment(TxtTocRuleEditDialog(it.id)) },
                    onDelete = ::confirmDelete,
                    onDeleteSelected = ::confirmDeleteSelection,
                    onEnableSelected = { viewModel.enableSelection(*selection().toTypedArray()) },
                    onDisableSelected = { viewModel.disableSelection(*selection().toTypedArray()) },
                    onExportSelected = ::exportSelection,
                    onMove = ::moveRule,
                    onDragFinished = ::saveRuleOrder,
                    onBack = ::finish
                )
            }
        }
        observeRuleData()
    }

    private fun observeRuleData() {
        lifecycleScope.launch {
            appDb.txtTocRuleDao.observeAll().catch {
                AppLog.put("TXT目录规则界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { latest ->
                rules.clear()
                rules.addAll(latest)
                val availableIds = latest.mapTo(hashSetOf()) { it.id }
                selectedIds.retainAll(availableIds)
            }
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
        if (index >= 0) rules[index] = updated
        viewModel.update(updated)
    }

    private fun changeSelection(id: Long, selected: Boolean) {
        if (selected && id !in selectedIds) selectedIds.add(id)
        else if (!selected) selectedIds.remove(id)
    }

    private fun selectAll(select: Boolean) {
        selectedIds.clear()
        if (select) selectedIds.addAll(rules.map { it.id })
    }

    private fun revertSelection() {
        val current = selectedIds.toSet()
        selectedIds.clear()
        selectedIds.addAll(rules.mapNotNull { it.id.takeIf { id -> id !in current } })
    }

    private fun selection(): List<TxtTocRule> = rules.filter { it.id in selectedIds }

    private fun confirmDelete(rule: TxtTocRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rule.name)
            noButton()
            yesButton {
                viewModel.del(rule)
                selectedIds.remove(rule.id)
            }
        }
    }

    private fun confirmDeleteSelection() {
        val selected = selection()
        if (selected.isEmpty()) return
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton {
                viewModel.del(*selected.toTypedArray())
                selectedIds.clear()
            }
            noButton()
        }
    }

    private fun moveRule(from: Int, to: Int) {
        if (from !in rules.indices || to !in rules.indices || from == to) return
        val item = rules.removeAt(from)
        rules.add(to, item)
    }

    private fun saveRuleOrder() {
        rules.forEachIndexed { index, rule -> rule.serialNumber = index + 1 }
        viewModel.update(*rules.toTypedArray())
    }

    private fun exportSelection() {
        val selected = selection()
        if (selected.isEmpty()) return
        exportResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "exportTxtTocRule.json",
                GSON.toJson(selected).toByteArray(),
                "application/json"
            )
        }
    }

    private fun showExportResult(url: String) {
        alert(R.string.export_success) {
            if (url.isAbsUrl()) {
                setMessage(DirectLinkUpload.getSummary())
                val time = System.currentTimeMillis()
                shibboleth {
                    val shibbolethUrl = StringUtils.toShibboleth(url, StringUtils.TOC_RULE, time)
                    alert(R.string.shibboleth) {
                        val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                            editView.setText(shibbolethUrl)
                        }
                        customView { alertBinding.root }
                        okButton { sendToClip(shibbolethUrl) }
                    }
                }
            }
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = getString(R.string.path)
                editView.setText(url)
            }
            customView { alertBinding.root }
            okButton { sendToClip(url) }
        }
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
        alert(titleResource = R.string.import_on_line) {
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

    override fun saveTxtTocRule(txtTocRule: TxtTocRule) {
        viewModel.save(txtTocRule)
    }
}
