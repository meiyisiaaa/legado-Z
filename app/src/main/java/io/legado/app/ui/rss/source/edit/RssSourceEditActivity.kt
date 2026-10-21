package io.legado.app.ui.rss.source.edit

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.RssSource
import io.legado.app.databinding.ActivityRssSourceEditBinding
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.book.source.edit.SourceEditAction
import io.legado.app.ui.book.source.edit.SourceEditChoice
import io.legado.app.ui.book.source.edit.SourceEditContent
import io.legado.app.ui.book.source.edit.SourceEditSwitch
import io.legado.app.ui.widget.dialog.UrlOptionDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.utils.GSON
import io.legado.app.utils.imeHeight
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.share
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.text.isNotEmpty

class RssSourceEditActivity :
    VMBaseActivity<ActivityRssSourceEditBinding, RssSourceEditViewModel>(),
    KeyboardToolPop.CallBack,
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityRssSourceEditBinding::inflate)
    override val viewModel by viewModels<RssSourceEditViewModel>()
    private var composeRoot: ComposeView? = null
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, composeRoot ?: binding.root, this)
    }
    private val adapter by lazy { RssSourceEditAdapter() }
    private val sourceEntities: ArrayList<EditEntity> = ArrayList()
    private val listEntities: ArrayList<EditEntity> = ArrayList()
    private val webViewEntities: ArrayList<EditEntity> = ArrayList()
    private val startEntities: ArrayList<EditEntity> = ArrayList()
    private val composeEntities = mutableStateListOf<EditEntity>()
    private var selectedEditTab by mutableStateOf(0)
    private var sourceEnabled by mutableStateOf(true)
    private var singleUrl by mutableStateOf(false)
    private var cookieEnabled by mutableStateOf(true)
    private var preloadEnabled by mutableStateOf(false)
    private var autoCompleteEnabled by mutableStateOf(false)
    private var sourceTypeIndex by mutableStateOf(0)
    private var articleStyleIndex by mutableStateOf(0)
    private val selectDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                sendText(uri.toString())
            } else {
                sendText(uri.path.toString())
            }
        }
    }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it?.let {
            viewModel.importSource(it) { source: RssSource ->
                upSourceView(source)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        autoCompleteEnabled = viewModel.autoComplete
        val root = LegadoComposeView(this).apply { id = R.id.legado_rss_source_compose_root }
        composeRoot = root
        setSupportActionBar(null)
        setContentView(root)
        root.setContent {
            LegadoTheme {
                SourceEditContent(
                    title = getString(R.string.rss_source_edit),
                    tabs = listOf(
                        getString(R.string.source_tab_base),
                        getString(R.string.source_tab_start),
                        getString(R.string.source_tab_list),
                        "WEB_VIEW"
                    ),
                    selectedTab = selectedEditTab,
                    entities = composeEntities,
                    switches = listOf(
                        SourceEditSwitch("enabled", getString(R.string.is_enable), sourceEnabled),
                        SourceEditSwitch("singleUrl", getString(R.string.single_url), singleUrl),
                        SourceEditSwitch("cookie", getString(R.string.auto_save_cookie), cookieEnabled),
                        SourceEditSwitch("preload", getString(R.string.enable_preload), preloadEnabled)
                    ),
                    choices = listOf(
                        SourceEditChoice("type", getString(R.string.book_type), resources.getStringArray(R.array.rss_type).toList(), sourceTypeIndex),
                        SourceEditChoice("articleStyle", getString(R.string.layout_type), resources.getStringArray(R.array.layout_type).toList(), articleStyleIndex)
                    ),
                    quickActions = listOf(
                        SourceEditAction(R.id.menu_fullscreen_edit, R.string.edit_content, R.drawable.ic_code),
                        SourceEditAction(R.id.menu_save, R.string.action_save, R.drawable.ic_save),
                        SourceEditAction(R.id.menu_debug_source, R.string.debug_source, R.drawable.ic_bug_report)
                    ),
                    menuActions = listOfNotNull(
                        viewModel.rssSource?.loginUrl?.takeIf(String::isNotBlank)?.let { SourceEditAction(R.id.menu_login, R.string.login) },
                        SourceEditAction(R.id.menu_clear_cookie, R.string.cookie),
                        SourceEditAction(R.id.menu_auto_complete, R.string.auto_complete, checked = autoCompleteEnabled),
                        SourceEditAction(R.id.menu_copy_source, R.string.copy_source),
                        SourceEditAction(R.id.menu_paste_source, R.string.paste_source),
                        SourceEditAction(R.id.menu_set_source_variable, R.string.set_source_variable),
                        SourceEditAction(R.id.menu_qr_code_camera, R.string.import_by_qr_code),
                        SourceEditAction(R.id.menu_share_qr, R.string.qr_share),
                        SourceEditAction(R.id.menu_share_str, R.string.str_share),
                        SourceEditAction(R.id.menu_log, R.string.log),
                        SourceEditAction(R.id.menu_help, R.string.help)
                    ),
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onTabSelected = ::setEditEntities,
                    onSwitchChange = ::onSourceSwitchChanged,
                    onChoiceChange = { key, index ->
                        when (key) {
                            "type" -> sourceTypeIndex = index
                            "articleStyle" -> articleStyleIndex = index
                        }
                    },
                    onAction = ::performAction
                )
            }
        }
        softKeyboardTool.attachToWindow(window)
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus is EditText) newFocus.postDelayed({ sendText("") }, 120)
        }
        viewModel.initData(intent) {
            upSourceView(viewModel.rssSource)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!LocalConfig.ruleHelpVersionIsLast) {
            showHelp("rssRuleHelp")
        }
    }

    override fun finish() {
        val source = getRssSource()
        if (!source.equal(viewModel.rssSource ?: RssSource())) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) {
                    super.finish()
                }
            }
        } else {
            super.finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.source_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !viewModel.rssSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_auto_complete)?.isChecked = viewModel.autoComplete
        return super.onMenuOpened(featureId, menu)
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val editedText = CodeTextTransfer.readResult(this, result.data) {
            toastOnUi(R.string.error_read_file)
        }
        if (result.resultCode == RESULT_OK) {
            val view = window.decorView.findFocus()
            if (view is EditText) {
                editedText?.let {
                    view.setText(it)
                }
                result.data?.getIntExtra("cursorPosition", -1)?.takeIf { it in 0..view.text.length }?.let {
                    view.setSelection(it)
                }
            } else {
                toastOnUi(R.string.focus_lost_on_textbox)
            }
        }
    }
    private fun onFullEditClicked() {
        val view = window.decorView.findFocus()
        if (view is EditText) {
            val hint = findParentTextInputLayout(view)?.hint?.toString()
            val currentText = view.text.toString()
            val intent = Intent(this, CodeEditActivity::class.java).apply {
                putExtra("title", hint)
                putExtra("cursorPosition", view.selectionStart)
            }
            CodeTextTransfer.launch(this, intent, currentText) { textEditLauncher.launch(it) }
        }
        else {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        performAction(item.itemId)
        return super.onCompatOptionsItemSelected(item)
    }

    private fun performAction(actionId: Int) {
        when (actionId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()

            R.id.menu_save -> viewModel.save(getRssSource()) {
                setResult(RESULT_OK)
                finish()
            }

            R.id.menu_debug_source -> viewModel.save(getRssSource()) { source ->
                startActivity<RssSourceDebugActivity> {
                    putExtra("key", source.sourceUrl)
                }
            }

            R.id.menu_login -> viewModel.save(getRssSource()) {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "rssSource")
                    putExtra("key", it.sourceUrl)
                }
            }

            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_clear_cookie -> viewModel.clearCookie(getRssSource().sourceUrl)
            R.id.menu_auto_complete -> {
                viewModel.autoComplete = !viewModel.autoComplete
                autoCompleteEnabled = viewModel.autoComplete
            }
            R.id.menu_copy_source -> sendToClip(GSON.toJson(getRssSource()))
            R.id.menu_qr_code_camera -> qrCodeResult.launch()
            R.id.menu_paste_source -> viewModel.pasteSource { upSourceView(it) }
            R.id.menu_share_str -> share(GSON.toJson(getRssSource()))
            R.id.menu_share_qr -> shareWithQr(
                GSON.toJson(getRssSource()),
                getString(R.string.share_rss_source),
                ErrorCorrectionLevel.L
            )

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_help -> showHelp("rssRuleHelp")
        }
    }

    private fun onSourceSwitchChanged(key: String, value: Boolean) {
        when (key) {
            "enabled" -> sourceEnabled = value
            "singleUrl" -> singleUrl = value
            "cookie" -> cookieEnabled = value
            "preload" -> preloadEnabled = value
        }
    }

    private fun initView() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_base)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_start)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            setText(R.string.source_tab_list)
        })
        binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
            text = "WEB_VIEW"
        })
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        val createSpanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = when (adapter.getItemViewType(position)) {
                EditEntity.ViewType.checkBox -> 1 //CheckBox 占1个span
                else -> 2 //占2个span（整行）
            }
        }
        val gridLayoutManager = if (adapter.editEntityMaxLine < 999) {
            object : GridLayoutManager(this, 2) {
                init {
                    spanSizeLookup = createSpanSizeLookup
                }
                override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean) = false
                override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean) = false
            }
        } else {
            GridLayoutManager(this, 2).apply {
                spanSizeLookup = createSpanSizeLookup
            }
        }
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus is EditText) {
                newFocus.postDelayed({ sendText("") }, 120)
            }
        }
        binding.tabLayout.setBackgroundColor(backgroundColor)
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {

            }

            override fun onTabSelected(tab: TabLayout.Tab?) {
                setEditEntities(tab?.position)
            }
        })
        binding.recyclerView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val navigationBarHeight = windowInsets.navigationBarHeight
            val imeHeight = windowInsets.imeHeight
            view.bottomPadding = if (imeHeight == 0) navigationBarHeight else 0
            softKeyboardTool.initialPadding = imeHeight
            windowInsets
        }
    }

    private fun setEditEntities(tabPosition: Int?) {
        selectedEditTab = tabPosition ?: 0
        val selected = when (tabPosition) {
            1 -> startEntities
            2 -> listEntities
            3 -> webViewEntities
            else -> sourceEntities
        }
        composeEntities.clear()
        composeEntities.addAll(selected)
        window.decorView.rootView.clearFocus()
    }

    private fun upSourceView(rssSource: RssSource?) {
        val rs = rssSource ?: RssSource()
        rs.let {
            sourceEnabled = rs.enabled
            singleUrl = rs.singleUrl
            cookieEnabled = rs.enabledCookieJar == true
            preloadEnabled = rs.preload
            sourceTypeIndex = rs.type.coerceIn(0, resources.getStringArray(R.array.rss_type).lastIndex)
            articleStyleIndex = rs.articleStyle.coerceIn(0, resources.getStringArray(R.array.layout_type).lastIndex)
        }
        sourceEntities.clear()
        sourceEntities.apply {
            add(EditEntity("sourceName", rs.sourceName, R.string.source_name))
            add(EditEntity("sourceUrl", rs.sourceUrl, R.string.source_url))
            add(EditEntity("sourceIcon", rs.sourceIcon, R.string.source_icon))
            add(EditEntity("sourceGroup", rs.sourceGroup, R.string.source_group))
            add(EditEntity("sourceComment", rs.sourceComment, R.string.comment))
            add(EditEntity("searchUrl", rs.searchUrl, R.string.r_search_url))
            add(EditEntity("sortUrl", rs.sortUrl, R.string.sort_url))
            add(EditEntity("loginUrl", rs.loginUrl, R.string.login_url))
            add(EditEntity("loginUi", rs.loginUi, R.string.login_ui))
            add(EditEntity("loginCheckJs", rs.loginCheckJs, R.string.login_check_js))
            add(EditEntity("coverDecodeJs", rs.coverDecodeJs, R.string.cover_decode_js))
            add(EditEntity("header", rs.header, R.string.source_http_header))
            add(EditEntity("variableComment", rs.variableComment, R.string.variable_comment))
            add(EditEntity("concurrentRate", rs.concurrentRate, R.string.concurrent_rate))
            add(EditEntity("jsLib", rs.jsLib, "jsLib"))
        }
        startEntities.clear()
        startEntities.apply {
            add(EditEntity("startHtml", rs.startHtml, R.string.r_startHtml))
            add(EditEntity("startStyle", rs.startStyle, R.string.r_startStyle))
            add(EditEntity("startJs", rs.startJs, R.string.r_startJs))
            add(EditEntity("preloadJs", rs.preloadJs, R.string.r_preloadJs))
        }
        listEntities.clear()
        listEntities.apply {
            add(EditEntity("ruleArticles", rs.ruleArticles, R.string.r_articles))
            add(EditEntity("ruleNextPage", rs.ruleNextPage, R.string.r_next))
            add(EditEntity("ruleTitle", rs.ruleTitle, R.string.r_title))
            add(EditEntity("rulePubDate", rs.rulePubDate, R.string.r_date))
            add(EditEntity("ruleDescription", rs.ruleDescription, R.string.r_description))
            add(EditEntity("ruleImage", rs.ruleImage, R.string.r_image))
            add(EditEntity("ruleLink", rs.ruleLink, R.string.r_link))
        }
        webViewEntities.clear()
        webViewEntities.apply {
            add(
                EditEntity(
                    "enableJs",
                    rs.enableJs.toString(),
                    R.string.enable_js,
                    EditEntity.ViewType.checkBox
                )
            )
            add(
                EditEntity(
                    "loadWithBaseUrl",
                    rs.loadWithBaseUrl.toString(),
                    R.string.load_with_base_url,
                    EditEntity.ViewType.checkBox
                )
            )
            add(
                 EditEntity(
                     "showWebLog",
                     rs.showWebLog.toString(),
                     R.string.load_with_web_log,
                     EditEntity.ViewType.checkBox
                 )
             )
            add(
                EditEntity(
                    "cacheFirst",
                    rs.cacheFirst.toString(),
                    R.string.cache_first,
                    EditEntity.ViewType.checkBox
                )
            )
            add(EditEntity("ruleContent", rs.ruleContent, R.string.r_content))
            add(EditEntity("style", rs.style, R.string.r_style))
            add(EditEntity("injectJs", rs.injectJs, R.string.r_inject_js))
            add(EditEntity("contentWhitelist", rs.contentWhitelist, R.string.c_whitelist))
            add(EditEntity("contentBlacklist", rs.contentBlacklist, R.string.c_blacklist))
            add(
                EditEntity(
                    "shouldOverrideUrlLoading",
                    rs.shouldOverrideUrlLoading,
                    "url跳转拦截(js, 返回true拦截,js变量url,可以通过js打开url,比如调用阅读搜索,添加书架等,简化规则写法,不用webView js注入)"
                )
            )
        }
        setEditEntities(0)
    }

    private fun getRssSource(): RssSource {
        val source = viewModel.rssSource?.copy() ?: RssSource()
        source.enabled = sourceEnabled
        source.singleUrl = singleUrl
        source.enabledCookieJar = cookieEnabled
        source.preload = preloadEnabled
        source.type = sourceTypeIndex
        source.articleStyle = articleStyleIndex
        sourceEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "sourceName" -> source.sourceName = it.value ?: ""
                "sourceUrl" -> source.sourceUrl = it.value ?: ""
                "sourceIcon" -> source.sourceIcon = it.value ?: ""
                "sourceGroup" -> source.sourceGroup = it.value
                "sourceComment" -> source.sourceComment = it.value
                "loginUrl" -> source.loginUrl = it.value
                "loginUi" -> source.loginUi = it.value
                "loginCheckJs" -> source.loginCheckJs = it.value
                "coverDecodeJs" -> source.coverDecodeJs = it.value
                "header" -> source.header = it.value
                "variableComment" -> source.variableComment = it.value
                "concurrentRate" -> source.concurrentRate = it.value
                "searchUrl" -> source.searchUrl = it.value
                "sortUrl" -> source.sortUrl = it.value
                "jsLib" -> source.jsLib = it.value
            }
        }
        startEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "startHtml" -> source.startHtml = it.value
                "startStyle" -> source.startStyle = it.value
                "startJs" -> source.startJs = it.value
                "preloadJs" -> source.preloadJs = it.value
            }
        }
        listEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "ruleArticles" -> source.ruleArticles = it.value
                "ruleNextPage" -> source.ruleNextPage =
                    viewModel.ruleComplete(it.value, source.ruleArticles, 2)

                "ruleTitle" -> source.ruleTitle =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "rulePubDate" -> source.rulePubDate =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "ruleDescription" -> source.ruleDescription =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "ruleImage" -> source.ruleImage =
                    viewModel.ruleComplete(it.value, source.ruleArticles, 3)

                "ruleLink" -> source.ruleLink =
                    viewModel.ruleComplete(it.value, source.ruleArticles)
            }
        }
        webViewEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "enableJs" -> source.enableJs = it.value.isTrue()
                "loadWithBaseUrl" -> source.loadWithBaseUrl = it.value.isTrue()
                "showWebLog" -> source.showWebLog = it.value.isTrue()
                "cacheFirst" -> source.cacheFirst = it.value.isTrue()
                "ruleContent" -> source.ruleContent =
                    viewModel.ruleComplete(it.value, source.ruleArticles)

                "style" -> source.style = it.value
                "injectJs" -> source.injectJs = it.value
                "contentWhitelist" -> source.contentWhitelist = it.value
                "contentBlacklist" -> source.contentBlacklist = it.value
                "shouldOverrideUrlLoading" -> source.shouldOverrideUrlLoading = it.value
            }
        }
        return source
    }

    private fun setSourceVariable() {
        viewModel.save(getRssSource()) { source ->
            lifecycleScope.launch {
                val comment =
                    source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
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
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("插入URL参数", "urlOption"),
            SelectItem("订阅源教程", "ruleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp"),
            SelectItem("选择文件", "selectFile"),
        )
    }

    override fun onHelpActionSelect(action: String) {
        when (action) {
            "urlOption" -> UrlOptionDialog(this) {
                sendText(it)
            }.show()

            "ruleHelp" -> showHelp("rssRuleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
            "selectFile" -> selectDoc.launch {
                mode = HandleFileContract.FILE
            }
        }
    }

    override fun sendText(text: String) {
        val view = window.decorView.findFocus()
        if (view is EditText) {
            var start = view.selectionStart
            var end = view.selectionEnd
            if (start > end) {
                val temp = start
                start = end
                end = temp
            }
            if (text.isNotEmpty()) {
                val edit = view.editableText//获取EditText的文字
                if (start < 0 || start >= edit.length) {
                    edit.append(text)
                } else {
                    edit.replace(start, end, text)//光标所在位置插入文字
                }
            }
            if (adapter.editEntityMaxLine >= 999) {
                view.post {
                    val editTextLocation = IntArray(2)
                    view.getLocationOnScreen(editTextLocation)
                    val recyclerViewLocation = IntArray(2)
                    binding.recyclerView.getLocationOnScreen(recyclerViewLocation)
                    val layout = view.layout
                    if (layout != null) {
                        val line = layout.getLineForOffset(end)
                        val cursorYInEditText = layout.getLineTop(line)
                        // 光标相对于屏幕的位置
                        val cursorYOnScreen = editTextLocation[1] + cursorYInEditText
                        // 光标相对于RecyclerView的位置
                        val cursorYInRecyclerView = cursorYOnScreen - recyclerViewLocation[1]
                        val recyclerViewBottom = binding.recyclerView.height - 120 //考虑键盘的经验值
                        // 如果光标不在可见范围内，则滚动到光标位置
                        if (cursorYInRecyclerView !in 0..recyclerViewBottom) {
                            val scrollDistance = cursorYInRecyclerView - recyclerViewBottom / 3
                            if (scrollDistance > 0 && binding.recyclerView.canScrollVertically(1) || scrollDistance < 0 && binding.recyclerView.canScrollVertically(-1)) {
                                binding.recyclerView.smoothScrollBy(0, scrollDistance)
                            }
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onUndoClicked() {
        val editText = window.decorView.findFocus()
        if (editText is EditText) {
            editText.onTextContextMenuItem(android.R.id.undo)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRedoClicked() {
        val editText = window.decorView.findFocus()
        if (editText is EditText) {
            editText.onTextContextMenuItem(android.R.id.redo)
        }
    }

}
