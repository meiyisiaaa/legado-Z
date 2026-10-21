package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.view.indices
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.import.local.ImportBookActivity
import io.legado.app.ui.book.import.remote.RemoteBookActivity
import io.legado.app.ui.book.manage.BookshelfManageActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.readText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi

abstract class BaseBookshelfFragment(layoutId: Int) : VMBaseFragment<BookshelfViewModel>(layoutId),
    MainFragmentInterface {

    override val position: Int? get() = arguments?.getInt("position")

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    private val importBookshelf = registerForActivityResult(HandleFileContract()) {
        kotlin.runCatching {
            it.uri?.readText(requireContext())?.let { text ->
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure {
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                }
                customView { LegadoComposeView(requireContext()).apply {
                    setContent { LegadoTheme { BookshelfInput(getString(R.string.path), uri.toString(), readOnly = true) } }
                } }
                okButton {
                    requireContext().sendToClip(uri.toString())
                }
            }
        }
    }
    abstract val groupId: Long
    abstract val books: List<Book>
    abstract var onlyUpdateRead: Boolean
    private var groupsLiveData: LiveData<List<BookGroup>>? = null
    private val waitDialog by lazy {
        WaitDialog(requireContext()).apply {
            setOnCancelListener {
                viewModel.addBookJob?.cancel()
            }
        }
    }

    abstract fun gotoTop()

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_remote -> startActivity<RemoteBookActivity>()
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> activityViewModel.upToc(books, onlyUpdateRead)
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_add_url -> showAddBookByUrlAlert()
            R.id.menu_bookshelf_manage -> startActivity<BookshelfManageActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", groupId)
            }

            R.id.menu_export_bookshelf -> viewModel.exportBookshelf(books) { file ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData =
                        HandleFileContract.FileData("bookshelf.json", file, "application/json")
                }
            }

            R.id.menu_import_bookshelf -> importBookshelfAlert(groupId)
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
    }

    protected fun initBookGroupData() {
        groupsLiveData?.removeObservers(viewLifecycleOwner)
        groupsLiveData = appDb.bookGroupDao.show.apply {
            observe(viewLifecycleOwner) {
                upGroup(it)
            }
        }
    }

    abstract fun upGroup(data: List<BookGroup>)

    abstract fun upSort()

    override fun observeLiveBus() {
        viewModel.addBookProgressLiveData.observe(this) { count ->
            if (count < 0) {
                waitDialog.dismiss()
            } else {
                waitDialog.setText("添加中... ($count)")
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showAddBookByUrlAlert() {
        var input = ""
        alert(titleResource = R.string.add_book_url) {
            customView { LegadoComposeView(requireContext()).apply {
                setContent { LegadoTheme { BookshelfInput("URL") { input = it } } }
            } }
            okButton {
                waitDialog.setText("添加中...")
                waitDialog.show()
                viewModel.addBookByUrl(input)
            }
            cancelButton()
        }
    }

    fun configBookshelf() {
        val original = BookshelfOptions(AppConfig.bookGroupStyle.coerceIn(0, 1),
            AppConfig.bookshelfLayout.coerceIn(0, 6), AppConfig.bookshelfSort.coerceIn(0, 5),
            AppConfig.showBookname.coerceIn(0, 2), AppConfig.bookshelfMargin.coerceIn(0, 60),
            AppConfig.showUnread, AppConfig.showLastUpdateTime, AppConfig.showWaitUpCount,
            AppConfig.showBookshelfFastScroller)
        var draft = original
        alert(titleResource = R.string.bookshelf_layout) {
            customView {
                io.legado.app.ui.widget.compose.LegadoComposeView(requireContext()).apply {
                    setContent {
                        io.legado.app.ui.widget.compose.LegadoTheme {
                            BookshelfOptionsContent(original) { draft = it }
                        }
                    }
                }
            }
            okButton {
                AppConfig.bookGroupStyle = draft.groupStyle
                AppConfig.bookshelfLayout = draft.layout
                AppConfig.bookshelfSort = draft.sort
                AppConfig.showBookname = draft.bookName
                AppConfig.bookshelfMargin = draft.margin
                AppConfig.showUnread = draft.unread
                AppConfig.showLastUpdateTime = draft.lastUpdate
                AppConfig.showWaitUpCount = draft.updateCount
                AppConfig.showBookshelfFastScroller = draft.fastScroll
                if (draft.updateCount != original.updateCount) activityViewModel.postUpBooksLiveData(true)
                if (draft.sort != original.sort) upSort()
                when {
                    draft.layout != original.layout || draft.bookName != original.bookName ||
                        draft.margin != original.margin -> postEvent(EventBus.RECREATE, "")
                    draft.groupStyle != original.groupStyle -> postEvent(EventBus.NOTIFY_MAIN, false)
                    else -> {
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                }
            }
            cancelButton()
        }
    }

    private fun importBookshelfAlert(groupId: Long) {
        var input = ""
        alert(titleResource = R.string.import_bookshelf) {
            customView { LegadoComposeView(requireContext()).apply {
                setContent { LegadoTheme { BookshelfInput("URL / JSON") { input = it } } }
            } }
            okButton {
                viewModel.importBookshelf(input, groupId)
            }
            cancelButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = arrayOf("txt", "json")
                }
            }
        }
    }

}
