package io.legado.app.ui.book.import.remote

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.constant.AppConst
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.remote.RemoteBook
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.import.BaseImportBookActivity
import io.legado.app.ui.book.import.ImportBrowserContent
import io.legado.app.ui.book.import.ImportBrowserItem
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.find
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.io.File

/**
 * 展示远程书籍
 */
class RemoteBookActivity : BaseImportBookActivity<RemoteBookViewModel>(),
    RemoteBookAdapter.CallBack,
    SelectActionBar.CallBack,
    ServersDialog.Callback {

    override val viewModel by viewModels<RemoteBookViewModel>()
    private val adapter by lazy { RemoteBookAdapter(this, this) }
    private val browserItems = mutableStateOf<List<ImportBrowserItem>>(emptyList())
    private var currentItems: List<RemoteBook> = emptyList()
    private var groupMenu: SubMenu? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        searchView.queryHint = getString(R.string.screen) + " • " + getString(R.string.remote_book)
        onBackPressedDispatcher.addCallback(this) {
            if (!goBackDir()) {
                finish()
            }
        }
        lifecycleScope.launch {
            if (!setBookStorage()) {
                finish()
                return@launch
            }
            initView()
            initEvent()
            launch {
                viewModel.dataFlow.conflate().collect { sortedRemoteBooks ->
                    binding.refreshProgressBar.isAutoLoading = false
                    adapter.setItems(sortedRemoteBooks)
                    currentItems = sortedRemoteBooks
                    refreshBrowserItems()
                    delay(500)
                }
            }
            viewModel.initData {
                upPath()
            }
        }
    }

    override fun observeLiveBus() {
        viewModel.permissionDenialLiveData.observe(this) {
            localBookTreeSelect.launch {
                title = getString(R.string.select_book_folder)
            }
        }
    }

    private fun initView() {
        binding.importCompose.setContent {
            LegadoTheme {
                ImportBrowserContent(
                    items = browserItems.value,
                    emptyMessage = androidx.compose.ui.res.stringResource(R.string.empty),
                    onItemClick = ::onBrowserItemClick,
                    onItemLongClick = ::onBrowserItemLongClick
                )
            }
        }
        binding.layTop.setBackgroundColor(backgroundColor)
        binding.selectActionBar.setMainActionText(R.string.add_to_bookshelf)
        binding.selectActionBar.setCallBack(this)
        if (!LocalConfig.webDavBookHelpVersionIsLast) {
            showHelp("webDavBookHelp")
        }
    }

    private fun sortCheck(sortKey: RemoteBookSort) {
        if (viewModel.sortKey == sortKey) {
            viewModel.sortAscending = !viewModel.sortAscending
        } else {
            viewModel.sortAscending = true
            viewModel.sortKey = sortKey
        }
    }

    private fun initEvent() {
        binding.tvGoBack.setOnClickListener {
            goBackDir()
        }
    }


    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_remote, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_refresh -> upPath()
            R.id.menu_server_config -> showDialogFragment<ServersDialog>()
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_help -> showHelp("webDavBookHelp")
            R.id.menu_sort_name -> {
                item.isChecked = true
                sortCheck(RemoteBookSort.Name)
                upPath()
            }
            R.id.menu_sort_time -> {
                item.isChecked = true
                sortCheck(RemoteBookSort.Default)
                upPath()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_sort)?.subMenu
        groupMenu?.setGroupCheckable(R.id.menu_group_sort, true, true)
        groupMenu?.findItem(R.id.menu_sort_name)?.isChecked =
            viewModel.sortKey == RemoteBookSort.Name
        groupMenu?.findItem(R.id.menu_sort_time)?.isChecked =
            viewModel.sortKey == RemoteBookSort.Default
        return super.onPrepareOptionsMenu(menu)
    }

    override fun revertSelection() {
        adapter.revertSelection()
        refreshBrowserItems()
    }

    override fun selectAll(selectAll: Boolean) {
        adapter.selectAll(selectAll)
        refreshBrowserItems()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onClickSelectBarMainAction() {
        binding.refreshProgressBar.isAutoLoading = true
        viewModel.addToBookshelf(adapter.selected) {
            adapter.selected.clear()
            adapter.notifyDataSetChanged()
            upCountView()
            binding.refreshProgressBar.isAutoLoading = false
        }
    }

    private fun goBackDir(): Boolean {
        if (viewModel.dirList.isEmpty()) {
            return false
        }
        viewModel.dirList.removeLastOrNull()
        upPath()
        return true
    }

    private fun upPath() {
        binding.tvGoBack.isEnabled = viewModel.dirList.isNotEmpty()
        var path = if (viewModel.isDefaultWebdav) {
            "books" + File.separator
        } else {
            File.separator
        }
        viewModel.dirList.forEach {
            path = path + it.filename + File.separator
        }
        binding.tvPath.text = path
        viewModel.dataCallback?.clear()
        adapter.selected.clear()
        currentItems = emptyList()
        refreshBrowserItems()
        upCountView()
        viewModel.loadRemoteBookList(
            viewModel.dirList.lastOrNull()?.path
        ) {
            binding.refreshProgressBar.isAutoLoading = it
        }
    }

    override fun openDir(remoteBook: RemoteBook) {
        viewModel.dirList.add(remoteBook)
        upPath()
    }

    override fun upCountView() {
        refreshBrowserItems()
        binding.selectActionBar.upCountView(adapter.selected.size, adapter.checkableCount)
    }

    private fun onBrowserItemClick(index: Int) {
        currentItems.getOrNull(index)?.let { item ->
            when {
                item.isDir -> openDir(item)
                !item.isOnBookShelf -> {
                    if (!adapter.selected.add(item)) adapter.selected.remove(item)
                    upCountView()
                }
                else -> startRead(item)
            }
        }
    }

    private fun onBrowserItemLongClick(index: Int) {
        currentItems.getOrNull(index)?.takeIf { it.isOnBookShelf }?.let(::addToBookShelfAgain)
    }

    private fun refreshBrowserItems() {
        browserItems.value = currentItems.map { item ->
            ImportBrowserItem(
                key = item.path,
                title = item.filename,
                badge = item.contentType,
                summary = "${ConvertUtils.formatFileSize(item.size)} · ${AppConst.dateFormat.format(item.lastModify)}",
                isDirectory = item.isDir,
                inBookshelf = item.isOnBookShelf,
                selected = adapter.selected.contains(item)
            )
        }
    }

    override fun onDialogDismiss(tag: String) {
        viewModel.initData {
            upPath()
        }
    }

    override fun onSearchTextChange(newText: String?) {
        viewModel.updateCallBackFlow(newText)
    }

    private fun showRemoteBookDownloadAlert(
        remoteBook: RemoteBook,
        onDownloadFinish: (() -> Unit)? = null
    ) {
        alert(
            R.string.draw,
            R.string.archive_not_found
        ) {
            okButton {
                viewModel.addToBookshelf(hashSetOf(remoteBook)) {
                    onDownloadFinish?.invoke()
                }
            }
            noButton()
        }
    }

    override fun startRead(remoteBook: RemoteBook) {
        val downloadFileName = remoteBook.filename
        if (!ArchiveUtils.isArchive(downloadFileName)) {
            appDb.bookDao.getBookByFileName(downloadFileName)?.let {
                startReadBook(it)
            }
        } else {
            AppConfig.defaultBookTreeUri ?: return
            val downloadArchiveFileDoc = FileDoc.fromUri(Uri.parse(AppConfig.defaultBookTreeUri), true)
                .find(downloadFileName)
            if (downloadArchiveFileDoc == null) {
                showRemoteBookDownloadAlert(remoteBook) {
                    startRead(remoteBook)
                }
            } else {
                onArchiveFileClick(downloadArchiveFileDoc)
            }
        }
    }

    override fun addToBookShelfAgain(remoteBook: RemoteBook) {
        alert(getString(R.string.sure), "是否重新加入书架？") {
            yesButton {
                binding.refreshProgressBar.isAutoLoading = true
                viewModel.addToBookshelf(hashSetOf(remoteBook)) {
                    binding.refreshProgressBar.isAutoLoading = false
                }
            }
            noButton()
        }
    }

}
