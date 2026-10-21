package io.legado.app.ui.book.import.local

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.book.import.BaseImportBookActivity
import io.legado.app.ui.book.import.ImportBrowserContent
import io.legado.app.ui.book.import.ImportBrowserItem
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isUri
import io.legado.app.utils.launch
import io.legado.app.utils.putPrefInt
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 导入本地书籍界面
 */
class ImportBookActivity : BaseImportBookActivity<ImportBookViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    ImportBookAdapter.CallBack,
    SelectActionBar.CallBack {

    override val viewModel by viewModels<ImportBookViewModel>()
    private val adapter by lazy { ImportBookAdapter(this, this) }
    private val browserItems = mutableStateOf<List<ImportBrowserItem>>(emptyList())
    private var currentItems: List<ImportBook> = emptyList()
    private var scanDocJob: Job? = null

    private val selectFolder = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            AppConfig.importBookPath = uri.toString()
            initRootDoc(true)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        searchView.queryHint = getString(R.string.screen) + " • " + getString(R.string.local_book)
        onBackPressedDispatcher.addCallback(this) {
            if (!goBackDir()) {
                finish()
            }
        }
        lifecycleScope.launch {
            initView()
            initEvent()
            if (setBookStorage() && AppConfig.importBookPath.isNullOrBlank()) {
                AppConfig.importBookPath = AppConfig.defaultBookTreeUri
            }
            initData()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.import_book, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_sort_name)?.isChecked = viewModel.sort == 0
        menu.findItem(R.id.menu_sort_size)?.isChecked = viewModel.sort == 1
        menu.findItem(R.id.menu_sort_time)?.isChecked = viewModel.sort == 2
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select_folder -> selectFolder.launch()
            R.id.menu_scan_folder -> scanFolder()
            R.id.menu_import_file_name -> alertImportFileName()
            R.id.menu_sort_name -> upSort(0)
            R.id.menu_sort_size -> upSort(1)
            R.id.menu_sort_time -> upSort(2)
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_del_selection -> deleteSelected()
        }
        return false
    }

    override fun selectAll(selectAll: Boolean) {
        adapter.selectAll(selectAll)
        refreshBrowserItems()
    }

    override fun revertSelection() {
        adapter.revertSelection()
        refreshBrowserItems()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onClickSelectBarMainAction() {
        viewModel.addToBookshelf(adapter.selected) {
            adapter.selected.forEach {
                it.isOnBookShelf = true
            }
            currentItems = adapter.getItems()
            adapter.selected.clear()
            adapter.notifyDataSetChanged()
            upCountView()
        }
    }

    private fun initView() {
        binding.importCompose.setContent {
            LegadoTheme {
                ImportBrowserContent(
                    items = browserItems.value,
                    emptyMessage = androidx.compose.ui.res.stringResource(R.string.empty_msg_import_book),
                    onItemClick = ::onBrowserItemClick,
                    onItemLongClick = {}
                )
            }
        }
        binding.layTop.setBackgroundColor(backgroundColor)
        binding.selectActionBar.setMainActionText(R.string.add_to_bookshelf)
        binding.selectActionBar.inflateMenu(R.menu.import_book_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initEvent() {
        binding.tvGoBack.setOnClickListener {
            goBackDir()
        }
    }

    private fun initData() {
        viewModel.dataFlowStart = {
            initRootDoc()
        }
        lifecycleScope.launch {
            viewModel.dataFlow.conflate().collect { docs ->
                adapter.setItems(docs)
                currentItems = docs
                refreshBrowserItems()
            }
        }
    }

    private fun initRootDoc(changedFolder: Boolean = false) {
        if (viewModel.rootDoc != null && !changedFolder) {
            upPath()
        } else {
            val lastPath = AppConfig.importBookPath
            if (lastPath.isNullOrBlank()) {
                selectFolder.launch()
            } else {
                val rootUri = if (lastPath.isUri()) {
                    lastPath.toUri()
                } else {
                    Uri.fromFile(File(lastPath))
                }
                when {
                    rootUri.isContentScheme() -> initRootPath(rootUri)
                    else -> initRootPath(rootUri.path!!)
                }
            }
        }
    }

    private fun initRootPath(rootUri: Uri) {
        kotlin.runCatching {
            val doc = DocumentFile.fromTreeUri(this, rootUri)
            if (doc == null || doc.name.isNullOrEmpty() || !doc.isDirectory) {
                selectFolder.launch()
            } else {
                viewModel.subDocs.clear()
                viewModel.rootDoc = FileDoc.fromDocumentFile(doc)
                upPath()
            }
        }.onFailure {
            selectFolder.launch()
        }
    }

    private fun initRootPath(path: String) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                kotlin.runCatching {
                    val file = File(path)
                    if (!file.isDirectory) {
                        selectFolder.launch()
                    } else {
                        viewModel.subDocs.clear()
                        viewModel.rootDoc = FileDoc.fromFile(file)
                        upPath()
                    }
                }.onFailure {
                    selectFolder.launch()
                }
            }
            .request()
    }

    private fun upSort(sort: Int) {
        viewModel.sort = sort
        putPrefInt(PreferKey.localBookImportSort, sort)
        if (scanDocJob?.isActive != true) {
            viewModel.dataCallback?.upAdapter()
        }
    }

    @Synchronized
    private fun upPath() {
        binding.tvGoBack.isEnabled = viewModel.subDocs.isNotEmpty()
        viewModel.rootDoc?.let {
            scanDocJob?.cancel()
            upDocs(it)
        }
    }

    private fun upDocs(rootDoc: FileDoc) {
        var path = rootDoc.name + File.separator
        var lastDoc = rootDoc
        for (doc in viewModel.subDocs) {
            lastDoc = doc
            path = path + doc.name + File.separator
        }
        binding.tvPath.text = path
        adapter.selected.clear()
        adapter.clearItems()
        currentItems = emptyList()
        refreshBrowserItems()
        upCountView()
        viewModel.loadDoc(lastDoc)
    }

    /**
     * 扫描当前文件夹及所有子文件夹
     */
    private fun scanFolder() {
        viewModel.rootDoc?.let { doc ->
            adapter.clearItems()
            currentItems = emptyList()
            refreshBrowserItems()
            upCountView()
            val lastDoc = viewModel.subDocs.lastOrNull() ?: doc
            binding.refreshProgressBar.isAutoLoading = true
            scanDocJob?.cancel()
            scanDocJob = lifecycleScope.launch(IO) {
                viewModel.scanDoc(lastDoc)
                withContext(Main) {
                    binding.refreshProgressBar.isAutoLoading = false
                }
            }
        }
    }

    private fun alertImportFileName() {
        alert(R.string.import_file_name) {
            setMessage("""使用js处理文件名变量src，将书名作者分别赋值到变量name author""")
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "js"
                editView.setText(AppConfig.bookImportFileName)
            }
            customView { alertBinding.root }
            okButton {
                AppConfig.bookImportFileName = alertBinding.editView.text?.toString()
            }
            cancelButton()
        }
    }

    @Synchronized
    override fun nextDoc(fileDoc: FileDoc) {
        viewModel.subDocs.add(fileDoc)
        upPath()
    }

    @Synchronized
    private fun goBackDir(): Boolean {
        return if (viewModel.subDocs.isNotEmpty()) {
            viewModel.subDocs.removeAt(viewModel.subDocs.lastIndex)
            upPath()
            true
        } else {
            false
        }
    }

    override fun onSearchTextChange(newText: String?) {
        viewModel.updateCallBackFlow(newText)
    }

    override fun upCountView() {
        refreshBrowserItems()
        binding.selectActionBar.upCountView(adapter.selected.size, adapter.checkableCount)
    }

    private fun onBrowserItemClick(index: Int) {
        currentItems.getOrNull(index)?.let { item ->
            when {
                item.isDir -> nextDoc(item.file)
                !item.isOnBookShelf -> {
                    if (!adapter.selected.add(item)) adapter.selected.remove(item)
                    upCountView()
                }
                else -> startRead(item.file)
            }
        }
    }

    private fun deleteSelected() {
        val deletedItems = HashSet(adapter.selected)
        viewModel.deleteDoc(adapter.selected) {
            adapter.removeSelection()
            adapter.selected.clear()
            currentItems = currentItems.filterNot { it in deletedItems }
            upCountView()
        }
    }

    private fun refreshBrowserItems() {
        browserItems.value = currentItems.map { item ->
            ImportBrowserItem(
                key = item.file.toString(),
                title = item.name,
                badge = item.name.substringAfterLast("."),
                summary = "${ConvertUtils.formatFileSize(item.size)} · ${AppConst.dateFormat.format(item.lastModified)}",
                isDirectory = item.isDir,
                inBookshelf = item.isOnBookShelf,
                selected = adapter.selected.contains(item)
            )
        }
    }

    override fun startRead(fileDoc: FileDoc) {
        if (!ArchiveUtils.isArchive(fileDoc.name)) {
            appDb.bookDao.getBookByFileName(fileDoc.name)?.let {
                val filePath = fileDoc.toString()
                if (it.bookUrl != filePath) {
                    it.bookUrl = filePath
                    appDb.bookDao.insert(it)
                }
                startReadBook(it)
            }
        } else {
            onArchiveFileClick(fileDoc)
        }
    }

}
