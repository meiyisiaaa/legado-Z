package io.legado.app.ui.rss.favorites

import android.os.Bundle
import android.view.View
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssStar
import io.legado.app.databinding.FragmentRssArticlesBinding
import io.legado.app.ui.rss.article.RssFavoriteListContent
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class RssFavoritesFragment() : VMBaseFragment<RssFavoritesViewModel>(R.layout.fragment_rss_articles) {

    constructor(group: String) : this() {
        arguments = Bundle().apply { putString("group", group) }
    }

    private val binding by viewBinding(FragmentRssArticlesBinding::bind)
    override val viewModel by viewModels<RssFavoritesViewModel>()
    private val favorites = mutableStateListOf<RssStar>()
    private var deleteTarget by mutableStateOf<RssStar?>(null)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                RssFavoriteListContent(
                    favorites = favorites,
                    onRead = { ReadRss.readRss(this@RssFavoritesFragment, it.toRssArticle()) },
                    onDelete = { deleteTarget = it }
                )
                deleteTarget?.let { target ->
                    AlertDialog(
                        onDismissRequest = { deleteTarget = null },
                        title = { Text(stringResource(R.string.draw)) },
                        text = { Text("${stringResource(R.string.sure_del)}\n<${target.title}>") },
                        confirmButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch(IO) {
                                    appDb.rssStarDao.delete(target.origin, target.link)
                                }
                                deleteTarget = null
                            }) { Text(stringResource(R.string.yes)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.no)) }
                        }
                    )
                }
            }
        }
        loadArticles()
    }

    private fun loadArticles() {
        viewLifecycleOwner.lifecycleScope.launch {
            val group = arguments?.getString("group") ?: "默认分组"
            appDb.rssStarDao.flowByGroup(group).catch {
                AppLog.put("订阅文章界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect { rows ->
                favorites.clear()
                favorites.addAll(rows)
            }
        }
    }
}
