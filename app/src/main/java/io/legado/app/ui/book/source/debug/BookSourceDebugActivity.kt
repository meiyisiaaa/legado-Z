package io.legado.app.ui.book.source.debug

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivitySourceDebugBinding
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

class BookSourceDebugActivity : VMBaseActivity<ActivitySourceDebugBinding, BookSourceDebugModel>() {

    override val binding by viewBinding(ActivitySourceDebugBinding::inflate)
    override val viewModel by viewModels<BookSourceDebugModel>()

    private val logs = mutableStateListOf<String>()
    private var query by mutableStateOf("")
    private var showingHelp by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)
    private var suggestions by mutableStateOf(
        listOf(
            SourceDebugSuggestion("我的", "我的"),
            SourceDebugSuggestion("系统", "系统"),
            SourceDebugSuggestion("详情页示例", "https://m.qidian.com/book/1015609210"),
            SourceDebugSuggestion("目录页示例", "++https://www.zhaishuyuan.com/read/30394"),
            SourceDebugSuggestion("正文页示例", "--https://www.zhaishuyuan.com/chapter/30394/20940996")
        )
    )
    private var exploreChoices: List<Pair<String, String>> = emptyList()

    private val qrCodeResult = registerForActivityResult(QrCodeResult()) { result ->
        result?.let(::startSearch)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        setSupportActionBar(null)
        setContentView(LegadoComposeView(this).apply {
            id = R.id.legado_book_source_compose_root
            setContent {
                LegadoTheme {
                    SourceDebugContent(
                        title = getString(R.string.debug_source),
                        query = query,
                        suggestions = suggestions,
                        logs = logs,
                        loading = isLoading,
                        showHelp = showingHelp,
                        menuItems = listOf(
                            R.id.menu_search_src to R.string.search_src,
                            R.id.menu_book_src to R.string.boo_src,
                            R.id.menu_toc_src to R.string.toc_src,
                            R.id.menu_content_src to R.string.content_src,
                            R.id.menu_refresh_explore to R.string.refresh_explore,
                            R.id.menu_help to R.string.help
                        ),
                        onQueryChange = { query = it },
                        onSearch = ::startSearch,
                        onScan = { qrCodeResult.launch() },
                        onAction = ::performAction,
                        onBack = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        })
        viewModel.init(intent.getStringExtra("key"), ::initHelpView)
        viewModel.observe { state, message ->
            lifecycleScope.launch {
                logs += message
                if (state == -1 || state == 1000) isLoading = false
            }
        }
    }

    private fun initHelpView() {
        val checkKeyWord = viewModel.bookSource?.ruleSearch?.checkKeyWord?.takeIf(String::isNotBlank) ?: "我的"
        suggestions = listOf(
            SourceDebugSuggestion(checkKeyWord, checkKeyWord),
            SourceDebugSuggestion("系统", "系统"),
            SourceDebugSuggestion("详情页示例", "https://m.qidian.com/book/1015609210"),
            SourceDebugSuggestion("目录页示例", "++https://www.zhaishuyuan.com/read/30394"),
            SourceDebugSuggestion("正文页示例", "--https://www.zhaishuyuan.com/chapter/30394/20940996")
        )
        initExploreKinds()
    }

    private fun initExploreKinds() {
        lifecycleScope.launch {
            val kinds = viewModel.bookSource?.exploreKinds()?.filter { !it.url.isNullOrBlank() }.orEmpty()
            exploreChoices = kinds.map { (it.title ?: "") to "${it.title}::${it.url}" }
            val first = kinds.firstOrNull() ?: return@launch
            val value = "${first.title}::${first.url}"
            if (first.title.startsWith("ERROR:")) {
                logs += "获取发现出错\n${first.url}"
                showingHelp = false
            }
            suggestions = suggestions.filterNot { it.label == "发现示例" } + SourceDebugSuggestion(
                label = "发现示例",
                value = value,
                choices = exploreChoices
            )
        }
    }

    private fun startSearch(key: String) {
        query = key
        logs.clear()
        showingHelp = false
        isLoading = true
        viewModel.startDebug(key, { isLoading = true }, {
            isLoading = false
            toastOnUi("未获取到书源")
        })
    }

    private fun performAction(id: Int) {
        when (id) {
            R.id.menu_search_src -> showDialogFragment(TextDialog("html", viewModel.searchSrc))
            R.id.menu_book_src -> showDialogFragment(TextDialog("html", viewModel.bookSrc))
            R.id.menu_toc_src -> showDialogFragment(TextDialog("html", viewModel.tocSrc))
            R.id.menu_content_src -> showDialogFragment(TextDialog("html", viewModel.contentSrc))
            R.id.menu_refresh_explore -> lifecycleScope.launch {
                viewModel.bookSource?.clearExploreKindsCache()
                logs.clear()
                showingHelp = true
                initExploreKinds()
            }
            R.id.menu_help -> showHelp("debugHelp")
            R.id.menu_scan -> qrCodeResult.launch()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_source_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        performAction(item.itemId)
        return super.onCompatOptionsItemSelected(item)
    }
}
