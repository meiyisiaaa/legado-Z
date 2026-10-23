package io.legado.app.ui.main.rss

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.FragmentRssBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.rss.article.ReadRecordDialog
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.favorites.RssFavoritesActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.rss.source.manage.RssSourceActivity
import io.legado.app.ui.rss.subscription.RuleSubActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.applyTint
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.openUrl
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** 订阅界面。列表状态保留在 Fragment，数据仍由原有 DAO flow 提供。 */
class RssFragment() : VMBaseFragment<RssViewModel>(R.layout.fragment_rss), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")
    override val viewModel by viewModels<RssViewModel>()

    private val binding by viewBinding(FragmentRssBinding::bind)
    private val sources = mutableStateListOf<RssSource>()
    private val groups = linkedSetOf<String>()
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var searchKey = ""
    private var groupsMenu: SubMenu? = null
    private var groupsFlowJob: Job? = null
    private var rssFlowJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        binding.legadoRssComposeRoot.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.legadoRssComposeRoot.setContent {
            LegadoTheme {
                RssSubscriptionContent(
                    fragment = this@RssFragment,
                    lifecycle = viewLifecycleOwner.lifecycle,
                    sources = sources,
                    onRuleSubscription = { startActivity<RuleSubActivity>() },
                    onOpen = ::openRss,
                    onEdit = ::edit,
                    onTop = ::toTop,
                    onLogin = ::login,
                    onDelete = ::del,
                    onDisable = ::disable
                )
            }
        }
        initGroupData()
        searchKey = searchView.query?.toString().orEmpty()
        upRssFlowJob(searchKey)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_rss, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_rss_media_tasks -> io.legado.app.ui.rss.media.RssMediaUi.showTasks(requireActivity())
            R.id.menu_read_record -> showDialogFragment<ReadRecordDialog>()
            R.id.menu_rss_config -> startActivity<RssSourceActivity>()
            R.id.menu_rss_star -> startActivity<RssFavoritesActivity>()
            else -> if (item.groupId == R.id.menu_group_text) {
                searchView.setQuery("group:${item.title}", true)
            }
        }
    }

    override fun onPause() {
        searchView.clearFocus()
        super.onPause()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach { group ->
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, group)
        }
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = false
        searchView.queryHint = getString(R.string.rss)
        searchView.clearFocus()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                searchKey = newText.orEmpty()
                upRssFlowJob(searchKey)
                return false
            }
        })
    }

    private fun initGroupData() {
        groupsFlowJob?.cancel()
        groupsFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.rssSourceDao.flowEnabledGroups().catch {
                AppLog.put("订阅界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME
            ).conflate().collect { values ->
                groups.clear()
                groups.addAll(values)
                upGroupsMenu()
            }
        }
    }

    private fun upRssFlowJob(searchKey: String? = null) {
        rssFlowJob?.cancel()
        rssFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> appDb.rssSourceDao.flowEnabled()
                searchKey.startsWith("group:") -> appDb.rssSourceDao.flowEnabledByGroup(searchKey.substringAfter("group:"))
                else -> appDb.rssSourceDao.flowEnabled(searchKey)
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.RSS_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("订阅界面更新数据出错", it)
            }.flowOn(IO).collect { values ->
                sources.clear()
                sources.addAll(values)
            }
        }
    }

    private fun openRss(rssSource: RssSource) {
        if (rssSource.singleUrl) {
            viewModel.getSingleUrl(rssSource) { url ->
                if (url.startsWith("http", true)) {
                    ReadRssActivity.start(
                        requireContext(), true, rssSource.sourceUrl, rssSource.sourceName, url
                    )
                } else {
                    context?.openUrl(url)
                }
            }
        } else {
            viewModel.launchRssWithHtml(rssSource, {
                startActivity<RssSortActivity> { putExtra("sourceUrl", rssSource.sourceUrl) }
            }) { html ->
                ReadRssActivity.start(
                    requireContext(), true, rssSource.sourceUrl, rssSource.sourceName, startHtml = html
                )
            }
        }
    }

    private fun toTop(rssSource: RssSource) = viewModel.topSource(rssSource)

    private fun login(rssSource: RssSource) {
        startActivity<SourceLoginActivity> {
            putExtra("type", "rssSource")
            putExtra("key", rssSource.sourceUrl)
        }
    }

    private fun edit(rssSource: RssSource) {
        startActivity<RssSourceEditActivity> { putExtra("sourceUrl", rssSource.sourceUrl) }
    }

    private fun del(rssSource: RssSource) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + rssSource.sourceName)
            noButton()
            yesButton { viewModel.del(rssSource) }
        }
    }

    private fun disable(rssSource: RssSource) = viewModel.disable(rssSource)
}
