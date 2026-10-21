package io.legado.app.ui.main.bookshelf.style1.books

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.BookshelfContent
import io.legado.app.ui.main.bookshelf.BookshelfAppearance
import io.legado.app.ui.main.bookshelf.sortShelfBooks
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BooksFragment() : BaseFragment(0) {
    constructor(position: Int, group: BookGroup) : this() {
        arguments = Bundle().apply {
            putInt("position", position)
            putLong("groupId", group.groupId)
            putInt("bookSort", group.getRealBookSort())
            putBoolean("enableRefresh", group.enableRefresh)
            putBoolean("onlyUpdateRead", group.onlyUpdateRead)
        }
    }
    private val activityViewModel by activityViewModels<MainViewModel>()
    private var shelfBooks by mutableStateOf<List<Book>>(emptyList())
    private var loading by mutableStateOf(true)
    private var loadError by mutableStateOf<String?>(null)
    private var refreshEnabled by mutableStateOf(true)
    private var revision by mutableIntStateOf(0)
    private var scrollRequest by mutableIntStateOf(0)
    private var booksFlowJob: Job? = null
    private var onlyUpdateRead = false
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    @Suppress("UNUSED_VARIABLE") val update = revision
                    BookshelfContent(shelfBooks, groupId, AppConfig.bookshelfLayout, scrollRequest,
                        refreshEnabled, { activityViewModel.isUpdate(it.bookUrl) },
                        { activityViewModel.upToc(shelfBooks, onlyUpdateRead) },
                        { startActivityForBook(it as Book) }, { openBookInfo(it as Book) }, revision = revision,
                        isLoading = loading, error = loadError, onRetry = ::upRecyclerData,
                        appearance = BookshelfAppearance.fromPreferences())
                }
            }
        }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position")
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort")
            refreshEnabled = it.getBoolean("enableRefresh", true)
            onlyUpdateRead = it.getBoolean("onlyUpdateRead")
        }
        upRecyclerData()
    }

    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        loadError = null
        loading = shelfBooks.isEmpty()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { sortShelfBooks(it, bookSort) }
                .flowWithLifecycleAndDatabaseChangeFirst(viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED, AppDatabase.BOOK_TABLE_NAME)
                .catch { AppLog.put("书架更新出错", it); loadError = it.localizedMessage ?: "无法读取本地书架"; loading = false }
                .conflate().flowOn(Dispatchers.Default)
                .collect { shelfBooks = it; loading = false }
        }
    }
    fun upBookSort(sort: Int) {
        arguments?.putInt("bookSort", sort)
        bookSort = sort
        upRecyclerData()
    }
    fun updateGroup(group: BookGroup) {
        refreshEnabled = group.enableRefresh
        onlyUpdateRead = group.onlyUpdateRead
        arguments?.putBoolean("enableRefresh", group.enableRefresh)
        arguments?.putBoolean("onlyUpdateRead", group.onlyUpdateRead)
    }
    fun getBooks(): List<Book> = shelfBooks
    fun getBooksCount(): Int = shelfBooks.size
    fun gotoTop() { scrollRequest++ }
    private fun openBookInfo(book: Book) {
        startActivity<BookInfoActivity> { putExtra("name", book.name); putExtra("author", book.author) }
    }
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) { revision++ }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) { revision++ }
    }
}
