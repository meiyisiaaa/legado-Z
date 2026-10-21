package io.legado.app.ui.book.searchContent

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.ActivitySearchContentBinding
import io.legado.app.help.IntentData
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.invisible
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchContentActivity : VMBaseActivity<ActivitySearchContentBinding, SearchContentViewModel>() {

    override val binding by viewBinding(ActivitySearchContentBinding::inflate)
    override val viewModel by viewModels<SearchContentViewModel>()

    private val resultItems = mutableStateListOf<SearchResult>()
    private var query by mutableStateOf("")
    private var resultCount by mutableIntStateOf(0)
    private var searching by mutableStateOf(false)
    private var replaceEnabled by mutableStateOf(SearchContentViewModel.replaceEnabled)
    private var regexEnabled by mutableStateOf(SearchContentViewModel.regexReplace)
    private var searchSubmitted by mutableStateOf(false)
    private var requestFocus by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var initialResultIndex by mutableStateOf<Int?>(null)
    private var durChapterIndex = 0
    private var searchJob: Job? = null
    private var initJob: Job? = null
    private var searchGeneration = 0

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val searchResultList = IntentData.get<List<SearchResult>>("searchResultList")
        val position = intent.getIntExtra("searchResultIndex", 0)
        requestFocus = searchResultList == null
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                SearchContentScreen(
                    state = SearchContentUiState(
                        query = query,
                        results = resultItems.toList(),
                        resultCount = resultCount,
                        searching = searching,
                        replaceEnabled = replaceEnabled,
                        regexEnabled = regexEnabled,
                        searchRequested = searchSubmitted,
                        requestFocus = requestFocus,
                        error = errorMessage,
                        initialResultIndex = initialResultIndex,
                        currentChapterIndex = durChapterIndex
                    ),
                    onQueryChange = { query = it },
                    onSearch = ::startContentSearch,
                    onToggleReplace = {
                        SearchContentViewModel.replaceEnabled = !SearchContentViewModel.replaceEnabled
                        replaceEnabled = SearchContentViewModel.replaceEnabled
                    },
                    onToggleRegex = {
                        SearchContentViewModel.regexReplace = !SearchContentViewModel.regexReplace
                        regexEnabled = SearchContentViewModel.regexReplace
                    },
                    onStop = { searchJob?.cancel() },
                    onRetry = { startContentSearch(query) },
                    onOpenResult = ::openSearchResult,
                    onBack = ::finish,
                    modifier = Modifier
                )
            }
        }
        initSearchResultList(searchResultList, position)
        val bookUrl = intent.getStringExtra("bookUrl")
        if (bookUrl.isNullOrBlank()) {
            errorMessage = "缺少书籍信息"
            return
        }
        viewModel.initBook(bookUrl) { initBook(searchResultList == null) }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.book?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) viewModel.cacheChapterNames.add(chapter.getFileName())
            }
        }
    }

    private fun initSearchResultList(list: List<SearchResult>?, position: Int) {
        list ?: return
        viewModel.searchResultList.addAll(list)
        viewModel.searchResultCounts = list.size
        resultItems.clear()
        resultItems.addAll(list)
        resultCount = list.size
        initialResultIndex = position.coerceIn(0, (list.size - 1).coerceAtLeast(0))
    }

    private fun initBook(submit: Boolean) {
        viewModel.book?.let { book ->
            initCacheFileNames(book)
            durChapterIndex = book.durChapterIndex
            intent.getStringExtra("searchWord")?.let { searchWord ->
                query = searchWord
                if (submit && searchWord.isNotBlank()) startContentSearch(searchWord)
            }
        }
    }

    private fun initCacheFileNames(book: Book) {
        initJob = lifecycleScope.launch {
            val names = withContext(IO) { BookHelp.getChapterFiles(book) }
            viewModel.cacheChapterNames.addAll(names)
        }
    }

    fun startContentSearch(query: String) {
        val searchQuery = query.trim()
        if (searchQuery.isBlank()) return
        searchJob?.cancel()
        val generation = ++searchGeneration
        resultItems.clear()
        viewModel.searchResultList.clear()
        viewModel.searchResultCounts = 0
        resultCount = 0
        this.query = searchQuery
        viewModel.lastQuery = searchQuery
        initialResultIndex = null
        searchSubmitted = true
        errorMessage = null
        searching = true
        searchJob = lifecycleScope.launch(IO) {
            try {
                initJob?.join()
                val chapters = appDb.bookChapterDao.getChapterList(viewModel.bookUrl)
                for (chapter in chapters) {
                    currentCoroutineContext().ensureActive()
                    val matches = if (isLocalBook || chapter.getFileName() in viewModel.cacheChapterNames) {
                        viewModel.searchChapter(searchQuery, chapter)
                    } else {
                        emptyList()
                    }
                    currentCoroutineContext().ensureActive()
                    if (matches.isNotEmpty()) {
                        viewModel.searchResultList.addAll(matches)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            resultItems.addAll(matches)
                            resultCount = viewModel.searchResultCounts
                        }
                    }
                }
                if (viewModel.searchResultCounts == 0) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        resultItems.add(SearchResult(resultText = getString(R.string.search_content_empty)))
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.put("全文搜索出错\n${error.localizedMessage}", error)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    errorMessage = error.localizedMessage ?: getString(R.string.search_content_empty)
                }
            } finally {
                withContext(NonCancellable + kotlinx.coroutines.Dispatchers.Main) {
                    if (generation == searchGeneration) searching = false
                }
            }
        }
    }

    private val isLocalBook: Boolean
        get() = viewModel.book?.isLocal == true

    private fun openSearchResult(searchResult: SearchResult, index: Int) {
        searchJob?.cancel()
        postEvent(EventBus.SEARCH_RESULT, viewModel.searchResultList as List<SearchResult>)
        val searchData = Intent()
        val key = System.currentTimeMillis()
        IntentData.put("searchResult$key", searchResult)
        IntentData.put("searchResultList$key", viewModel.searchResultList)
        searchData.putExtra("key", key)
        searchData.putExtra("index", index)
        setResult(RESULT_OK, searchData)
        finish()
    }
}
