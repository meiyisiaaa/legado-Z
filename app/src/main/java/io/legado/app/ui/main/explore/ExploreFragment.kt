package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 发现界面。Compose owns the list and interaction state; scripts keep a small View island. */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")
    override val viewModel by viewModels<ExploreViewModel>()

    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var uiState by mutableStateOf(ExploreUiState())
    private var groupsMenu: SubMenu? = null
    private var exploreFlowJob: Job? = null
    private var groupsFlowJob: Job? = null
    private var exploreFlowGeneration = 0L
    private var groupsFlowGeneration = 0L
    private val kindLoadJobs = linkedMapOf<String, Job>()
    private val kindLoadTokens = linkedMapOf<String, Long>()
    private var nextKindLoadToken = 0L
    private var saveInfoMapJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        uiState = ExploreUiState()
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        binding.legadoComposeHost.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.legadoComposeHost.setContent {
            LegadoTheme {
                ExploreContent(
                    state = uiState,
                    onToggleSource = ::toggleSource,
                    onSourceAction = ::onSourceAction,
                    onOpenExplore = ::openExplore,
                    onRetrySources = { upExploreData(uiState.query) },
                    onRetryGroups = ::initGroupData,
                    kindContent = { source, kinds, revision, resumed ->
                        ExploreKindControls(
                            source = source,
                            kinds = kinds,
                            revision = revision,
                            active = resumed,
                            onOpenExplore = ::openExplore,
                            onRefresh = ::refreshSource
                        )
                    }
                )
            }
        }
        initGroupData()
        val query = searchView.query?.toString().orEmpty()
        uiState = uiState.copy(
            query = query,
            selectedGroup = query.takeIf { it.startsWith("group:") }?.substringAfter("group:")
        )
        upExploreData(query)
    }

    private fun initSearchView() {
        searchView.apply {
            applyTint(primaryTextColor)
            isSubmitButtonEnabled = true
            queryHint = getString(R.string.screen_find)
            clearFocus()
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    onQueryChange(newText.orEmpty())
                    return false
                }
            })
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        uiState.groups.forEach { group ->
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, group)
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        if (item.groupId == R.id.menu_group_text) {
            searchView.setQuery("group:${item.title}", true)
        }
    }

    private fun initGroupData() {
        val generation = ++groupsFlowGeneration
        groupsFlowJob?.cancel()
        uiState = uiState.copy(
            groups = emptyList(),
            isLoadingGroups = true,
            groupsError = null
        )
        upGroupsMenu()
        groupsFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                appDb.bookSourceDao.flowExploreGroups()
                    .flowWithLifecycleAndDatabaseChange(
                        viewLifecycleOwner.lifecycle,
                        Lifecycle.State.RESUMED,
                        AppDatabase.BOOK_SOURCE_TABLE_NAME
                    )
                    .conflate()
                    .distinctUntilChanged()
                    .collect { groups ->
                        if (generation != groupsFlowGeneration) return@collect
                        uiState = uiState.copy(
                            groups = groups,
                            isLoadingGroups = false,
                            groupsError = null
                        )
                        upGroupsMenu()
                        delay(500)
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == groupsFlowGeneration) {
                    AppLog.put("发现界面获取分组数据失败", error)
                    uiState = uiState.copy(
                        groups = emptyList(),
                        groupsError = getString(R.string.error_get_data)
                    )
                    upGroupsMenu()
                }
            } finally {
                if (generation == groupsFlowGeneration) {
                    uiState = uiState.copy(isLoadingGroups = false)
                }
            }
        }
    }

    private fun onQueryChange(query: String) {
        val group = query.takeIf { it.startsWith("group:") }?.substringAfter("group:")
        uiState = uiState.copy(query = query, selectedGroup = group)
        upExploreData(query)
    }

    private fun upExploreData(searchKey: String? = null) {
        val generation = ++exploreFlowGeneration
        exploreFlowJob?.cancel()
        uiState = uiState.copy(
            sources = emptyList(),
            isLoadingSources = true,
            sourcesError = null
        )
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val sourceFlow = when {
                    searchKey.isNullOrBlank() -> appDb.bookSourceDao.flowExplore()
                    searchKey.startsWith("group:") -> appDb.bookSourceDao.flowGroupExplore(searchKey.substringAfter("group:"))
                    else -> appDb.bookSourceDao.flowExplore(searchKey)
                }
                sourceFlow
                    .flowWithLifecycleAndDatabaseChange(
                        viewLifecycleOwner.lifecycle,
                        Lifecycle.State.RESUMED,
                        AppDatabase.BOOK_SOURCE_TABLE_NAME
                    )
                    .conflate()
                    .flowOn(IO)
                    .collect { sources ->
                        if (generation != exploreFlowGeneration) return@collect
                        val previousExpandedUrl = uiState.expandedSourceUrl
                        val expandedStillExists = previousExpandedUrl?.let { expandedUrl ->
                            sources.any { it.bookSourceUrl == expandedUrl }
                        } ?: true
                        uiState = uiState.copy(
                            sources = sources,
                            isLoadingSources = false,
                            sourcesError = null,
                            expandedSourceUrl = if (expandedStillExists) uiState.expandedSourceUrl else null
                        )
                        if (!expandedStillExists) {
                            previousExpandedUrl?.let(::cancelKindLoad)
                        }
                        delay(500)
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == exploreFlowGeneration) {
                    AppLog.put("发现界面更新数据出错", error)
                    uiState = uiState.copy(
                        sources = emptyList(),
                        sourcesError = getString(R.string.error_get_data)
                    )
                }
            } finally {
                if (generation == exploreFlowGeneration) {
                    uiState = uiState.copy(isLoadingSources = false)
                }
            }
        }
    }

    private fun toggleSource(source: BookSourcePart) {
        val sourceUrl = source.bookSourceUrl
        if (uiState.expandedSourceUrl == sourceUrl) {
            uiState = uiState.copy(expandedSourceUrl = null)
            return
        }
        uiState = uiState.copy(expandedSourceUrl = sourceUrl)
        if (!uiState.kindsBySource.containsKey(sourceUrl)) loadKinds(source)
    }

    private fun loadKinds(source: BookSourcePart, refresh: Boolean = false) {
        val sourceUrl = source.bookSourceUrl
        kindLoadJobs.remove(sourceUrl)?.cancel()
        val token = ++nextKindLoadToken
        kindLoadTokens[sourceUrl] = token
        uiState = uiState.copy(
            loadingSourceUrls = uiState.loadingSourceUrls + sourceUrl,
            kindErrorsBySource = uiState.kindErrorsBySource - sourceUrl
        )
        kindLoadJobs[sourceUrl] = viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (refresh) source.clearExploreKindsCache()
                val kinds = withContext(IO) { source.exploreKinds() }
                val errorKind = kinds.firstOrNull { it.title.startsWith("ERROR:") }
                val kindError = errorKind?.title?.removePrefix("ERROR:")
                    ?.takeIf(String::isNotBlank)
                    ?: errorKind?.let { getString(R.string.error_get_data) }
                uiState = uiState.copy(
                    kindsBySource = uiState.kindsBySource + (sourceUrl to kinds),
                    kindErrorsBySource = if (kindError == null) {
                        uiState.kindErrorsBySource - sourceUrl
                    } else {
                        uiState.kindErrorsBySource + (sourceUrl to kindError)
                    },
                    renderRevisionBySource = uiState.renderRevisionBySource +
                        (sourceUrl to ((uiState.renderRevisionBySource[sourceUrl] ?: 0) + 1))
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.put("发现书源分类加载失败: $sourceUrl", error)
                uiState = uiState.copy(
                    kindsBySource = uiState.kindsBySource + (sourceUrl to emptyList()),
                    kindErrorsBySource = uiState.kindErrorsBySource + (sourceUrl to
                        (error.localizedMessage?.takeIf(String::isNotBlank) ?: getString(R.string.error_get_data)))
                )
            } finally {
                if (kindLoadTokens[sourceUrl] == token) {
                    kindLoadTokens.remove(sourceUrl)
                    kindLoadJobs.remove(sourceUrl)
                    uiState = uiState.copy(loadingSourceUrls = uiState.loadingSourceUrls - sourceUrl)
                }
            }
        }
    }

    private fun refreshSource(sourceUrl: String) {
        val source = uiState.sources.firstOrNull { it.bookSourceUrl == sourceUrl } ?: return
        uiState = uiState.copy(kindsBySource = uiState.kindsBySource - sourceUrl)
        loadKinds(source, refresh = true)
    }

    private fun cancelKindLoad(sourceUrl: String) {
        kindLoadTokens.remove(sourceUrl)
        kindLoadJobs.remove(sourceUrl)?.cancel()
        uiState = uiState.copy(loadingSourceUrls = uiState.loadingSourceUrls - sourceUrl)
    }

    private fun onSourceAction(source: BookSourcePart, action: ExploreSourceAction) {
        when (action) {
            ExploreSourceAction.Edit -> editSource(source.bookSourceUrl)
            ExploreSourceAction.Top -> viewModel.topSource(source)
            ExploreSourceAction.Login -> loginSource(source.bookSourceUrl)
            ExploreSourceAction.Search -> searchBook(source)
            ExploreSourceAction.Refresh -> refreshSource(source.bookSourceUrl)
            ExploreSourceAction.Delete -> deleteSource(source)
        }
    }

    override fun onResume() {
        super.onResume()
        uiState = uiState.copy(isResumed = true)
        uiState.expandedSourceUrl?.takeIf { it !in uiState.kindsBySource }?.let { sourceUrl ->
            uiState.sources.firstOrNull { it.bookSourceUrl == sourceUrl }?.let(::loadKinds)
        }
    }

    override fun onPause() {
        searchView.clearFocus()
        uiState = uiState.copy(
            isResumed = false,
            kindsBySource = emptyMap(),
            renderRevisionBySource = emptyMap(),
            loadingSourceUrls = emptySet()
        )
        kindLoadJobs.values.forEach(Job::cancel)
        kindLoadJobs.clear()
        kindLoadTokens.clear()
        saveInfoMapJob?.cancel()
        saveInfoMapJob = viewLifecycleOwner.lifecycleScope.launch {
            ExploreAdapter.exploreInfoMapList.snapshot()
                .values
                .filter { it.needSave }
                .map { infoMap -> launch { infoMap.saveNow() } }
                .joinAll()
        }
        super.onPause()
    }

    override fun onDestroyView() {
        exploreFlowGeneration++
        groupsFlowGeneration++
        exploreFlowJob?.cancel()
        groupsFlowJob?.cancel()
        kindLoadJobs.values.forEach(Job::cancel)
        kindLoadJobs.clear()
        kindLoadTokens.clear()
        super.onDestroyView()
    }

    fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> { putExtra("sourceUrl", sourceUrl) }
    }

    private fun loginSource(sourceUrl: String) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "bookSource")
            putExtra("key", sourceUrl)
        }
    }

    fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton { viewModel.deleteSource(source) }
        }
    }

    fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(requireContext(), bookSource)
    }

    fun compressExplore() {
        if (uiState.expandedSourceUrl != null) {
            uiState = uiState.copy(expandedSourceUrl = null)
        } else {
            uiState = uiState.copy(scrollToTopRequest = uiState.scrollToTopRequest + 1)
        }
    }
}
