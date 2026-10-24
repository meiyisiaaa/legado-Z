package io.legado.app.ui.rss.source.debug

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssSourceDebugBinding
import io.legado.app.help.source.sortUrls
import io.legado.app.ui.book.source.debug.SourceDebugContent
import io.legado.app.ui.book.source.debug.SourceDebugSuggestion
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

class RssSourceDebugActivity : VMBaseActivity<ActivityRssSourceDebugBinding, RssSourceDebugModel>() {

    override val binding by viewBinding(ActivityRssSourceDebugBinding::inflate)
    override val viewModel by viewModels<RssSourceDebugModel>()

    private val logs = mutableStateListOf<String>()
    private var query by mutableStateOf("")
    private var showingHelp by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)
    private var suggestions by mutableStateOf(
        listOf(SourceDebugSuggestion("我的", "我的"), SourceDebugSuggestion("系统", "系统"))
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        setSupportActionBar(null)
        setContentView(LegadoComposeView(this).apply {
            id = R.id.legado_rss_source_compose_root
            setContent {
                LegadoTheme {
                    SourceDebugContent(
                        title = getString(R.string.debug_source),
                        queryHintRes = R.string.search,
                        query = query,
                        suggestions = suggestions,
                        logs = logs,
                        loading = isLoading,
                        showHelp = showingHelp,
                        showScan = false,
                        menuItems = listOf(
                            R.id.menu_list_src to R.string.list_src,
                            R.id.menu_content_src to R.string.content_src
                        ),
                        onQueryChange = { query = it },
                        onSearch = ::startSearch,
                        onScan = {},
                        onAction = ::performAction,
                        onBack = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        })
        viewModel.initData(intent.getStringExtra("key"), ::initHelpView)
        viewModel.observe { state, message ->
            lifecycleScope.launch {
                logs += message
                if (state == -1 || state == 1000) isLoading = false
            }
        }
    }

    private fun initHelpView() {
        suggestions = listOf(
            SourceDebugSuggestion("我的", "我的"),
            SourceDebugSuggestion("系统", "系统"),
            SourceDebugSuggestion("内容页示例", "https://m.qidian.com/book/1015609210")
        )
        initSortKinds()
    }

    private fun initSortKinds() {
        lifecycleScope.launch {
            val kinds = viewModel.rssSource?.sortUrls()?.filter { it.second.isNotBlank() }.orEmpty()
            val first = kinds.firstOrNull()
            if (first != null) {
                if (first.first.startsWith("ERROR:")) {
                    logs += "获取分类出错\n${first.second}"
                    showingHelp = false
                }
                suggestions = suggestions.filterNot { it.label == "分类示例" } + SourceDebugSuggestion(
                    label = "分类示例",
                    value = "${first.first}::${first.second}",
                    choices = kinds.map { it.first to "${it.first}::${it.second}" }
                )
            }
        }
    }

    private fun startSearch(key: String) {
        query = key
        logs.clear()
        showingHelp = false
        isLoading = true
        viewModel.startDebug(key, { isLoading = true }, {
            isLoading = false
            toastOnUi("未获取到订阅源")
        })
    }

    private fun performAction(id: Int) {
        when (id) {
            R.id.menu_list_src -> showDialogFragment(TextDialog("Html", viewModel.listSrc))
            R.id.menu_content_src -> showDialogFragment(TextDialog("Html", viewModel.contentSrc))
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_source_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        performAction(item.itemId)
        return super.onCompatOptionsItemSelected(item)
    }
}
