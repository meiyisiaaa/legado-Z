package io.legado.app.ui.book.changesource

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.DialogChapterChangeSourceBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class ChangeChapterSourceDialog() : BaseDialogFragment(R.layout.dialog_chapter_change_source),
    Toolbar.OnMenuItemClickListener {

    constructor(name: String, author: String, chapterIndex: Int, chapterTitle: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
            putInt("chapterIndex", chapterIndex)
            putString("chapterTitle", chapterTitle)
        }
    }

    private val binding by viewBinding(DialogChapterChangeSourceBinding::bind)
    private val groups = linkedSetOf<String>()
    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeChapterSourceViewModel by viewModels()
    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            viewModel.startSearch()
        }
    private var searchBooks by mutableStateOf<List<SearchBook>>(emptyList())
    private var tocChapters by mutableStateOf<List<BookChapter>>(emptyList())
    private var listRevision by mutableIntStateOf(0)
    private var tocCurrentIndex by mutableIntStateOf(0)
    private val listState = LazyListState()
    private val tocListState = LazyListState()
    private val contentSuccess: (content: String) -> Unit = {
        binding.loadingToc.gone()
        callBack?.replaceContent(it)
        dismissAllowingStateLoss()
    }
    private var searchBook: SearchBook? = null
    private val searchFinishCallback: (isEmpty: Boolean) -> Unit = {
        if (it) {
            val searchGroup = AppConfig.searchGroup
            if (searchGroup.isNotEmpty()) {
                lifecycleScope.launch {
                    context?.alert("搜索结果为空") {
                        setMessage("${searchGroup}分组搜索结果为空,是否切换到全部分组")
                        noButton()
                        yesButton {
                            AppConfig.searchGroup = ""
                            upGroupMenu()
                            viewModel.startSearch()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        viewModel.initData(arguments, callBack?.oldBook, activity is ReadBookActivity)
        showTitle()
        initMenu()
        initView()
        initComposeContent()
        initSearchView()
        initBottomBar()
        initLiveData()
        viewModel.searchFinishCallback = searchFinishCallback
        activity?.onBackPressedDispatcher?.addCallback(this) {
            if (binding.clToc.isVisible) {
                binding.clToc.gone()
                return@addCallback
            }
            dismissAllowingStateLoss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.searchFinishCallback = null
    }

    private fun showTitle() {
        binding.toolBar.title = viewModel.chapterTitle
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.change_source)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.menu.findItem(R.id.menu_check_author)
            ?.isChecked = AppConfig.changeSourceCheckAuthor
        binding.toolBar.menu.findItem(R.id.menu_load_info)
            ?.isChecked = AppConfig.changeSourceLoadInfo
        binding.toolBar.menu.findItem(R.id.menu_load_toc)
            ?.isChecked = AppConfig.changeSourceLoadToc
        binding.toolBar.menu.findItem(R.id.menu_load_word_count)
            ?.isChecked = AppConfig.changeSourceLoadWordCount
    }

    private fun initView() {
        binding.ivHideToc.setOnClickListener {
            binding.clToc.gone()
        }
        binding.flHideToc.elevation = requireContext().elevation
    }

    private fun initComposeContent() {
        val sourceComposeView = LegadoComposeView(requireContext()).apply { id = View.generateViewId() }
        binding.composeContainer.addView(
            sourceComposeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        sourceComposeView.setContent {
            LegadoTheme {
                ChangeChapterSourceContent(
                    books = searchBooks,
                    listState = listState,
                    oldBookUrl = callBack?.oldBook?.bookUrl,
                    revision = listRevision,
                    loadWordCount = AppConfig.changeSourceLoadWordCount,
                    onOpenToc = ::openToc,
                    getScore = viewModel::getBookScore,
                    onSetScore = ::setBookScore,
                    onTop = ::topSource,
                    onBottom = ::bottomSource,
                    onEdit = ::editSource,
                    onDisable = ::disableSource,
                    onDelete = ::deleteSource
                )
            }
        }
        val tocComposeView = LegadoComposeView(requireContext()).apply { id = View.generateViewId() }
        binding.tocComposeContainer.addView(
            tocComposeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        tocComposeView.setContent {
            LegadoTheme {
                ChangeChapterTocContent(
                    chapters = tocChapters,
                    listState = tocListState,
                    currentIndex = tocCurrentIndex,
                    onSelect = ::clickChapter
                )
            }
        }
    }

    private fun initSearchView() {
        val searchView = binding.toolBar.menu.findItem(R.id.menu_screen).actionView as SearchView
        searchView.setOnCloseListener {
            showTitle()
            false
        }
        searchView.setOnSearchClickListener {
            binding.toolBar.title = ""
            binding.toolBar.subtitle = ""
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.screen(newText)
                return false
            }

        })
    }

    private fun initBottomBar() {
        binding.tvDur.text = callBack?.oldBook?.originName
        binding.tvDur.setOnClickListener {
            scrollToDurSource()
        }
        binding.ivTop.setOnClickListener {
            lifecycleScope.launch { listState.animateScrollToItem(0) }
        }
        binding.ivBottom.setOnClickListener {
            if (searchBooks.isNotEmpty()) {
                lifecycleScope.launch { listState.animateScrollToItem(searchBooks.lastIndex) }
            }
        }
    }

    private fun initLiveData() {
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            binding.refreshProgressBar.isAutoLoading = it
            if (it) {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_stop_black_24dp)
                    item.setTitle(R.string.stop)
                }
            } else {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_refresh_black_24dp)
                    item.setTitle(R.string.refresh)
                }
            }
            binding.toolBar.menu.applyTint(requireContext())
        }
        lifecycleScope.launch {
            lifecycle.currentStateFlow.first { it.isAtLeast(STARTED) }
            viewModel.searchDataFlow.conflate().collect {
                searchBooks = it.toList()
                listRevision++
                if (searchBooks.isNotEmpty()) listState.scrollToItem(0)
                delay(1000)
            }
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().conflate().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    private val startStopMenuItem: MenuItem?
        get() = binding.toolBar.menu.findItem(R.id.menu_start_stop)

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_check_author -> {
                AppConfig.changeSourceCheckAuthor = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.refresh()
            }

            R.id.menu_load_info -> {
                AppConfig.changeSourceLoadInfo = !item.isChecked
                item.isChecked = !item.isChecked
            }

            R.id.menu_load_toc -> {
                AppConfig.changeSourceLoadToc = !item.isChecked
                item.isChecked = !item.isChecked
            }

            R.id.menu_load_word_count -> {
                AppConfig.changeSourceLoadWordCount = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.onLoadWordCountChecked(item.isChecked)
            }

            R.id.menu_start_stop -> viewModel.startOrStopSearch()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            else -> if (item?.groupId == R.id.source_group && !item.isChecked) {
                item.isChecked = true
                if (item.title.toString() == getString(R.string.all_source)) {
                    AppConfig.searchGroup = ""
                } else {
                    AppConfig.searchGroup = item.title.toString()
                }
                lifecycleScope.launch(IO) {
                    viewModel.stopSearch()
                    if (viewModel.refresh()) {
                        viewModel.startSearch()
                    }
                }
            }
        }
        return false
    }

    private fun scrollToDurSource() {
        val index = searchBooks.indexOfFirst { it.bookUrl == oldBookUrl }
        if (index >= 0) lifecycleScope.launch { listState.animateScrollToItem(index, 60.dpToPx()) }
    }

    private fun openToc(searchBook: SearchBook) {
        this.searchBook = searchBook
        tocChapters = emptyList()
        tocCurrentIndex = 0
        binding.clToc.visible()
        binding.loadingToc.visible()
        val book = searchBook.toBook()
        viewModel.getToc(book, { toc: List<BookChapter>, _: BookSource ->
            tocChapters = toc
            tocCurrentIndex = BookHelp.getDurChapter(viewModel.chapterIndex, viewModel.chapterTitle, toc)
            binding.loadingToc.gone()
            if (toc.isNotEmpty()) {
                lifecycleScope.launch { tocListState.scrollToItem((tocCurrentIndex - 5).coerceAtLeast(0)) }
            }
        }, {
            binding.clToc.gone()
            AppLog.put("单章换源获取目录出错\n$it", it, true)
        })
    }

    private val oldBookUrl: String? get() = callBack?.oldBook?.bookUrl

    private fun topSource(searchBook: SearchBook) {
        viewModel.topSource(searchBook)
        listRevision++
    }

    private fun bottomSource(searchBook: SearchBook) {
        viewModel.bottomSource(searchBook)
        listRevision++
    }

    private fun editSource(searchBook: SearchBook) {
        editSourceResult.launch {
            putExtra("sourceUrl", searchBook.origin)
        }
    }

    private fun disableSource(searchBook: SearchBook) {
        viewModel.disableSource(searchBook)
        listRevision++
    }

    private fun deleteSource(searchBook: SearchBook) {
        viewModel.del(searchBook)
        listRevision++
        if (oldBookUrl == searchBook.bookUrl) {
            viewModel.autoChangeSource(callBack?.oldBook?.type) { book, toc, source ->
                callBack?.changeTo(source, book, toc)
            }
        }
    }

    private fun setBookScore(searchBook: SearchBook, score: Int) {
        viewModel.setBookScore(searchBook, score)
        listRevision++
    }

    private fun clickChapter(bookChapter: BookChapter, nextChapterUrl: String?) {
        searchBook?.let {
            binding.loadingToc.visible()
            viewModel.getContent(it.toBook(), bookChapter, nextChapterUrl, contentSuccess) { msg ->
                binding.loadingToc.gone()
                binding.clToc.gone()
                toastOnUi(msg)
            }
        }
    }

    /**
     * 更新分组菜单
     */
    private fun upGroupMenu() {
        binding.toolBar.menu.findItem(R.id.menu_group)?.subMenu?.transaction { menu ->
            val selectedGroup = AppConfig.searchGroup
            menu.removeGroup(R.id.source_group)
            val allItem = menu.add(R.id.source_group, Menu.NONE, Menu.NONE, R.string.all_source)
            var hasSelectedGroup = false
            groups.forEach { group ->
                menu.add(R.id.source_group, Menu.NONE, Menu.NONE, group)?.let {
                    if (group == selectedGroup) {
                        it.isChecked = true
                        hasSelectedGroup = true
                    }
                }
            }
            menu.setGroupCheckable(R.id.source_group, true, true)
            if (!hasSelectedGroup) {
                allItem.isChecked = true
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            listRevision++
        }
    }

    interface CallBack {
        val oldBook: Book?
        fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>)
        fun replaceContent(content: String)
    }

}
