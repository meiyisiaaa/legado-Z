package io.legado.app.ui.book.explore

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ActivityExploreShowBinding
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * 发现列表
 */
class ExploreShowActivity : VMBaseActivity<ActivityExploreShowBinding, ExploreShowViewModel>() {
    override val binding by viewBinding(ActivityExploreShowBinding::inflate)
    override val viewModel by viewModels<ExploreShowViewModel>()

    private val listState = LazyListState()
    private var books by mutableStateOf<List<SearchBook>>(emptyList())
    private var listRevision by mutableIntStateOf(0)
    private var currentPage by mutableIntStateOf(1)
    private var bottomLoading by mutableStateOf(true)
    private var topLoading by mutableStateOf(false)
    private var bottomHasMore by mutableStateOf(true)
    private var topHasMore by mutableStateOf(false)
    private var bottomError by mutableStateOf<String?>(null)
    private var topError by mutableStateOf<String?>(null)
    private var failedTopPage: Int? = null
    private var oldPage = -1
    private val menuPage by lazy {
        binding.titleBar.menu.add(getString(R.string.menu_page, currentPage)).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                val page = viewModel.pageLiveData.value ?: currentPage
                NumberPickerDialog(this@ExploreShowActivity)
                    .setTitle(getString(R.string.change_page))
                    .setMaxValue(999)
                    .setMinValue(1)
                    .setValue(page)
                    .show {
                        if (page != it) {
                            oldPage = it
                            topHasMore = it > 1
                            topLoading = false
                            topError = null
                            failedTopPage = null
                            bottomHasMore = true
                            bottomLoading = false
                            bottomError = null
                            books = emptyList()
                            listRevision++
                            viewModel.skipPage(it)
                            lifecycleScope.launch { listState.scrollToItem(0) }
                            scrollToBottom(true)
                        }
                    }
                true
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = intent.getStringExtra("exploreName")
        initComposeContent()
        viewModel.booksData.observe(this) { upData(it) }
        viewModel.addBooksData.observe(this) { upDataTop(it) }
        viewModel.errorLiveData.observe(this) {
            bottomLoading = false
            bottomHasMore = false
            bottomError = it
        }
        viewModel.errorTopLiveData.observe(this) {
            topLoading = false
            topHasMore = false
            topError = it
            failedTopPage = oldPage
        }
        viewModel.upAdapterLiveData.observe(this) {
            listRevision++
        }
        viewModel.pageLiveData.observe(this) {
            currentPage = it
            menuPage.title = getString(R.string.menu_page, it)
        }
        viewModel.initData(intent)
    }

    private fun initComposeContent() {
        val composeView = LegadoComposeView(this).apply { id = View.generateViewId() }
        binding.composeContainer.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        composeView.setContent {
            LegadoTheme {
                ExploreShowContent(
                    books = books,
                    listState = listState,
                    revision = listRevision,
                    page = currentPage,
                    previousPage = oldPage.coerceAtLeast(1),
                    bottomLoading = bottomLoading,
                    topLoading = topLoading,
                    bottomHasMore = bottomHasMore,
                    topHasMore = topHasMore,
                    bottomError = bottomError,
                    topError = topError,
                    isBookInShelf = viewModel::isInBookShelf,
                    onBookClick = ::showBookInfo,
                    onLoadBottom = { scrollToBottom() },
                    onLoadTop = { scrollToTop() },
                    onRetryBottom = { scrollToBottom(true) },
                    onRetryTop = { scrollToTop(true) }
                )
            }
        }
    }

    private fun scrollToBottom(forceLoad: Boolean = false) {
        if (!bottomLoading && (bottomHasMore || forceLoad) && !topLoading) {
            bottomLoading = true
            bottomHasMore = true
            bottomError = null
            viewModel.explore()
        }
    }

    private fun scrollToTop(forceLoad: Boolean = false) {
        if (!topLoading && ((oldPage > 1 && topHasMore) || forceLoad) && !bottomLoading) {
            topLoading = true
            topHasMore = true
            topError = null
            val retryPage = if (forceLoad) failedTopPage else null
            if (retryPage == null) oldPage--
            else failedTopPage = null
            viewModel.explore(retryPage ?: oldPage)
        }
    }

    private fun upData(books: List<SearchBook>) {
        bottomLoading = false
        bottomError = null
        if (books.isEmpty() && this.books.isEmpty()) {
            bottomHasMore = false
            this.books = emptyList()
            listRevision++
        } else if (this.books.size == books.size) {
            bottomHasMore = false
        } else {
            this.books = books.toList()
            listRevision++
        }
    }

    private fun upDataTop(books: List<SearchBook>) {
        topLoading = false
        topError = null
        failedTopPage = null
        if (books.isEmpty()) {
            topHasMore = false
            return
        }
        val firstVisible = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        this.books = books + this.books
        listRevision++
        topHasMore = oldPage > 1
        lifecycleScope.launch { listState.scrollToItem(firstVisible + books.size, offset) }
    }

    private fun showBookInfo(book: SearchBook) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }
}
