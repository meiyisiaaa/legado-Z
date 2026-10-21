package io.legado.app.ui.dict.rule

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.DictRule
import io.legado.app.databinding.ActivityDictRuleBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class DictRuleActivity : VMBaseActivity<ActivityDictRuleBinding, DictRuleViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack {

    override val viewModel by viewModels<DictRuleViewModel>()
    override val binding by viewBinding(ActivityDictRuleBinding::inflate)
    private val importRecordKey = "dictRuleUrls"
    private val rules = mutableStateListOf<DictRule>()
    private val selectedNames = mutableStateListOf<String>()
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportDictRuleDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportDictRuleDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                val url = uri.toString()
                if (url.isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                    val time = System.currentTimeMillis()
                    shibboleth {
                        val shibbolethUrl = StringUtils.toShibboleth(url, StringUtils.DICT_RULE, time)
                        alert(R.string.shibboleth) {
                            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                                editView.setText(shibbolethUrl)
                            }
                            customView { alertBinding.root }
                            okButton {
                                sendToClip(shibbolethUrl)
                            }
                        }
                    }
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(url)
                }
                customView { alertBinding.root }
                okButton {
                    sendToClip(url)
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initSelectActionView()
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                DictRuleContent(
                    rules = rules,
                    selectedNames = selectedNames,
                    onSelect = ::selectRule,
                    onToggle = { rule, enabled ->
                        rule.enabled = enabled
                        update(rule)
                    },
                    onEdit = ::edit,
                    onDelete = ::delete,
                    onMove = ::moveRule,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        observeDictRuleData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dict_rule, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    private fun initSelectActionView() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.dict_rule_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun observeDictRuleData() {
        lifecycleScope.launch {
            appDb.dictRuleDao.flowAll().catch {
                AppLog.put("字典规则获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                rules.clear()
                rules.addAll(it)
                val availableNames = it.mapTo(hashSetOf()) { rule -> rule.name }
                selectedNames.retainAll(availableNames)
                upCountView()
            }
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add -> showDialogFragment<DictRuleEditDialog>()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_import_qr -> qrCodeResult.launch()
            R.id.menu_import_default -> viewModel.importDefault()
            R.id.menu_help -> showHelp("dictRuleHelp")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_enable_selection -> {
                viewModel.enableSelection(*selection().toTypedArray())
            }

            R.id.menu_disable_selection -> {
                viewModel.disableSelection(*selection().toTypedArray())
            }

            R.id.menu_export_selection -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "exportDictRule.json",
                    GSON.toJson(selection()).toByteArray(),
                    "application/json"
                )
            }
        }
        return true
    }

    override fun onClickSelectBarMainAction() {
        viewModel.delete(*selection().toTypedArray())
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            selectedNames.clear()
            selectedNames.addAll(rules.map { it.name })
        } else {
            selectedNames.clear()
        }
        upCountView()
    }

    override fun revertSelection() {
        val selected = selectedNames.toSet()
        selectedNames.clear()
        selectedNames.addAll(rules.mapNotNull { it.name.takeIf { name -> name !in selected } })
        upCountView()
    }

    fun update(vararg rule: DictRule) {
        viewModel.update(*rule)
    }

    fun delete(rule: DictRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rule.name)
            noButton()
            yesButton {
                viewModel.delete(rule)
            }
        }
    }

    fun edit(rule: DictRule) {
        showDialogFragment(DictRuleEditDialog(rule.name))
    }

    fun upCountView() {
        binding.selectActionBar.upCountView(
            selectedNames.size,
            rules.size
        )
    }

    private fun selectRule(rule: DictRule, selected: Boolean) {
        if (selected) {
            if (rule.name !in selectedNames) selectedNames.add(rule.name)
        } else {
            selectedNames.remove(rule.name)
        }
        upCountView()
    }

    private fun selection(): List<DictRule> = rules.filter { it.name in selectedNames }

    private fun moveRule(rule: DictRule, offset: Int) {
        val index = rules.indexOfFirst { it.name == rule.name }
        if (index < 0) return
        val targetIndex = index + offset
        if (targetIndex !in rules.indices) return
        val target = rules[targetIndex]
        if (rule.sortNumber == target.sortNumber) {
            viewModel.upSortNumber()
        } else {
            val oldOrder = rule.sortNumber
            rule.sortNumber = target.sortNumber
            target.sortNumber = oldOrder
            update(rule, target)
        }
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(titleResource = R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(importRecordKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()
                text?.let {
                    if (it.isAbsUrl() && !cacheUrls.contains(it)) {
                        cacheUrls.add(0, it)
                        aCache.put(importRecordKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(
                        ImportDictRuleDialog(it)
                    )
                }
            }
            cancelButton()
        }
    }
}
