@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.style1

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.viewpager.widget.ViewPager
import io.legado.app.ui.main.bookshelf.BookshelfGroups
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.ComposeView
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.utils.isCreated
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.collections.set

/**
 * 书架界面
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val groupTabs: ComposeView get() = binding.titleBar.findViewById(R.id.group_tabs)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }
    private var selectedIndex by mutableIntStateOf(0)
    private val bookGroups = mutableStateListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override var onlyUpdateRead = false
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(selectedIndex)

    private fun initView() {
        binding.viewPagerBookshelf.setEdgeEffectColor(primaryColor)
        groupTabs.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        groupTabs.setContent {
            LegadoTheme {
                val motion = LocalLegadoMotion.current
                BookshelfGroups(bookGroups, selectedIndex, { index ->
                    if (selectedIndex == index) {
                        selectedGroup?.let { group ->
                            toastOnUi("${group.groupName}(${fragmentMap[group.groupId]?.getBooksCount() ?: 0})")
                        }
                    } else binding.viewPagerBookshelf.setCurrentItem(index, motion.enabled)
                }, { showDialogFragment(GroupEditDialog(it)) })
            }
        }
        binding.viewPagerBookshelf.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                selectedIndex = position
                onlyUpdateRead = selectedGroup?.onlyUpdateRead ?: false
                AppConfig.saveTabPosition = position
            }
        })
        binding.viewPagerBookshelf.offscreenPageLimit = 1
        binding.viewPagerBookshelf.adapter = adapter
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                adapter.notifyDataSetChanged()
                selectLastTab()
            }
        }
    }

    override fun upSort() {
        adapter.notifyDataSetChanged()
    }

    private fun selectLastTab() {
        binding.viewPagerBookshelf.post {
            selectedIndex = AppConfig.saveTabPosition.coerceIn(0, (bookGroups.size - 1).coerceAtLeast(0))
            onlyUpdateRead = selectedGroup?.onlyUpdateRead ?: false
            binding.viewPagerBookshelf.setCurrentItem(selectedIndex, false)
        }
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getPageTitle(position: Int): CharSequence {
            return bookGroups[position].groupName
        }

        /**
         * 确定视图位置是否更改时调用
         * @return POSITION_NONE 已更改,刷新视图. POSITION_UNCHANGED 未更改,不刷新视图
         */
        override fun getItemPosition(any: Any): Int {
            val fragment = any as BooksFragment
            val position = fragment.position
            val group = bookGroups.getOrNull(position)
            if (fragment.groupId != group?.groupId) {
                return POSITION_NONE
            }
            val bookSort = group.getRealBookSort()
            fragment.updateGroup(group)
            if (fragment.bookSort != bookSort) {
                fragment.upBookSort(bookSort)
            }
            return POSITION_UNCHANGED
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            return BooksFragment(position, group)
        }

        override fun getCount(): Int {
            return bookGroups.size
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            /**
             * Activity recreate 会复用之前的 Fragment，不正确的需要重新创建
             */
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as BooksFragment
            }
            fragmentMap[group.groupId] = fragment
            return fragment
        }

    }
}
