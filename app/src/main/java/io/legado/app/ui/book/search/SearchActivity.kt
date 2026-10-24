package io.legado.app.ui.book.search

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.databinding.ActivityBookSearchBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.info.EXTRA_SHARED_COVER_TRANSITION_NAME
import io.legado.app.ui.book.info.isBookCoverTransitionEnabled
import io.legado.app.ui.book.info.newBookCoverTransition
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class SearchActivity : VMBaseActivity<ActivityBookSearchBinding, SearchViewModel>(),
    SearchScopeDialog.Callback {

    private var coverTransitionsEnabled = false

    override val binding by viewBinding(ActivityBookSearchBinding::inflate)
    override val viewModel by viewModels<SearchViewModel>()

    private var uiState by mutableStateOf(SearchUiState())
    private var searchBooks: List<SearchBook> = emptyList()
    private var historyItems: List<SearchKeyword> = emptyList()
    private var groups: List<String> = emptyList()
    private var historyFlowJob: Job? = null
    private var booksFlowJob: Job? = null
    private var menu: Menu? = null
    private var precisionSearchMenuItem: MenuItem? = null
    private var isManualStopSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        coverTransitionsEnabled = isBookCoverTransitionEnabled()
        if (coverTransitionsEnabled) {
            window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
            window.sharedElementExitTransition = newBookCoverTransition()
            window.sharedElementReenterTransition = newBookCoverTransition()
        }
        super.onCreate(savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        uiState = uiState.copy(hasMore = viewModel.hasMore)
        binding.titleBar.findViewById<ComposeView>(R.id.search_toolbar_compose).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    SearchToolbar(
                        state = uiState,
                        onQueryChange = ::onQueryChanged,
                        onQueryFocusChange = ::onQueryFocusChanged,
                        onSubmit = ::submitSearch
                    )
                }
            }
        }
        binding.composeContainer.addView(
            LegadoComposeView(this).apply {
                setContent {
                    LegadoTheme {
                        SearchScreen(
                            state = uiState,
                            onHistoryClick = ::searchHistory,
                            onHistoryDelete = ::deleteHistory,
                            onClearHistory = ::alertClearHistory,
                            onLocalBookClick = { showBookInfo(it.name, it.author, it.bookUrl) },
                            onBookClick = { book, cover ->
                                showBookInfo(book.name, book.author, book.bookUrl, cover)
                            },
                            onStopSearch = ::stopSearch,
                            onContinueSearch = ::continueSearch,
                            onListEndReached = ::scrollToBottom
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        initData()
        if (savedInstanceState == null) {
            receiptIntent(intent)
        } else {
            savedInstanceState.getString(KEY_SEARCH_SCOPE)?.let {
                viewModel.searchScope.update(it, postValue = false, save = false)
            }
            uiState = uiState.copy(
                query = savedInstanceState.getString(KEY_QUERY).orEmpty(),
                showInputHelp = savedInstanceState.getBoolean(KEY_SHOW_HELP, true),
                queryFocused = savedInstanceState.getBoolean(KEY_QUERY_FOCUSED),
                focusRequestId = if (savedInstanceState.getBoolean(KEY_QUERY_FOCUSED)) 1 else 0
            )
            refreshSuggestions(uiState.query.trim())
            val submittedQuery = savedInstanceState.getString(KEY_SUBMITTED_QUERY).orEmpty()
            if (viewModel.searchKey.isEmpty() && submittedQuery.isNotBlank()) {
                viewModel.search(submittedQuery)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_QUERY, uiState.query)
        outState.putBoolean(KEY_SHOW_HELP, uiState.showInputHelp)
        outState.putBoolean(KEY_QUERY_FOCUSED, uiState.queryFocused)
        outState.putString(KEY_SUBMITTED_QUERY, viewModel.searchKey)
        outState.putString(KEY_SEARCH_SCOPE, viewModel.searchScope.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiptIntent(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_search, menu)
        this.menu = menu
        precisionSearchMenuItem = menu.findItem(R.id.menu_precision_search)
        precisionSearchMenuItem?.isChecked = getPrefBoolean(PreferKey.precisionSearch)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.transaction {
            menu.removeGroup(R.id.menu_group_1)
            menu.removeGroup(R.id.menu_group_2)
            var hasChecked = false
            val searchScopeNames = viewModel.searchScope.displayNames
            if (viewModel.searchScope.isSource()) {
                menu.add(R.id.menu_group_1, Menu.NONE, Menu.NONE, searchScopeNames.first()).apply {
                    isChecked = true
                    hasChecked = true
                }
            }
            val allSourceMenu =
                menu.add(R.id.menu_group_2, R.id.menu_1, Menu.NONE, getString(R.string.all_source))
                    .apply {
                        if (searchScopeNames.isEmpty()) {
                            isChecked = true
                            hasChecked = true
                        }
                    }
            groups.forEach { group ->
                if (searchScopeNames.contains(group)) {
                    menu.add(R.id.menu_group_1, Menu.NONE, Menu.NONE, group).apply {
                        isChecked = true
                        hasChecked = true
                    }
                } else {
                    menu.add(R.id.menu_group_2, Menu.NONE, Menu.NONE, group)
                }
            }
            if (!hasChecked) {
                viewModel.searchScope.update("")
                allSourceMenu.isChecked = true
            }
            menu.setGroupCheckable(R.id.menu_group_1, true, false)
            menu.setGroupCheckable(R.id.menu_group_2, true, true)
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_precision_search -> {
                val enabled = !getPrefBoolean(PreferKey.precisionSearch)
                putPrefBoolean(PreferKey.precisionSearch, enabled)
                precisionSearchMenuItem?.isChecked = enabled
                uiState.query.trim().let { query ->
                    uiState = uiState.copy(query = query)
                    submitSearch()
                }
            }

            R.id.menu_search_scope -> alertSearchScope()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            R.id.menu_log -> showDialogFragment(AppLogDialog())
            R.id.menu_1 -> viewModel.searchScope.update("")
            else -> {
                if (item.groupId == R.id.menu_group_1) {
                    viewModel.searchScope.remove(item.title.toString())
                } else if (item.groupId == R.id.menu_group_2) {
                    viewModel.searchScope.update(item.title.toString())
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initData() {
        var initialScope = true
        viewModel.searchScope.stateLiveData.observe(this) {
            if (initialScope) initialScope = false
            else if (!uiState.showInputHelp) submitSearch()
        }
        viewModel.isSearchLiveData.observe(this) { searching ->
            uiState = uiState.copy(
                isSearching = searching,
                hasMore = viewModel.hasMore,
                manuallyStopped = isManualStopSearch
            )
        }
        viewModel.searchBookLiveData.observe(this) { books ->
            searchBooks = books
            publishSearchBooks()
        }
        viewModel.upAdapterLiveData.observe(this) {
            publishSearchBooks()
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().collect { groups = it }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.resume()
                try {
                    awaitCancellation()
                } finally {
                    viewModel.pause()
                }
            }
        }
    }

    private fun receiptIntent(intent: Intent? = null) {
        intent?.getStringExtra("searchScope")?.let { scope ->
            viewModel.searchScope.update(scope, postValue = false, save = false)
        }
        val key = intent?.getStringExtra("key")
        if (key.isNullOrBlank()) {
            uiState = uiState.copy(
                query = "",
                showInputHelp = true,
                queryFocused = true,
                focusRequestId = uiState.focusRequestId + 1
            )
            refreshSuggestions("")
        } else {
            uiState = uiState.copy(query = key, queryFocused = false)
            submitSearch()
        }
    }

    private fun onQueryChanged(query: String) {
        uiState = uiState.copy(query = query)
        viewModel.stop()
        refreshSuggestions(query.trim())
    }

    private fun onQueryFocusChanged(focused: Boolean) {
        if (uiState.queryFocused == focused) return
        val showHelp = !uiState.isSearching &&
            (focused || uiState.results.isEmpty() || uiState.query.isBlank())
        uiState = uiState.copy(queryFocused = focused, showInputHelp = showHelp)
        if (showHelp) refreshSuggestions(uiState.query.trim())
    }

    private fun submitSearch() {
        val query = uiState.query.trim()
        isManualStopSearch = false
        uiState = uiState.copy(
            query = query,
            showInputHelp = false,
            queryFocused = false,
            manuallyStopped = false
        )
        viewModel.saveSearchKey(query)
        viewModel.searchKey = ""
        viewModel.search(query)
    }

    private fun stopSearch() {
        isManualStopSearch = true
        uiState = uiState.copy(manuallyStopped = true)
        viewModel.stop()
    }

    private fun continueSearch() {
        isManualStopSearch = false
        uiState = uiState.copy(manuallyStopped = false)
        viewModel.search("")
    }

    private fun scrollToBottom() {
        if (!isManualStopSearch &&
            !uiState.isSearching &&
            viewModel.searchKey.isNotEmpty() &&
            viewModel.hasMore
        ) {
            viewModel.search("")
        }
    }

    private fun refreshSuggestions(key: String?) {
        booksFlowJob?.cancel()
        if (key.isNullOrBlank()) {
            uiState = uiState.copy(localBooks = emptyList())
        } else {
            booksFlowJob = lifecycleScope.launch {
                appDb.bookDao.flowSearch(key).conflate().collect { books ->
                    uiState = uiState.copy(
                        localBooks = books.map { SearchLocalBookUi(it.name, it.author, it.bookUrl) }
                    )
                }
            }
        }

        historyFlowJob?.cancel()
        historyFlowJob = lifecycleScope.launch {
            when {
                key.isNullOrBlank() -> appDb.searchKeywordDao.flowByTime()
                else -> appDb.searchKeywordDao.flowSearch(key)
            }.catch {
                AppLog.put("搜索界面获取搜索历史数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { items ->
                historyItems = items
                uiState = uiState.copy(history = items.map(SearchKeyword::word))
            }
        }
    }

    private fun publishSearchBooks() {
        uiState = uiState.copy(
            results = searchBooks.map { book ->
                SearchBookUi(
                    bookUrl = book.bookUrl,
                    name = book.name,
                    author = book.author,
                    coverUrl = book.coverUrl,
                    origin = book.origin,
                    originCount = book.origins.size,
                    latestChapter = book.latestChapterTitle,
                    intro = book.trimIntro(this),
                    kinds = book.getKindList(),
                    inBookshelf = viewModel.isInBookShelf(book)
                )
            }
        )
    }

    private fun showBookInfo(name: String, author: String, bookUrl: String, coverView: View? = null) {
        val intent = Intent(this, BookInfoActivity::class.java).apply {
            putExtra("name", name)
            putExtra("author", author)
            putExtra("bookUrl", bookUrl)
        }
        val sourceCover = coverView?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
        val transitionName = sourceCover?.let { ViewCompat.getTransitionName(it) }
        if (coverTransitionsEnabled && isBookCoverTransitionEnabled() && sourceCover != null &&
            transitionName != null && transitionName.isNotBlank()
        ) {
            intent.putExtra(EXTRA_SHARED_COVER_TRANSITION_NAME, transitionName)
            startActivity(
                intent,
                ActivityOptions.makeSceneTransitionAnimation(
                    this,
                    sourceCover,
                    transitionName
                ).toBundle()
            )
        } else {
            startActivity(intent)
        }
    }

    private fun searchHistory(key: String) {
        lifecycleScope.launch {
            val shouldSubmit = uiState.query == key || withContext(IO) {
                appDb.bookDao.findByName(key).isEmpty()
            }
            if (shouldSubmit) {
                uiState = uiState.copy(query = key)
                submitSearch()
            } else {
                onQueryChanged(key)
                uiState = uiState.copy(showInputHelp = true)
            }
        }
    }

    private fun deleteHistory(word: String) {
        historyItems.firstOrNull { it.word == word }?.let(viewModel::deleteHistory)
    }

    override fun onSearchScopeOk(searchScope: SearchScope) {
        viewModel.searchScope.update(searchScope.toString())
    }

    override fun observeLiveBus() {
        viewModel.searchFinishLiveData.observe(this) { isEmpty ->
            if (!isEmpty || viewModel.searchScope.isAll()) return@observe
            alert("搜索结果为空") {
                val precisionSearch = appCtx.getPrefBoolean(PreferKey.precisionSearch)
                val displayScope = viewModel.searchScope.display
                if (precisionSearch) {
                    setMessage("${displayScope}分组搜索结果为空，是否关闭精准搜索？")
                    yesButton {
                        appCtx.putPrefBoolean(PreferKey.precisionSearch, false)
                        precisionSearchMenuItem?.isChecked = false
                        viewModel.searchKey = ""
                        viewModel.search(uiState.query)
                    }
                } else {
                    setMessage("${displayScope}分组搜索结果为空，是否切换到全部分组？")
                    yesButton { viewModel.searchScope.update("") }
                }
                noButton()
            }
        }
    }

    private fun alertSearchScope() {
        showDialogFragment<SearchScopeDialog>()
    }

    private fun alertClearHistory() {
        alert(R.string.draw) {
            setMessage(R.string.sure_clear_search_history)
            yesButton { viewModel.clearHistory() }
            noButton()
        }
    }

    companion object {
        private const val KEY_QUERY = "searchQuery"
        private const val KEY_SHOW_HELP = "searchShowHelp"
        private const val KEY_QUERY_FOCUSED = "searchQueryFocused"
        private const val KEY_SUBMITTED_QUERY = "searchSubmittedQuery"
        private const val KEY_SEARCH_SCOPE = "searchScope"

        fun start(context: Context, key: String?, searchScope: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", searchScope)
            }
        }

        fun start(context: Context, source: BookSource, key: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", SearchScope(source).toString())
            }
        }

        fun start(context: Context, source: BookSourcePart, key: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", SearchScope(source).toString())
            }
        }
    }
}
