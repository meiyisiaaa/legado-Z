package io.legado.app.ui.main.bookshelf.style2

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf2Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.BookshelfContent
import io.legado.app.ui.main.bookshelf.BookshelfAppearance
import io.legado.app.ui.main.bookshelf.sortShelfBooks
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BookshelfFragment2() : BaseBookshelfFragment(R.layout.fragment_bookshelf2),
    SearchView.OnQueryTextListener {
    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }
    private val binding by viewBinding(FragmentBookshelf2Binding::bind)
    private var bookGroups by mutableStateOf<List<BookGroup>>(emptyList())
    override var books by mutableStateOf<List<Book>>(emptyList())
    override var groupId by mutableLongStateOf(BookGroup.IdRoot)
    override var onlyUpdateRead = false
    private var refreshEnabled by mutableStateOf(true)
    private var loading by mutableStateOf(true)
    private var loadError by mutableStateOf<String?>(null)
    private var scrollRequest by mutableIntStateOf(0)
    private var revision by mutableIntStateOf(0)
    private var booksFlowJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        groupId = savedInstanceState?.getLong("groupId", BookGroup.IdRoot) ?: groupId
        setSupportToolbar(binding.titleBar.toolbar)
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                @Suppress("UNUSED_VARIABLE") val update = revision
                BookshelfContent(getItems(), groupId, AppConfig.bookshelfLayout, scrollRequest,
                    refreshEnabled, { activityViewModel.isUpdate(it.bookUrl) },
                    { activityViewModel.upToc(books, onlyUpdateRead) }, ::onItemClick, ::onItemLongClick,
                    revision = revision, isLoading = loading, error = loadError, onRetry = ::initBooksData,
                    appearance = BookshelfAppearance.fromPreferences())
            }
        }
        initBookGroupData()
        initBooksData()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("groupId", groupId)
        super.onSaveInstanceState(outState)
    }
    override fun upGroup(data: List<BookGroup>) {
        val before = bookGroups.firstOrNull { it.groupId == groupId }
        bookGroups = data
        val group = data.firstOrNull { it.groupId == groupId }
        if (group != null) {
            refreshEnabled = group.enableRefresh
            onlyUpdateRead = group.onlyUpdateRead
            binding.titleBar.title = "${getString(R.string.bookshelf)} (${group.groupName})"
            if (before?.getRealBookSort() != group.getRealBookSort()) initBooksData()
        }
    }
    override fun upSort() { initBooksData() }
    private fun initBooksData() {
        val group = bookGroups.firstOrNull { it.groupId == groupId }
        binding.titleBar.title = group?.let { "${getString(R.string.bookshelf)} (${it.groupName})" }
            ?: getString(R.string.bookshelf)
        refreshEnabled = group?.enableRefresh ?: true
        onlyUpdateRead = group?.onlyUpdateRead ?: false
        booksFlowJob?.cancel()
        loadError = null
        loading = books.isEmpty()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId)
                .map { sortShelfBooks(it, AppConfig.getBookSortByGroupId(groupId)) }
                .flowWithLifecycleAndDatabaseChangeFirst(viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED, AppDatabase.BOOK_TABLE_NAME)
                .catch { AppLog.put("书架更新出错", it); loadError = it.localizedMessage ?: "无法读取本地书架"; loading = false }
                .conflate().flowOn(Dispatchers.Default)
                .collect { books = it; loading = false }
        }
    }
    fun back(): Boolean {
        if (groupId == BookGroup.IdRoot) return false
        groupId = BookGroup.IdRoot
        books = emptyList()
        initBooksData()
        return true
    }
    override fun gotoTop() { scrollRequest++ }
    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }
    override fun onQueryTextChange(newText: String?) = false
    private fun onItemClick(item: Any) {
        when (item) {
            is Book -> startActivityForBook(item)
            is BookGroup -> { groupId = item.groupId; books = emptyList(); initBooksData() }
        }
    }
    private fun onItemLongClick(item: Any) {
        when (item) {
            is Book -> startActivity<BookInfoActivity> {
                putExtra("name", item.name)
                putExtra("author", item.author)
            }
            is BookGroup -> showDialogFragment(GroupEditDialog(item))
        }
    }
    private fun getItems(): List<Any> = if (groupId == BookGroup.IdRoot) bookGroups + books else books
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) { revision++ }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) { revision++ }
    }
}
