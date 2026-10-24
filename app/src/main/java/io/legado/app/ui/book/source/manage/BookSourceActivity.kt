package io.legado.app.ui.book.source.manage

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.ActivityBookSourceBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.ui.ai.AiSourceAssistantDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.ACache
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.observeEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 书源管理界面
 */
class BookSourceActivity : VMBaseActivity<ActivityBookSourceBinding, BookSourceViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    BookSourceAdapter.CallBack,
    SelectActionBar.CallBack,
    SearchView.OnQueryTextListener {
    override val binding by viewBinding(ActivityBookSourceBinding::inflate)
    override val viewModel by viewModels<BookSourceViewModel>()
    private val importRecordKey = "bookSourceRecordKey"
    private val adapter by lazy { BookSourceAdapter(this, this, binding.recyclerView) }
    private val itemTouchCallback by lazy { ItemTouchCallback(adapter) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var sourceFlowJob: Job? = null
    private var checkMessageRefreshJob: Job? = null
    private val groups = linkedSetOf<String>()
    private var groupMenu: SubMenu? = null
    override var sort by mutableStateOf(BookSourceSort.Default)
        private set
    override var sortAscending by mutableStateOf(true)
        private set
    private var snackBar: Snackbar? = null
    private var groupSourcesByDomain by mutableStateOf(false)
    private val composeSources = mutableStateListOf<BookSourcePart>()
    private val composeGroups = mutableStateListOf<String>()
    private val composeDebugMessages = mutableStateMapOf<String, String>()
    private val selectedUrls = mutableStateListOf<String>()
    private var selectionVersion by mutableStateOf(0)
    private var currentSearch by mutableStateOf("")
    private var composeRoot: View? = null
    private val hostMap = hashMapOf<String, String>()
    private val qrResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportBookSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportBookSourceDialog(uri.toString()))
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
                        val shibbolethUrl = StringUtils.toShibboleth(url, StringUtils.BOOK_SOURCE, time)
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
    private val groupMenuLifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun onMenuOpened() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun onMenuClosed() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        upBookSource()
        initLiveDataGroup()
        initSelectActionBar()
        resumeCheckSource()
        if (!LocalConfig.bookSourcesHelpVersionIsLast) {
            showHelp("SourceMBookHelp")
        }
        setSupportActionBar(null)
        val root = LegadoComposeView(this).apply {
            id = R.id.legado_book_source_compose_root
            setContent {
                LegadoTheme {
                    BookSourceManageContent(
                        sources = composeSources,
                        groups = composeGroups,
                        query = currentSearch,
                        selectedUrls = selectedUrls.toSet(),
                        selectionVersion = selectionVersion,
                        sort = sort,
                        sortAscending = sortAscending,
                        groupByDomain = groupSourcesByDomain,
                        sourceHost = ::getSourceHost,
                        debugMessages = composeDebugMessages,
                        isChecking = Debug.isChecking,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        onQueryChange = ::setFilterQuery,
                        onTopAction = ::onTopAction,
                        onSort = ::selectSort,
                        onToggleSortDirection = {
                            sortAscending = !sortAscending
                            upBookSource(currentSearch)
                        },
                        onToggleGroupByDomain = {
                            groupSourcesByDomain = !groupSourcesByDomain
                            adapter.showSourceHost = groupSourcesByDomain
                            upBookSource(currentSearch)
                        },
                        onSelectionAction = ::performSelectionAction,
                        onSelectionChange = { source, selected ->
                            adapter.setSelected(source, selected)
                            syncSelection()
                        },
                        onEnabledChange = { source, enabled -> enable(enabled, source) },
                        onExploreChange = { source, enabled -> enableExplore(enabled, source) },
                        onEdit = ::edit,
                        onLogin = ::loginSource,
                        onMove = ::move,
                        onSearchBook = ::searchBook,
                        onDebug = ::debug,
                        onTop = ::toTop,
                        onBottom = ::toBottom,
                        onDelete = ::del,
                        onSelectAll = { adapter.selectAll(); syncSelection() },
                        onRevertSelection = { adapter.revertSelection(); syncSelection() },
                        onDeleteSelected = ::onClickSelectBarMainAction
                    )
                }
            }
        }
        composeRoot = root
        setContentView(root)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_source, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group).subMenu
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_book_source -> startActivity<BookSourceEditActivity>()
            R.id.menu_ai_source_assistant -> showDialogFragment(AiSourceAssistantDialog())
            R.id.menu_import_qr -> qrResult.launch()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json", "js")
            }

            R.id.menu_import_onLine -> showImportDialog()

            R.id.menu_sort_desc -> {
                sortAscending = !sortAscending
                item.isChecked = !sortAscending
                upBookSource(currentSearch)
            }

            R.id.menu_sort_manual -> {
                item.isChecked = true
                sort = BookSourceSort.Default
                upBookSource(currentSearch)
            }

            R.id.menu_sort_auto -> {
                item.isChecked = true
                sort = BookSourceSort.Weight
                upBookSource(currentSearch)
            }

            R.id.menu_sort_name -> {
                item.isChecked = true
                sort = BookSourceSort.Name
                upBookSource(currentSearch)
            }

            R.id.menu_sort_url -> {
                item.isChecked = true
                sort = BookSourceSort.Url
                upBookSource(currentSearch)
            }

            R.id.menu_sort_time -> {
                item.isChecked = true
                sort = BookSourceSort.Update
                upBookSource(currentSearch)
            }

            R.id.menu_sort_respondTime -> {
                item.isChecked = true
                sort = BookSourceSort.Respond
                upBookSource(currentSearch)
            }

            R.id.menu_sort_enable -> {
                item.isChecked = true
                sort = BookSourceSort.Enable
                upBookSource(currentSearch)
            }

            R.id.menu_enabled_group -> {
                setFilterQuery(getString(R.string.enabled))
            }

            R.id.menu_disabled_group -> {
                setFilterQuery(getString(R.string.disabled))
            }

            R.id.menu_group_login -> {
                setFilterQuery(getString(R.string.need_login))
            }

            R.id.menu_group_null -> {
                setFilterQuery(getString(R.string.no_group))
            }

            R.id.menu_enabled_explore_group -> {
                setFilterQuery(getString(R.string.enabled_explore))
            }

            R.id.menu_disabled_explore_group -> {
                setFilterQuery(getString(R.string.disabled_explore))
            }

            R.id.menu_group_sources_by_domain -> {
                item.isChecked = !item.isChecked
                groupSourcesByDomain = item.isChecked
                adapter.showSourceHost = item.isChecked
                upBookSource(currentSearch)
            }

            R.id.menu_help -> showHelp("SourceMBookHelp")
        }
        if (item.groupId == R.id.source_group) {
            setFilterQuery("group:${item.title}")
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun setFilterQuery(value: String) {
        currentSearch = value
        upBookSource(value)
    }

    private fun syncSelection() {
        selectedUrls.clear()
        selectedUrls.addAll(adapter.selection.map { it.bookSourceUrl })
        selectionVersion++
    }

    private fun selectSort(value: BookSourceSort) {
        sort = value
        upBookSource(currentSearch)
    }

    private fun onTopAction(itemId: Int) {
        when (itemId) {
            R.id.menu_add_book_source -> startActivity<BookSourceEditActivity>()
            R.id.menu_ai_source_assistant -> showDialogFragment(AiSourceAssistantDialog())
            R.id.menu_import_qr -> qrResult.launch()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json", "js")
            }
            R.id.menu_import_onLine -> showImportDialog()
            R.id.menu_group_sources_by_domain -> {
                groupSourcesByDomain = !groupSourcesByDomain
                adapter.showSourceHost = groupSourcesByDomain
                upBookSource(currentSearch)
            }
            R.id.menu_help -> showHelp("SourceMBookHelp")
        }
    }

    private fun loginSource(source: BookSourcePart) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", source.bookSourceUrl)
        }
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.search_book_source)
        searchView.setOnQueryTextListener(this)
    }


    private fun upBookSource(searchKey: String? = currentSearch.ifBlank { null }) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> {
                    appDb.bookSourceDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.bookSourceDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.bookSourceDao.flowDisabled()
                }

                searchKey == getString(R.string.need_login) -> {
                    appDb.bookSourceDao.flowLogin()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.bookSourceDao.flowNoGroup()
                }

                searchKey == getString(R.string.enabled_explore) -> {
                    appDb.bookSourceDao.flowEnabledExplore()
                }

                searchKey == getString(R.string.disabled_explore) -> {
                    appDb.bookSourceDao.flowDisabledExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupSearch(key)
                }

                else -> {
                    appDb.bookSourceDao.flowSearch(searchKey)
                }
            }.map { data ->
                hostMap.clear()
                if (groupSourcesByDomain) {
                    data.sortedWith(
                        compareBy<BookSourcePart> { getSourceHost(it.bookSourceUrl) == "#" }
                            .thenBy { getSourceHost(it.bookSourceUrl) }
                            .thenByDescending { it.lastUpdateTime })
                } else if (sortAscending) {
                    when (sort) {
                        BookSourceSort.Weight -> data.sortedBy { it.weight }
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o1.bookSourceName.cnCompare(o2.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                        BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sort = -o1.enabled.compareTo(o2.enabled)
                            if (sort == 0) {
                                sort = o1.bookSourceName.cnCompare(o2.bookSourceName)
                            }
                            sort
                        }

                        else -> data
                    }
                } else {
                    when (sort) {
                        BookSourceSort.Weight -> data.sortedByDescending { it.weight }
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o2.bookSourceName.cnCompare(o1.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                        BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sort = o1.enabled.compareTo(o2.enabled)
                            if (sort == 0) {
                                sort = o1.bookSourceName.cnCompare(o2.bookSourceName)
                            }
                            sort
                        }

                        else -> data.reversed()
                    }
                }
            }.flowWithLifecycleAndDatabaseChange(
                lifecycle,
                table = AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("书源界面更新书源出错", it)
            }.flowOn(IO).conflate().collect { data ->
                val previouslySelected = adapter.selection.map { it.bookSourceUrl }.toSet()
                adapter.setItems(data, adapter.diffItemCallback, !Debug.isChecking)
                composeSources.clear()
                composeSources.addAll(data)
                refreshComposeDebugMessages()
                selectedUrls.clear()
                selectedUrls.addAll(data.filter { it.bookSourceUrl in previouslySelected }.map { it.bookSourceUrl })
                selectionVersion++
                itemTouchCallback.isCanDrag =
                    sort == BookSourceSort.Default && !groupSourcesByDomain
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.upResumed(true)
    }

    override fun onPause() {
        adapter.upResumed(false)
        super.onPause()
    }


    private fun initLiveDataGroup() {
        lifecycleScope.launch {
            appDb.bookSourceDao.flowGroups()
                .flowWithLifecycleAndDatabaseChange(
                    lifecycle,
                    table = AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .flowWithLifecycleAndDatabaseChangeFirst(
                    groupMenuLifecycleOwner.lifecycle,
                    table = AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    composeGroups.clear()
                    composeGroups.addAll(it)
                    upGroupMenu()
                    delay(500)
                }
        }
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
        syncSelection()
    }

    override fun revertSelection() {
        adapter.revertSelection()
        syncSelection()
    }

    override fun onClickSelectBarMainAction() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(adapter.selection) }
            noButton()
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        if (menu === groupMenu) {
            groupMenuLifecycleOwner.onMenuOpened()
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        super.onPanelClosed(featureId, menu)
        if (menu === groupMenu) {
            groupMenuLifecycleOwner.onMenuClosed()
        }
    }

    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.book_source_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        item?.itemId?.let(::performSelectionAction)
        return true
    }

    private fun performSelectionAction(itemId: Int) {
        when (itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_enable_explore -> viewModel.enableSelectExplore(adapter.selection)
            R.id.menu_disable_explore -> viewModel.disableSelectExplore(adapter.selection)
            R.id.menu_check_source -> checkSource()
            R.id.menu_top_sel -> viewModel.topSource(*adapter.selection.toTypedArray())
            R.id.menu_bottom_sel -> viewModel.bottomSource(*adapter.selection.toTypedArray())
            R.id.menu_add_group -> selectionAddToGroups()
            R.id.menu_remove_group -> selectionRemoveFromGroups()
            R.id.menu_export_selection -> viewModel.saveToFile(
                adapter,
                currentSearch,
                sortAscending,
                sort
            ) { file, name ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        name,
                        file,
                        "application/json"
                    )
                }
            }

            R.id.menu_export_main_js -> viewModel.saveMainJsToFile(adapter.selection) { file, name ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        name,
                        file,
                        "text/javascript"
                    )
                }
            }

            R.id.menu_share_source -> viewModel.saveToFile(
                adapter,
                currentSearch,
                sortAscending,
                sort
            ) { file, name ->
                share(file)
            }

            R.id.menu_check_selected_interval -> adapter.checkSelectedInterval()
        }
    }

    @SuppressLint("InflateParams")
    private fun checkSource() {
        val dialog = alert(titleResource = R.string.search_book_key) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "search word"
                editView.setText(CheckSource.keyword)
            }
            customView { alertBinding.root }
            okButton {
                keepScreenOn(true)
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        CheckSource.keyword = it
                    }
                }
                val selectItems = adapter.selection
                CheckSource.start(this@BookSourceActivity, selectItems)
                val adapterItems = adapter.getItems()
                val firstItem = adapterItems.indexOf(selectItems.firstOrNull())
                val lastItem = adapterItems.indexOf(selectItems.lastOrNull())
                startCheckMessageRefreshJob(firstItem, lastItem)
            }
            neutralButton(R.string.check_source_config)
            cancelButton()
        }
        //手动设置监听 避免点击打开校验设置后对话框关闭
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            showDialogFragment<CheckSourceConfig>()
        }
    }

    private fun resumeCheckSource() {
        if (!Debug.isChecking) {
            return
        }
        keepScreenOn(true)
        CheckSource.resume(this)
        startCheckMessageRefreshJob(0, 0)
    }

    @SuppressLint("InflateParams")
    private fun selectionAddToGroups() {
        alert(titleResource = R.string.add_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionAddToGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun selectionRemoveFromGroups() {
        alert(titleResource = R.string.remove_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionRemoveFromGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.source_group)
        groups.forEach {
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, it)
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
                    showDialogFragment(ImportBookSourceDialog(it))
                }
            }
            cancelButton()
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.CHECK_SOURCE) { msg ->
            snackBar?.setText(msg) ?: let {
                snackBar = Snackbar
                    .make(composeRoot ?: binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.cancel) {
                        CheckSource.stop(this)
                    }.apply { show() }
            }
        }
        observeEvent<Int>(EventBus.CHECK_SOURCE_DONE) {
            keepScreenOn(false)
            snackBar?.dismiss()
            snackBar = null
            refreshComposeDebugMessages()
            adapter.notifyItemRangeChanged(
                0,
                adapter.itemCount,
                bundleOf(Pair("checkSourceMessage", null))
            )
            groups.forEach { group ->
                if (group.contains("失效") && currentSearch.isEmpty()) {
                    setFilterQuery("失效")
                    toastOnUi("发现有失效书源，已为您自动筛选！")
                }
            }
        }
    }

    private fun startCheckMessageRefreshJob(firstItem: Int, lastItem: Int) {
        checkMessageRefreshJob?.cancel()
        checkMessageRefreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshComposeDebugMessages()
                    if (lastItem == 0) {
                        adapter.notifyItemRangeChanged(
                            0,
                            adapter.itemCount,
                            bundleOf(Pair("checkSourceMessage", null))
                        )
                    } else {
                        adapter.notifyItemRangeChanged(
                            firstItem,
                            lastItem + 1,
                            bundleOf(Pair("checkSourceMessage", null))
                        )
                    }
                    if (!Debug.isChecking) {
                        checkMessageRefreshJob?.cancel()
                    }
                    delay(300L)
                }
            }
        }
    }

    private fun refreshComposeDebugMessages() {
        composeSources.forEach { source ->
            val message = Debug.debugMessageMap[source.bookSourceUrl]
            if (message.isNullOrBlank()) {
                composeDebugMessages.remove(source.bookSourceUrl)
            } else {
                composeDebugMessages[source.bookSourceUrl] = message
            }
        }
    }

    /**
     * 保持亮屏
     */
    private fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun upCountView() {
        binding.selectActionBar
            .upCountView(adapter.selection.size, adapter.itemCount)
    }

    override fun getSourceHost(origin: String): String {
        return hostMap.getOrPut(origin) {
            NetworkUtils.getSubDomainOrNull(origin) ?: "#"
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        newText?.let {
            setFilterQuery(it)
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun del(bookSource: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + bookSource.bookSourceName)
            noButton()
            yesButton {
                viewModel.del(listOf(bookSource))
            }
        }
    }

    override fun edit(bookSource: BookSourcePart) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", bookSource.bookSourceUrl)
        }
    }

    override fun move(
        sourceUrl: String,
        targetUrl: String,
        after: Boolean,
        onFinally: () -> Unit
    ) {
        viewModel.move(sourceUrl, targetUrl, if (sortAscending) after else !after, onFinally)
    }

    override fun reload() {
        upBookSource(currentSearch)
    }

    override fun enable(enable: Boolean, bookSource: BookSourcePart) {
        viewModel.enable(enable, listOf(bookSource))
    }

    override fun enableExplore(enable: Boolean, bookSource: BookSourcePart) {
        viewModel.enableExplore(enable, listOf(bookSource))
    }

    override fun toTop(bookSource: BookSourcePart) {
        if (sortAscending) {
            viewModel.topSource(bookSource)
        } else {
            viewModel.bottomSource(bookSource)
        }
    }

    override fun toBottom(bookSource: BookSourcePart) {
        if (sortAscending) {
            viewModel.bottomSource(bookSource)
        } else {
            viewModel.topSource(bookSource)
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(this, bookSource)
    }

    override fun debug(bookSource: BookSourcePart) {
        startActivity<BookSourceDebugActivity> {
            putExtra("key", bookSource.bookSourceUrl)
        }
    }

    override fun finish() {
        if (currentSearch.isNullOrEmpty()) {
            super.finish()
        } else {
            setFilterQuery("")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!Debug.isChecking) {
            Debug.debugMessageMap.clear()
        }
    }

}
