@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.os.Bundle
import android.animation.ValueAnimator
import android.text.format.DateUtils
import android.view.MenuItem
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.addCallback
import androidx.core.view.ViewGroupCompat
import androidx.activity.viewModels
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.bookshelf.style2.BookshelfFragment2
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.main.rss.RssFragment
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.utils.isCreated
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.association.ImportDictRuleDialog
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.association.ImportTxtTocRuleDialog
import io.legado.app.utils.StringUtils
import io.legado.app.utils.clearClip
import io.legado.app.utils.getClipText
import kotlin.time.Duration.Companion.hours

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition by mutableIntStateOf(0)
    private var visiblePages by mutableStateOf(listOf(0, 1, 2, 3))
    private var updatingBooks by mutableIntStateOf(0)
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private val EXIT_INTERVAL = 2000L
    private val realPositions = arrayOf(idBookshelf, idExplore, idRss, idMy)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        onBackPressedDispatcher.addCallback(this) {
            if (pagePosition != 0) {
                binding.viewPagerMain.setCurrentItem(0, !AppConfig.isEInkMode && ValueAnimator.areAnimatorsEnabled())
                return@addCallback
            }
            (fragmentMap[getFragmentId(0)] as? BookshelfFragment2)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (BaseReadAloudService.pause) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新
            upVersion()
            //设置本地密码
            setLocalPassword()
            notifyAppCrash()
            //备份同步
            backupSync()
            //设置回调
            viewModel.setActivityCallback(this@MainActivity)
            //自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            readShibboleth(1500)
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                binding.viewPagerMain.postDelayed(2000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    private fun onDestinationReselected(page: Int) {
        when (page) {
            idBookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            idExplore -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        // Before Android 11, an offscreen Compose page can consume insets needed by its siblings.
        ViewGroupCompat.installCompatInsetsDispatch(root)
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = TabFragmentPageAdapter(supportFragmentManager)
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        bottomNavigationView.setContent {
            LegadoTheme {
                val motion = LocalLegadoMotion.current
                MainNavigation(visiblePages, realPositions[pagePosition], updatingBooks) { page ->
                    val position = visiblePages.indexOf(page)
                    if (position == pagePosition) onDestinationReselected(page)
                    else if (position >= 0) viewPagerMain.setCurrentItem(position, motion.enabled)
                }
            }
        }
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@sc
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            if (AppConfig.autoUpdateVariant) {
                if (LocalConfig.lastCheckUpdate + 24.hours.inWholeMilliseconds < System.currentTimeMillis()) {
                    AppUpdate.giteeUpdate.check(lifecycleScope)
                        .onSuccess {
                            showDialogFragment(
                                UpdateDialog(it)
                            )
                        }
                    LocalConfig.lastCheckUpdate = System.currentTimeMillis()
                }
            }
            block.resume(null)
            return@sc
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@sc
        }
        var password = ""
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            customView {
                LegadoComposeView(this@MainActivity).apply {
                    setContent { LegadoTheme { LocalPasswordContent { password = it } } }
                }
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = password
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            updatingBooks = it
        }
        observeEvent<String>(EventBus.RECREATE) {
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                upBottomMenu()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun upBottomMenu() {
        val previousPages = visiblePages
        val selectedPage = visiblePages.getOrNull(pagePosition) ?: idBookshelf
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        var index = 0
        if (showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        if (showRss) {
            index++
            realPositions[index] = idRss
        }
        index++
        realPositions[index] = idMy
        bottomMenuCount = index + 1
        visiblePages = realPositions.take(bottomMenuCount)
        val targetPosition = visiblePages.indexOf(selectedPage).coerceAtLeast(0)
        pagePosition = targetPosition
        val currentAdapter = binding.viewPagerMain.adapter as? TabFragmentPageAdapter
        if (currentAdapter != null) {
            if (previousPages != visiblePages || currentAdapter.groupStyle != AppConfig.bookGroupStyle) {
                fragmentMap.clear()
                binding.viewPagerMain.adapter = TabFragmentPageAdapter(supportFragmentManager)
            } else currentAdapter.notifyDataSetChanged()
            binding.viewPagerMain.setCurrentItem(targetPosition, false)
        }
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        val groupStyle = AppConfig.bookGroupStyle

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val page = when (any) {
                is BookshelfFragment1 -> if (AppConfig.bookGroupStyle != 1) idBookshelf else return POSITION_NONE
                is BookshelfFragment2 -> if (AppConfig.bookGroupStyle == 1) idBookshelf else return POSITION_NONE
                is ExploreFragment -> idExplore
                is RssFragment -> idRss
                is MyFragment -> idMy
                else -> return POSITION_NONE
            }
            val position = visiblePages.indexOf(page)
            if (position < 0) return POSITION_NONE
            return if ((any as MainFragmentInterface).position == position) POSITION_UNCHANGED else POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idBookshelf2 -> BookshelfFragment2(position)
                idExplore -> ExploreFragment(position)
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }

    }

    override fun openImportUi(type:Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source)
            )
        }
    }

    /**
     * 读取导入口令
     */
    fun readShibboleth(delay: Long) {
        binding.viewPagerMain.postDelayed(delay) {
            val text = this@MainActivity.getClipText()
            if (!text.isNullOrBlank()) {
                if ("#L:" in text) {
                    this@MainActivity.clearClip() //清理一下防重复
                    val (url, type, customWord) = StringUtils.unShibboleth(text)
                    when(type) {
                        StringUtils.BOOK_SOURCE ->
                            showDialogFragment(ImportBookSourceDialog(url))
                        StringUtils.RSS_SOURCE ->
                            showDialogFragment(ImportRssSourceDialog(url))
                        StringUtils.DICT_RULE ->
                            showDialogFragment(ImportDictRuleDialog(url))
                        StringUtils.REPLACE_RULE ->
                            showDialogFragment(ImportReplaceRuleDialog(url))
                        StringUtils.TOC_RULE ->
                            showDialogFragment(ImportTxtTocRuleDialog(url))
                        StringUtils.TTS_RULE ->
                            showDialogFragment(ImportHttpTtsDialog(url))
                        else -> showDialogFragment(ImportHttpTtsDialog(url))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LifecycleHelp.activitySize() == 1) {
            readShibboleth(500)
        }
    }

}
