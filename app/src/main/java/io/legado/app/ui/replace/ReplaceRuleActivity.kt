package io.legado.app.ui.replace

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.ActivityReplaceRuleBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ACache
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 替换规则管理
 */
class ReplaceRuleActivity : VMBaseActivity<ActivityReplaceRuleBinding, ReplaceRuleViewModel>(),
    SearchView.OnQueryTextListener,
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack {
    override val binding by viewBinding(ActivityReplaceRuleBinding::inflate)
    override val viewModel by viewModels<ReplaceRuleViewModel>()
    private val importRecordKey = "replaceRuleRecordKey"
    private val rules = mutableStateListOf<ReplaceRule>()
    private val selectedIds = mutableStateListOf<Long>()
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var groups = arrayListOf<String>()
    private var groupMenu: SubMenu? = null
    private var replaceRuleFlowJob: Job? = null
    private var dataInit = false
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportReplaceRuleDialog(it))
    }
    private val editActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_OK)
            }
        }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportReplaceRuleDialog(uri.toString()))
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
                        val shibbolethUrl = StringUtils.toShibboleth(url, StringUtils.REPLACE_RULE, time)
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
        initSearchView()
        initSelectActionView()
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                ReplaceRuleContent(
                    rules = rules,
                    selectedIds = selectedIds,
                    onSelect = ::selectRule,
                    onToggle = { rule, enabled ->
                        rule.isEnabled = enabled
                        update(rule)
                    },
                    onEdit = ::edit,
                    onDelete = ::delete,
                    onTop = ::toTop,
                    onBottom = ::toBottom,
                    onMove = ::moveAdjacent,
                    onAdd = ::addReplaceRule,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        observeReplaceRuleData()
        observeGroupData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.replace_rule, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.replace_purify_search)
        searchView.setOnQueryTextListener(this)
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            selectedIds.clear()
            selectedIds.addAll(rules.map { it.id })
        } else {
            selectedIds.clear()
        }
        upCountView()
    }

    override fun revertSelection() {
        val selected = selectedIds.toSet()
        selectedIds.clear()
        selectedIds.addAll(rules.mapNotNull { it.id.takeIf { id -> id !in selected } })
        upCountView()
    }

    override fun onClickSelectBarMainAction() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.delSelection(selection()) }
            noButton()
        }
    }

    private fun initSelectActionView() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.replace_rule_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun observeReplaceRuleData(searchKey: String? = null) {
        dataInit = false
        replaceRuleFlowJob?.cancel()
        replaceRuleFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> {
                    appDb.replaceRuleDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.replaceRuleDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.replaceRuleDao.flowDisabled()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.replaceRuleDao.flowNoGroup()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.replaceRuleDao.flowGroupSearch("%$key%")
                }

                else -> {
                    appDb.replaceRuleDao.flowSearch("%$searchKey%")
                }
            }.catch {
                AppLog.put("替换规则管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect {
                if (dataInit) {
                    setResult(RESULT_OK)
                }
                rules.clear()
                rules.addAll(it)
                selectedIds.retainAll(it.mapTo(hashSetOf()) { rule -> rule.id })
                upCountView()
                dataInit = true
                delay(100)
            }
        }
    }

    private fun observeGroupData() {
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowGroups().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_replace_rule -> addReplaceRule()

            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_enabled_group -> {
                searchView.setQuery(getString(R.string.enabled), true)
            }

            R.id.menu_disabled_group -> {
                searchView.setQuery(getString(R.string.disabled), true)
            }
            R.id.menu_del_selection -> viewModel.delSelection(selection())
            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_qr -> qrCodeResult.launch()
            R.id.menu_help -> showHelp("replaceRuleHelp")
            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            else -> if (item.groupId == R.id.replace_group) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(selection())
            R.id.menu_disable_selection -> viewModel.disableSelection(selection())
            R.id.menu_top_sel -> viewModel.topSelect(selection())
            R.id.menu_bottom_sel -> viewModel.bottomSelect(selection())
            R.id.menu_export_selection -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "exportReplaceRule.json",
                    GSON.toJson(selection()).toByteArray(),
                    "application/json"
                )
            }
        }
        return false
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.replace_group)
        groups.forEach {
            menu.add(R.id.replace_group, Menu.NONE, Menu.NONE, it)
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
                        ImportReplaceRuleDialog(it)
                    )
                }
            }
            cancelButton()
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        observeReplaceRuleData(newText)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async { ContentProcessor.upReplaceRules() }
    }

    fun upCountView() {
        binding.selectActionBar.upCountView(
            selectedIds.size,
            rules.size
        )
    }

    fun update(vararg rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.update(*rule)
    }

    fun delete(rule: ReplaceRule) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rule.name)
            noButton()
            yesButton {
                setResult(RESULT_OK)
                viewModel.delete(rule)
            }
        }
    }

    fun edit(rule: ReplaceRule) {
        setResult(RESULT_OK)
        editActivity.launch(ReplaceEditActivity.startIntent(this, rule.id))
    }

    private fun addReplaceRule() {
        editActivity.launch(ReplaceEditActivity.startIntent(this))
    }

    fun toTop(rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.toTop(rule)
    }

    fun toBottom(rule: ReplaceRule) {
        setResult(RESULT_OK)
        viewModel.toBottom(rule)
    }

    fun move(
        ruleId: Long,
        targetId: Long,
        after: Boolean,
        onFinally: () -> Unit
    ) {
        setResult(RESULT_OK)
        viewModel.move(ruleId, targetId, after, onFinally)
    }

    fun reload() {
        observeReplaceRuleData(searchView.query?.toString())
    }

    private fun selectRule(rule: ReplaceRule, selected: Boolean) {
        if (selected) {
            if (rule.id !in selectedIds) selectedIds.add(rule.id)
        } else {
            selectedIds.remove(rule.id)
        }
        upCountView()
    }

    private fun selection(): List<ReplaceRule> = rules.filter { it.id in selectedIds }

    private fun moveAdjacent(rule: ReplaceRule, offset: Int) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        val target = rules.getOrNull(index + offset) ?: return
        move(rule.id, target.id, offset > 0) {}
    }
}
