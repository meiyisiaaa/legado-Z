package io.legado.app.ui.code

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityCodeEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.code.config.ChangeThemeDialog
import io.legado.app.ui.code.config.SettingsDialog
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.utils.imeHeight
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CodeEditActivity :
    VMBaseActivity<ActivityCodeEditBinding, CodeEditViewModel>(),
    KeyboardToolPop.CallBack, ChangeThemeDialog.CallBack, SettingsDialog.CallBack {
    companion object {
        private var isInitialized = false
        private var findText = ""
        private var replaceText = ""
        private var isRegex = true
        internal const val SAFE_EDITOR_TAG = "safe_code_editor"
        private const val SAFE_BRIDGE_NAME = "EditorBridge"
        private const val SAFE_STATE_TIMEOUT_MS = 5_000L
    }
    override val binding by viewBinding(ActivityCodeEditBinding::inflate)
    override val viewModel by viewModels<CodeEditViewModel>()
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }
    private val editor: CodeEditor by lazy { binding.editText }
    private val editorSearcher: EditorSearcher by lazy { editor.searcher }
    private var searchOptions: SearchOptions? = null
    private var menuSaveBtn: MenuItem? = null
    private var optionsMenu: Menu? = null
    private var safeWebView: WebView? = null
    private var safePageReady = false
    private var safeEditorDestroyed = false
    private var saveInProgress = false
    private val safeStateHandler = Handler(Looper.getMainLooper())
    private val initDataCallback: () -> Unit = {
        viewModel.title?.let { binding.titleBar.title = it }
        menuSaveBtn?.isVisible = viewModel.writable
        if (viewModel.useSafeEditor) {
            setupSafeEditor()
        } else {
            editor.colorScheme = TextMateColorScheme2.create(ThemeRegistry.getInstance()) //先设置颜色,避免一开始的白屏
            editor.apply {
                nonPrintablePaintingFlags = AppConfig.editNonPrintable
                setEditorLanguage(viewModel.language)
                upEdit(AppConfig.editFontScale, null, AppConfig.editAutoWrap)
                setText(viewModel.editorDraft ?: viewModel.initialText)
                editable = viewModel.writable
                requestFocus()
                postDelayed({
                    val cursorPosition = if (viewModel.editorDraft == null) {
                        viewModel.cursorPosition
                    } else {
                        viewModel.editorDraftCursorPosition
                    }
                    val pos = cursor.indexer.getCharPosition(cursorPosition)
                    setSelection(pos.line, pos.column, true)
                }, 360) // 进行延时,确保加载渲染完成,从而确保光标能显示跳转到长文本最后
            }
        }
    }
    private val initRestoreFailureCallback: () -> Unit = {
        runOnUiThread { finishAfterRestoreFailure() }
    }

    private val isDark
        get() = AppConfig.editTemeAuto && ThemeConfig.isDarkTheme()
    private var themeIndex = -1

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        viewModel.initData(intent, savedInstanceState, initDataCallback, initRestoreFailureCallback)
        initView()
    }

    private fun finishAfterRestoreFailure() {
        if (isFinishing) return
        setResult(RESULT_CANCELED)
        super.finish()
    }

    private fun setupSafeEditor() {
        binding.editText.isGone = true
        binding.searchGroup.isGone = true
        updateSafeMenu()
        safeEditorDestroyed = false
        val webView = WebView(this).apply {
            tag = SAFE_EDITOR_TAG
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                blockNetworkLoads = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            addJavascriptInterface(SafeEditorBridge(), SAFE_BRIDGE_NAME)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse = blockedResponse()

                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse = blockedResponse()

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == "about:blank") {
                        safePageReady = true
                        view.requestFocus()
                    }
                }
            }
        }
        safeWebView = webView
        binding.editorContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val draft = viewModel.safeDraft ?: viewModel.initialText
        val cursor = if (viewModel.safeDraft == null) viewModel.cursorPosition else viewModel.safeCursorPosition
        webView.loadDataWithBaseURL(
            "about:blank",
            safeEditorHtml(draft, cursor, !viewModel.writable),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun updateSafeMenu() {
        val menu = optionsMenu ?: return
        listOf(
            R.id.menu_search,
            R.id.menu_format_code,
            R.id.menu_change_theme,
            R.id.menu_config_settings,
            R.id.menu_auto_wrap,
            R.id.menu_log
        ).forEach { menu.findItem(it)?.isVisible = false }
    }

    private fun safeEditorHtml(text: String, cursor: Int, readOnly: Boolean): String {
        val encodedText = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val editorFontSizePx = safeEditorFontSizeCssPx(AppConfig.editFontScale, resources.configuration.fontScale)
        val darkStyle = if (isDark) {
            "--editor-bg:#15171a;--editor-fg:#e6e8eb;--editor-focus:#82aaff;"
        } else {
            "--editor-bg:#ffffff;--editor-fg:#202124;--editor-focus:#365fce;"
        }
        return """<!doctype html>
            <html lang="zh-CN"><head><meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'none'; form-action 'none'; base-uri 'none';">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                :root{$darkStyle}
                *{box-sizing:border-box}
                html,body{width:100%;height:100%;margin:0;background:var(--editor-bg);color:var(--editor-fg)}
                body{padding:12px}
                textarea{display:block;width:100%;height:100%;resize:none;border:0;outline:0;padding:8px;
                    background:transparent;color:inherit;font:${editorFontSizePx}px/1.55 monospace;tab-size:4;white-space:pre-wrap;
                    overflow-wrap:anywhere;caret-color:var(--editor-focus);-webkit-user-select:text;user-select:text}
                textarea:focus-visible{outline:2px solid var(--editor-focus);outline-offset:-2px}
            </style></head><body>
            <textarea id="editor" aria-label="代码文本编辑器" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" ${if (readOnly) "readonly" else ""}></textarea>
            <script>
                const editor=document.getElementById('editor');
                function decodeText(value){
                    const bytes=Uint8Array.from(atob(value),c=>c.charCodeAt(0));
                    if(window.TextDecoder)return new TextDecoder('utf-8').decode(bytes);
                    let binary='';for(let i=0;i<bytes.length;i+=0x8000)binary+=String.fromCharCode.apply(null,bytes.subarray(i,i+0x8000));
                    return decodeURIComponent(escape(binary));
                }
                editor.value=decodeText('$encodedText');
                const initialCursor=Math.max(0,Math.min(editor.value.length,$cursor));
                editor.setSelectionRange(initialCursor,initialCursor);
                let snapshotTimer;
                function reportCursor(){try{window.$SAFE_BRIDGE_NAME.onCursor(editor.selectionStart)}catch(e){}}
                function scheduleSnapshot(){clearTimeout(snapshotTimer);snapshotTimer=setTimeout(()=>{
                    try{window.$SAFE_BRIDGE_NAME.onSnapshot(editor.value,editor.selectionStart)}catch(e){}
                },350)}
                editor.addEventListener('input',scheduleSnapshot);
                document.addEventListener('selectionchange',reportCursor);
                window.readEditorState=function(){return JSON.stringify({text:editor.value,cursor:editor.selectionStart})};
                window.insertEditorText=function(text){
                    const start=editor.selectionStart,end=editor.selectionEnd;
                    editor.setRangeText(text,start,end,'end');editor.focus();scheduleSnapshot();reportCursor();
                };
                scheduleSnapshot();reportCursor();
            </script></body></html>"""
    }

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        "UTF-8",
        403,
        "Blocked",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

    private inner class SafeEditorBridge {
        @JavascriptInterface
        fun onSnapshot(text: String, cursorPosition: Int) {
            if (safeEditorDestroyed) return
            runOnUiThread {
                if (!safeEditorDestroyed) viewModel.updateSafeDraft(text, cursorPosition)
            }
        }

        @JavascriptInterface
        fun onCursor(cursorPosition: Int) {
            if (safeEditorDestroyed) return
            runOnUiThread {
                if (!safeEditorDestroyed) viewModel.updateSafeCursor(cursorPosition)
            }
        }
    }

    private data class SafeEditorState(val text: String, val cursor: Int)

    private fun readSafeEditorState(onSuccess: (SafeEditorState?) -> Unit) {
        val webView = safeWebView
        if (webView == null || !safePageReady) {
            onSuccess(viewModel.safeDraft?.let { SafeEditorState(it, viewModel.safeCursorPosition) })
            return
        }
        val settled = AtomicBoolean(false)
        val timeout = Runnable {
            if (settled.compareAndSet(false, true)) onSuccess(null)
        }
        safeStateHandler.postDelayed(timeout, SAFE_STATE_TIMEOUT_MS)
        webView.evaluateJavascript("window.readEditorState()") { result ->
            if (!settled.compareAndSet(false, true)) return@evaluateJavascript
            safeStateHandler.removeCallbacks(timeout)
            if (safeEditorDestroyed || isFinishing || isDestroyed) return@evaluateJavascript
            try {
                val json = JSONTokener(result).nextValue() as? String
                val value = json?.let(::JSONObject)
                val text = value?.getString("text")
                val cursor = value?.getInt("cursor")
                if (text != null && cursor != null) {
                    viewModel.updateSafeDraft(text, cursor)
                    onSuccess(SafeEditorState(text, cursor))
                } else {
                    onSuccess(null)
                }
            } catch (_: Exception) {
                onSuccess(null)
            }
        }
    }

    private fun snapshotSafeEditor() {
        val webView = safeWebView ?: return
        if (!safePageReady) return
        webView.evaluateJavascript("window.readEditorState()") { result ->
            val state = parseSafeEditorState(result) ?: return@evaluateJavascript
            viewModel.updateSafeDraft(state.text, state.cursor)
        }
    }

    private fun parseSafeEditorState(result: String): SafeEditorState? {
        return try {
            val json = JSONTokener(result).nextValue() as? String ?: return null
            val value = JSONObject(json)
            SafeEditorState(value.getString("text"), value.getInt("cursor"))
        } catch (_: Exception) {
            null
        }
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            softKeyboardTool.initialPadding = windowInsets.imeHeight
            windowInsets
        }
    }

    override fun onDestroy() {
        viewModel.removeInitCallback(initDataCallback, initRestoreFailureCallback)
        safeEditorDestroyed = true
        safePageReady = false
        safeStateHandler.removeCallbacksAndMessages(null)
        safeWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.apply {
                removeJavascriptInterface(SAFE_BRIDGE_NAME)
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                webChromeClient = null
                webViewClient = WebViewClient()
                removeAllViews()
                destroy()
            }
        }
        safeWebView = null
        optionsMenu = null
        super.onDestroy()
        editorSearcher.stopSearch()
        editor.release()
    }

    override fun onPause() {
        snapshotSafeEditor()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (viewModel.dataReady) {
            val text: String
            val cursor: Int
            if (viewModel.useSafeEditor) {
                snapshotSafeEditor()
                text = viewModel.safeDraft ?: viewModel.initialText
                cursor = viewModel.safeCursorPosition
            } else {
                text = editor.text.toString()
                cursor = editor.cursor?.left ?: 0
                viewModel.updateEditorDraft(text, cursor)
            }
            viewModel.saveProcessState(outState, text, cursor)
        } else {
            viewModel.preservePendingProcessState(outState)
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * 使用super.finish(),防止循环回调
     * */
    private fun save(check: Boolean) {
        if (!viewModel.dataReady) {
            if (check) super.finish() else toastOnUi(R.string.error_read_file)
            return
        }
        if (viewModel.useSafeEditor) {
            saveSafe(check)
            return
        }
        if (!viewModel.writable) return super.finish()
        val text = editor.text.toString()
        val cursorPos = editor.cursor?.left ?: 0
        when {
            text == viewModel.initialText -> {
                if (cursorPos > 0) {
                    val result = Intent().apply {
                        putExtra("cursorPosition", cursorPos)
                    }
                    setResult(RESULT_OK, result)
                }
                super.finish()
            }
            check -> {
                alert(R.string.exit) {
                    setMessage(R.string.exit_no_save)
                    positiveButton(R.string.yes)
                    negativeButton(R.string.no) {
                        if (cursorPos > 0) {
                            val result = Intent().apply {
                                putExtra("cursorPosition", cursorPos)
                            }
                            setResult(RESULT_OK, result)
                        }
                        super.finish()
                    }
                }
            }
            else -> {
                finishWithTextResult(text, cursorPos)
            }
        }
    }

    private fun saveSafe(check: Boolean) {
        if (!viewModel.writable) return super.finish()
        readSafeEditorState { state ->
            if (safeEditorDestroyed || isFinishing || isDestroyed) return@readSafeEditorState
            if (state == null) {
                toastOnUi(R.string.error_read_file)
                return@readSafeEditorState
            }
            when {
                state.text == viewModel.initialText -> {
                    if (state.cursor > 0) {
                        setResult(RESULT_OK, CodeTextTransfer.createResult(this@CodeEditActivity, null, state.cursor))
                    }
                    super.finish()
                }
                check -> {
                    alert(R.string.exit) {
                        setMessage(R.string.exit_no_save)
                        positiveButton(R.string.yes)
                        negativeButton(R.string.no) {
                            setResult(RESULT_OK, CodeTextTransfer.createResult(this@CodeEditActivity, null, state.cursor))
                            super.finish()
                        }
                    }
                }
                else -> {
                    finishWithTextResult(state.text, state.cursor)
                }
            }
        }
    }

    private fun finishWithTextResult(text: String, cursorPosition: Int) {
        if (saveInProgress) return
        saveInProgress = true
        lifecycleScope.launch {
            try {
                val result = withContext(IO) {
                    viewModel.createResult(text, cursorPosition)
                }
                setResult(RESULT_OK, result)
                viewModel.releaseOutput(result)
                viewModel.releaseProcessState()
                super.finish()
            } catch (cancelled: CancellationException) {
                saveInProgress = false
                throw cancelled
            } catch (error: Exception) {
                saveInProgress = false
                toastOnUi(error.localizedMessage ?: getString(R.string.error_read_file))
            }
        }
    }

    override fun upEdit(fontSize: Int?, autoComplete: Boolean?, autoWarp: Boolean?, editNonPrintable: Int?) {
        if (viewModel.useSafeEditor) return
        if (fontSize != null) {
            editor.setTextSize(fontSize.toFloat())
        }
        if (autoComplete != null) {
            viewModel.language?.isAutoCompleteEnabled = autoComplete
            editor.setEditorLanguage(viewModel.language)
        }
        if (autoWarp != null) {
            editor.isWordwrap = autoWarp
        }
        if (editNonPrintable != null) {
            editor.nonPrintablePaintingFlags = editNonPrintable
        }
    }

    override fun initTheme() {
        super.initTheme()
        if (!isInitialized) {
            viewModel.initSora()
            isInitialized = true
        }
        val index = if (isDark) {
            AppConfig.editThemeDark
        } else {
            AppConfig.editTheme
        }
        upTheme(index)
        themeIndex = index
    }

    override fun upTheme(index: Int) {
        if (viewModel.useSafeEditor) return
        if (themeIndex != index) {
            viewModel.loadTextMateThemes(index)
            editor.setEditorLanguage(viewModel.language) //每次更改颜色后需要再执行一次语言设置,防止切换主题后高亮颜色不正确
            themeIndex = index
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.code_edit_activity, menu)
        optionsMenu = menu
        menuSaveBtn = menu.findItem(R.id.menu_save).apply {
            isVisible = viewModel.writable
        }
        if (viewModel.useSafeEditor) updateSafeMenu()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_auto_wrap)?.isChecked = AppConfig.editAutoWrap
        return super.onPrepareOptionsMenu(menu)
    }

    private fun setSearchOptions() {
        searchOptions =  SearchOptions(
            if (isRegex) SearchOptions.TYPE_REGULAR_EXPRESSION else SearchOptions.TYPE_NORMAL,
            !isRegex,
            RegexBackrefGrammar.DEFAULT
        )
    }

    private fun search() {
        if (binding.searchGroup.isVisible) return
        binding.switchRegex.run {
            isChecked = isRegex
            setSearchOptions()
            setOnCheckedChangeListener { _, isChecked ->
                isRegex = isChecked
                setSearchOptions()
                searchTxt(binding.etFind.text.toString())
            }
        }
        val receiptSearch =
            editor.subscribeEvent(PublishSearchResultEvent::class.java) { event, _ ->
                if (event.editor == editor) {
                    updateSearchResults()
                }
            }
        val receiptChange = editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (event.cause == SelectionChangeEvent.CAUSE_SEARCH) {
                updateSearchResults()
            }
        }
        binding.searchGroup.visibility = View.VISIBLE
        binding.btnCloseFind.setOnClickListener {
            binding.searchGroup.visibility = View.GONE
            editorSearcher.stopSearch()
            receiptSearch.unsubscribe()
            receiptChange.unsubscribe()
            editor.requestFocus()
            editor.invalidate()
        }
        searchTxt(findText)
        binding.etFind.run {
            requestFocus()
            setText(findText)
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    findText = text.toString()
                    searchTxt(findText)
                } else {
                    editorSearcher.stopSearch()
                    editor.invalidate()
                }
            }

        }
        binding.etReplace.run {
            setText(replaceText)
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    replaceText = text.toString()
                }
            }
        }
        binding.btnPrevious.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.gotoPrevious()
            }
        }
        binding.btnNext.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.gotoNext()
            }
        }
        binding.btnReplace.setOnClickListener {
            if (binding.replaceGroup.isGone) {
                binding.replaceGroup.visibility = View.VISIBLE
                binding.btnReplaceAll.isEnabled = true
                binding.etReplace.requestFocus()
            } else {
                if (editorSearcher.hasQuery()) {
                    editorSearcher.replaceCurrentMatch(binding.etReplace.text.toString())
                }
            }
        }
        binding.btnCloseReplace.setOnClickListener {
            binding.replaceGroup.visibility = View.GONE
            binding.btnReplaceAll.isEnabled = false
            binding.etFind.requestFocus()
        }
        binding.btnReplaceAll.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.replaceAll(binding.etReplace.text.toString())
            }
        }
    }

    private fun searchTxt(txt: String) {
        if (txt.isNotEmpty()) {
            try {
                searchOptions?.let {
                    editorSearcher.search(txt, it)
                }
            } catch (_: java.util.regex.PatternSyntaxException) {
                // 忽略正则表达式语法错误
                editorSearcher.stopSearch()
                editor.invalidate()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateSearchResults() {
        if (editorSearcher.hasQuery()) {
            val totalResults = editorSearcher.matchedPositionCount
            val currentPosition = editorSearcher.currentMatchedPositionIndex + 1
            binding.tvSearchResult.text =
                "${if (currentPosition > 0) "$currentPosition/" else ""}$totalResults"
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_search -> if (!viewModel.useSafeEditor) search()
            R.id.menu_save -> save(false)
            R.id.menu_format_code -> if (!viewModel.useSafeEditor) viewModel.formatCode(editor)
            R.id.menu_change_theme -> if (!viewModel.useSafeEditor) showDialogFragment(ChangeThemeDialog())
            R.id.menu_config_settings -> if (!viewModel.useSafeEditor) showDialogFragment(SettingsDialog(this, this))
            R.id.menu_auto_wrap -> if (!viewModel.useSafeEditor) {
                item.isChecked = !AppConfig.editAutoWrap
                upEdit(autoWarp = !AppConfig.editAutoWrap)
                putPrefBoolean(PreferKey.editAutoWrap, !AppConfig.editAutoWrap)
            }
            R.id.menu_log -> if (!viewModel.useSafeEditor) showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun finish() {
        save(true)
    }

    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("书源教程", "ruleHelp"),
            SelectItem("订阅源教程", "rssRuleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp")
        )
    }

    override fun onHelpActionSelect(action: String) {
        when (action) {
            "ruleHelp" -> showHelp("ruleHelp")
            "rssRuleHelp" -> showHelp("rssRuleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
        }
    }

    override fun sendText(text: String) {
        if (viewModel.useSafeEditor) {
            if (text.isNotEmpty() && safePageReady) {
                val encoded = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                val js = "window.insertEditorText(decodeURIComponent(Array.prototype.map.call(atob('$encoded'),c=>'%'+('0'+c.charCodeAt(0).toString(16)).slice(-2)).join('')))"
                safeWebView?.evaluateJavascript(js, null)
            }
            return
        }
        val view = window.decorView.findFocus()
        if (view is TextInputEditText) {
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
        }
        else {
            editor.insertText(text, text.length)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onUndoClicked() {
        if (!viewModel.useSafeEditor) editor.undo()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRedoClicked() {
        if (!viewModel.useSafeEditor) editor.redo()
    }

}

internal fun safeEditorFontSizeCssPx(fontSize: Int, fontScale: Float): Float = fontSize * fontScale
