package io.legado.app.ui.rss.article

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssArtivlesBinding
import io.legado.app.help.source.sortUrls
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.rss.article.ReadRecordDialog
import io.legado.app.utils.GSONStrict
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.shouldHideSoftInput
import io.legado.app.utils.hideSoftInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssSortActivity : VMBaseActivity<ActivityRssArtivlesBinding, RssSortViewModel>(), VariableDialog.Callback {

    override val binding by viewBinding(ActivityRssArtivlesBinding::inflate)
    override val viewModel by viewModels<RssSortViewModel>()

    private val adapter by lazy { TabFragmentPageAdapter() }
    private var sortUrls: List<Pair<String, String>>? = null
    private val sortList = mutableStateListOf<Pair<String, String>>()
    private var selectedSortIndex by mutableIntStateOf(0)
    private var screenTitle by mutableStateOf("")
    private lateinit var pager: ViewPager
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.initData(intent) {
                sortUrls = null
                upFragments()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.initData(intent) { upFragments() }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        pager = binding.viewPager.apply {
            (parent as? ViewGroup)?.removeView(this)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            adapter = this@RssSortActivity.adapter
            addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    selectedSortIndex = position
                }
            })
        }
        val host = LegadoComposeView(this)
        setSupportActionBar(null)
        setContentView(host)
        host.setContent {
            LegadoTheme {
                RssSortContent(
                    title = screenTitle,
                    sorts = sortList,
                    selectedIndex = selectedSortIndex,
                    pager = pager,
                    hasSearch = !viewModel.rssSource?.searchUrl.isNullOrBlank(),
                    hasLogin = !viewModel.rssSource?.loginUrl.isNullOrBlank(),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSortSelected = { index ->
                        selectedSortIndex = index
                        pager.currentItem = index
                    },
                    onAction = ::performAction,
                    onSearch = { query ->
                        val sourceUrl = viewModel.rssSource?.sourceUrl
                        if (!query.isBlank() && sourceUrl != null) start(this, null, sourceUrl, query)
                    }
                )
            }
        }
        viewModel.initData(intent) { upFragments() }
        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.searchKey != null) {
                viewModel.searchKey = null
                upFragments()
            } else {
                finish()
            }
        }
        savedInstanceState?.getInt("CURRENT_POSITION", 0)?.let { pager.post { pager.currentItem = it } }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let { if (it.shouldHideSoftInput(ev)) it.hideSoftInput() }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::pager.isInitialized) outState.putInt("CURRENT_POSITION", pager.currentItem)
    }

    override fun onDestroy() {
        if (::pager.isInitialized) pager.adapter = null
        super.onDestroy()
    }

    private fun performAction(actionId: Int) {
        when (actionId) {
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }
            R.id.menu_refresh_sort -> {
                sortUrls = null
                viewModel.clearSortCache { upFragments() }
            }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_edit_source -> viewModel.rssSource?.let {
                editSourceResult.launch { putExtra("sourceUrl", it.sourceUrl) }
            }
            R.id.menu_clear -> viewModel.url?.let { viewModel.clearArticles() }
            R.id.menu_switch_layout -> {
                viewModel.switchLayout()
                upFragments()
            }
            R.id.menu_read_record -> showDialogFragment(ReadRecordDialog(viewModel.rssSource?.sourceUrl))
        }
    }

    private fun upFragments() {
        lifecycleScope.launch {
            val source = viewModel.rssSource ?: return@launch
            val urls = when {
                viewModel.searchKey != null -> source.searchUrl?.let { listOf("搜索" to it) }.orEmpty()
                !viewModel.sortUrl.isNullOrBlank() -> {
                    val url = viewModel.sortUrl!!
                    runCatching {
                        if (url.isJsonObject()) {
                            GSONStrict.fromJsonObject<Map<String, String>>(url).getOrThrow().map { it.key to it.value }
                        } else listOf("" to url)
                    }.getOrElse { listOf("" to url) }
                }
                else -> {
                    if (sortUrls == null) sortUrls = source.sortUrls()
                    sortUrls.orEmpty()
                }
            }
            sortList.clear()
            sortList.addAll(urls)
            val currentTitle = if (sortList.size == 1 && sortList.first().first.isNotEmpty()) {
                viewModel.searchKey ?: sortList.first().first
            } else {
                viewModel.sourceName.orEmpty()
            }
            screenTitle = currentTitle.ifBlank { source.sourceName }
            if (selectedSortIndex !in sortList.indices) selectedSortIndex = 0
            adapter.notifyDataSetChanged()
            if (sortList.isNotEmpty()) pager.currentItem = selectedSortIndex
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.rssSource
            if (source == null) {
                toastOnUi("源不存在")
                return@launch
            }
            val comment = source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(Dispatchers.IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    private inner class TabFragmentPageAdapter :
        FragmentStatePagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItemPosition(`object`: Any): Int = POSITION_NONE
        override fun getPageTitle(position: Int): CharSequence = sortList[position].first
        override fun getItem(position: Int): Fragment {
            val sort = sortList[position]
            return RssArticlesFragment(sort.first, sort.second, viewModel.searchKey)
        }
        override fun getCount(): Int = sortList.size
    }

    companion object {
        fun start(context: Context, sortUrl: String?, sourceUrl: String, key: String? = null) {
            context.startActivity<RssSortActivity> {
                putExtra("sortUrl", sortUrl)
                putExtra("sourceUrl", sourceUrl)
                putExtra("key", key)
            }
        }
    }
}
