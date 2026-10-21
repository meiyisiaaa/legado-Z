package io.legado.app.ui.rss.article

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.databinding.FragmentRssArticlesBinding
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class RssArticlesFragment() : VMBaseFragment<RssArticlesViewModel>(R.layout.fragment_rss_articles) {

    constructor(sortName: String, sortUrl: String, searchKey: String?) : this() {
        arguments = Bundle().apply {
            putString("sortName", sortName)
            putString("sortUrl", sortUrl)
            putString("searchKey", searchKey)
        }
    }

    private val binding by viewBinding(FragmentRssArticlesBinding::bind)
    private val activityViewModel by activityViewModels<RssSortViewModel>()
    override val viewModel by viewModels<RssArticlesViewModel>()
    private val isPreload by lazy { activityViewModel.rssSource?.preload ?: false }
    private val articles = mutableStateListOf<RssArticle>()
    private var refreshing by mutableStateOf(false)
    private var loadingMore by mutableStateOf(false)
    private var hasMore by mutableStateOf(false)
    private var loadError by mutableStateOf<String?>(null)
    private var articlesFlowJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.init(arguments)
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                RssArticleListContent(
                    articles = articles,
                    articleStyle = activityViewModel.articleStyle ?: 0,
                    isRefreshing = refreshing,
                    isLoadingMore = loadingMore,
                    hasMore = hasMore,
                    error = loadError,
                    onRefresh = ::loadArticles,
                    onLoadMore = ::scrollToBottom,
                    onRead = ::readRss,
                    preload = isPreload
                )
            }
        }
        initData()
        if (isPreload) {
            loadArticles()
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    loadArticles()
                    this@launch.cancel()
                }
            }
        }
    }

    private fun initData() {
        val rssUrl = activityViewModel.url ?: return
        articlesFlowJob?.cancel()
        articlesFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.rssArticleDao.flowByOriginSort(rssUrl, viewModel.sortName)
                .catch { AppLog.put("订阅文章界面获取数据失败\n${it.localizedMessage}", it) }
                .flowOn(IO)
                .collect { newList ->
                    val oldSize = articles.size
                    articles.clear()
                    articles.addAll(newList)
                    if (loadingMore && !refreshing && newList.size > oldSize) {
                        loadingMore = false
                        loadError = null
                    }
                    delay(200)
                }
        }
    }

    private fun loadArticles() {
        refreshing = true
        loadingMore = true
        hasMore = false
        loadError = null
        activityViewModel.rssSource?.let(viewModel::loadArticles) ?: run {
            refreshing = false
            loadingMore = false
        }
    }

    private fun scrollToBottom(forceLoad: Boolean = false) {
        if (loadingMore) return
        if ((hasMore && articles.isNotEmpty()) || forceLoad) {
            loadingMore = true
            loadError = null
            activityViewModel.rssSource?.let(viewModel::loadMore) ?: run { loadingMore = false }
        }
    }

    override fun observeLiveBus() {
        viewModel.loadErrorLiveData.observe(viewLifecycleOwner) { error ->
            loadError = error
            refreshing = false
            loadingMore = false
        }
        viewModel.loadFinallyLiveData.observe(viewLifecycleOwner) { more ->
            refreshing = false
            loadingMore = false
            hasMore = more
            loadError = null
        }
    }

    private fun readRss(article: RssArticle) {
        ReadRss.readRss(this, article, activityViewModel.rssSource)
    }

}
