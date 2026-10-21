package io.legado.app.ui.rss.favorites

import android.os.Bundle
import android.view.ViewGroup
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
import io.legado.app.base.BaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityRssFavoritesBinding
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class RssFavoritesActivity : BaseActivity<ActivityRssFavoritesBinding>() {

    override val binding by viewBinding(ActivityRssFavoritesBinding::inflate)
    private val adapter by lazy { TabFragmentPageAdapter() }
    private val groupList = mutableStateListOf<String>()
    private var currentGroup = ""
    private var selectedIndex by mutableIntStateOf(0)
    private var deleteDialog by mutableStateOf<Int?>(null)
    private lateinit var pager: ViewPager

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        pager = binding.viewPager.apply {
            (parent as? ViewGroup)?.removeView(this)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            adapter = this@RssFavoritesActivity.adapter
            addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    selectedIndex = position
                    currentGroup = groupList.getOrNull(position).orEmpty()
                }
            })
        }
        val host = LegadoComposeView(this)
        setSupportActionBar(null)
        setContentView(host)
        host.setContent {
            LegadoTheme {
                RssFavoritesContent(
                    groups = groupList,
                    selectedIndex = selectedIndex,
                    pager = pager,
                    deleteDialog = deleteDialog,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSelectGroup = { index ->
                        selectedIndex = index
                        pager.currentItem = index
                    },
                    onRequestDeleteGroup = { if (groupList.isNotEmpty()) deleteDialog = 1 },
                    onRequestDeleteAll = { deleteDialog = 2 },
                    onConfirmDeleteGroup = {
                        groupList.getOrNull(selectedIndex)?.let { group ->
                            lifecycleScope.launch(IO) { appDb.rssStarDao.deleteByGroup(group) }
                        }
                        deleteDialog = null
                    },
                    onConfirmDeleteAll = {
                        lifecycleScope.launch(IO) { appDb.rssStarDao.deleteAll() }
                        deleteDialog = null
                    },
                    onDismissDialog = { deleteDialog = null }
                )
            }
        }
        pager.currentItem = savedInstanceState?.getInt("CURRENT_POSITION", 0) ?: 0
        upFragments()
    }

    override fun onResume() {
        super.onResume()
        if (currentGroup.isNotEmpty() && groupList.isNotEmpty()) {
            var index = groupList.indexOf(currentGroup)
            if (index == -1) index = pager.currentItem
            lifecycleScope.launch {
                delay(100)
                pager.currentItem = index.coerceIn(groupList.indices)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::pager.isInitialized) outState.putInt("CURRENT_POSITION", pager.currentItem)
    }

    private fun upFragments() {
        lifecycleScope.launch {
            appDb.rssStarDao.flowGroups().catch {
                AppLog.put("订阅分组数据获取失败\n${it.localizedMessage}", it)
            }.distinctUntilChanged().flowOn(IO).collect { groups ->
                groupList.clear()
                groupList.addAll(groups)
                val rememberedIndex = if (currentGroup.isNotEmpty()) groupList.indexOf(currentGroup) else -1
                val targetIndex = rememberedIndex.takeIf { it >= 0 } ?: selectedIndex
                selectedIndex = targetIndex.coerceIn(0, (groupList.size - 1).coerceAtLeast(0))
                adapter.notifyDataSetChanged()
                if (groupList.isNotEmpty()) pager.currentItem = selectedIndex
            }
        }
    }

    private inner class TabFragmentPageAdapter :
        FragmentStatePagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItemPosition(`object`: Any): Int = POSITION_NONE
        override fun getPageTitle(position: Int): CharSequence = groupList[position]
        override fun getItem(position: Int): Fragment = RssFavoritesFragment(groupList[position])
        override fun getCount(): Int = groupList.size
    }
}
