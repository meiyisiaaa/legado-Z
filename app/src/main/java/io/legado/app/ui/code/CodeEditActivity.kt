package io.legado.app.ui.code

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewStructure
import android.view.accessibility.AccessibilityEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.autofill.AutofillValue
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
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
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.code.config.ChangeThemeDialog
import io.legado.app.ui.code.config.SettingsDialog
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.utils.imeHeight
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
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
    }
    override val binding by viewBinding(ActivityCodeEditBinding::inflate)
    override val viewModel by viewModels<CodeEditViewModel>()
    private val titleBar: TitleBar by lazy {
        TitleBar(this).apply {
            id = R.id.title_bar
            title = getString(R.string.edit_code)
            if (!AppConfig.isEInkMode) setBackgroundColor(context.primaryColor)
        }
    }
    private val editorContainer: FrameLayout by lazy {
        FrameLayout(this).apply {
            addView(
                editor,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }
    private val editor: CodeEditor by lazy {
        CodeEditor(this).apply {
            id = R.id.editText
            setTextSize(18f)
        }
    }
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }
    private val editorSearcher: EditorSearcher by lazy { editor.searcher }
    private var searchOptions: SearchOptions? = null
    private var menuSaveBtn: MenuItem? = null
    private var optionsMenu: Menu? = null
    private var safeEditText: AppCompatEditText? = null
    private var saveInProgress = false
    private var searchPanelVisible by mutableStateOf(false)
    private var replacePanelVisible by mutableStateOf(false)
    private var searchQuery by mutableStateOf(findText)
    private var replacementQuery by mutableStateOf(replaceText)
    private var searchRegex by mutableStateOf(isRegex)
    private var searchSummary by mutableStateOf("0")
    private var replaceAllEnabled by mutableStateOf(false)
    private var closeSearchAction: (() -> Unit)? = null
    private val initDataCallback: () -> Unit = {
        viewModel.title?.let { titleBar.title = it }
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
        initSearchPanel()
    }

    private fun initSearchPanel() {
        binding.root.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.root.setContent {
            LegadoTheme {
                // adjustResize and KeyboardToolPop already reserve the IME and helper space.
                Column(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { titleBar }
                    )
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        factory = { editorContainer }
                    )
                    CodeSearchPanel(
                        visible = searchPanelVisible,
                        replaceVisible = replacePanelVisible,
                        query = searchQuery,
                        replacement = replacementQuery,
                        isRegex = searchRegex,
                        resultSummary = searchSummary,
                        replaceAllEnabled = replaceAllEnabled,
                        onQueryChange = { query ->
                            searchQuery = query
                            if (query.isNotEmpty()) findText = query
                            if (query.isNotEmpty()) searchTxt(query) else {
                                editorSearcher.stopSearch()
                                editor.invalidate()
                            }
                        },
                        onReplacementChange = { replacement ->
                            replacementQuery = replacement
                            if (replacement.isNotEmpty()) replaceText = replacement
                        },
                        onRegexChange = { checked ->
                            searchRegex = checked
                            isRegex = checked
                            setSearchOptions()
                            if (searchQuery.isNotEmpty()) searchTxt(searchQuery)
                        },
                        onClose = { closeSearchAction?.invoke() },
                        onPrevious = {
                            if (editorSearcher.hasQuery()) editorSearcher.gotoPrevious()
                        },
                        onNext = {
                            if (editorSearcher.hasQuery()) editorSearcher.gotoNext()
                        },
                        onToggleReplace = {
                            if (replacePanelVisible) {
                                if (editorSearcher.hasQuery()) {
                                    editorSearcher.replaceCurrentMatch(replacementQuery)
                                }
                            } else {
                                replacePanelVisible = true
                                replaceAllEnabled = true
                            }
                        },
                        onCloseReplace = {
                            replacePanelVisible = false
                            replaceAllEnabled = false
                        },
                        onReplaceAll = {
                            if (editorSearcher.hasQuery()) editorSearcher.replaceAll(replacementQuery)
                        }
                    )
                }
            }
        }
    }

    private fun finishAfterRestoreFailure() {
        if (isFinishing) return
        setResult(RESULT_CANCELED)
        super.finish()
    }

    private fun setupSafeEditor() {
        editor.isGone = true
        searchPanelVisible = false
        replacePanelVisible = false
        updateSafeMenu()
        val draft = viewModel.safeDraft ?: viewModel.initialText
        val cursor = if (viewModel.safeDraft == null) viewModel.cursorPosition else viewModel.safeCursorPosition
        val horizontalScrolling = !AppConfig.editAutoWrap
        val safeEditor = object : AppCompatEditText(this) {
            override fun sendAccessibilityEventUnchecked(event: AccessibilityEvent) {
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                    // Match Android's current-text parcel limit for the otherwise unbounded beforeText.
                    event.beforeText = event.beforeText?.let(EditSafety::accessibilityBeforeText)
                }
                super.sendAccessibilityEventUnchecked(event)
            }

            override fun getAutofillType() = View.AUTOFILL_TYPE_NONE

            // Autofill can request values even for views marked not important.
            override fun getAutofillValue(): AutofillValue? = null

            override fun onProvideAutofillStructure(structure: ViewStructure, flags: Int) {
                super.onProvideAutofillStructure(structure, flags)
                structure.setText("")
            }
        }.apply {
            tag = SAFE_EDITOR_TAG
            id = View.generateViewId()
            hint = getString(R.string.edit_code)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, AppConfig.editFontScale.toFloat())
            typeface = Typeface.MONOSPACE
            setTextColor(if (isDark) Color.rgb(230, 232, 235) else Color.rgb(32, 33, 36))
            setBackgroundColor(if (isDark) Color.rgb(21, 23, 26) else Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setSingleLine(false)
            setHorizontallyScrolling(horizontalScrolling)
            isHorizontalScrollBarEnabled = horizontalScrolling
            isVerticalScrollBarEnabled = true
            gravity = Gravity.TOP or Gravity.START
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setLineSpacing(0f, 1.55f)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            setTextIsSelectable(!viewModel.writable)
            isCursorVisible = viewModel.writable
            if (!viewModel.writable) keyListener = null
            setText(draft)
            setSelection(cursor.coerceIn(0, draft.length))
        }
        editorContainer.addView(
            safeEditor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        safeEditText = safeEditor
        safeEditor.requestFocus()
        viewModel.updateSafeDraft(draft, safeEditor.selectionStart.takeIf { it >= 0 } ?: cursor)
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

    private fun snapshotSafeEditor() {
        val editor = safeEditText ?: return
        val text = editor.editableText.toString()
        val cursor = editor.selectionStart.takeIf { it >= 0 } ?: text.length
        viewModel.updateSafeDraft(text, cursor)
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            softKeyboardTool.initialPadding = windowInsets.imeHeight
            windowInsets
        }
    }

    override fun onDestroy() {
        viewModel.removeInitCallback(initDataCallback, initRestoreFailureCallback)
        safeEditText = null
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
        snapshotSafeEditor()
        if (!viewModel.writable) return super.finish()
        val text = viewModel.safeDraft ?: viewModel.initialText
        val cursor = viewModel.safeCursorPosition
        when {
            text == viewModel.initialText -> {
                if (cursor > 0) {
                    setResult(RESULT_OK, CodeTextTransfer.createResult(this@CodeEditActivity, null, cursor))
                }
                super.finish()
            }
            check -> {
                alert(R.string.exit) {
                    setMessage(R.string.exit_no_save)
                    positiveButton(R.string.yes)
                    negativeButton(R.string.no) {
                        setResult(RESULT_OK, CodeTextTransfer.createResult(this@CodeEditActivity, null, cursor))
                        super.finish()
                    }
                }
            }
            else -> finishWithTextResult(text, cursor)
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
        if (searchPanelVisible) return
        searchQuery = findText
        replacementQuery = replaceText
        searchRegex = isRegex
        searchSummary = "0"
        searchPanelVisible = true
        setSearchOptions()
        val receiptSearch =
            editor.subscribeEvent(PublishSearchResultEvent::class.java) { event, _ ->
                if (event.editor == editor) {
                    runOnUiThread { updateSearchResults() }
                }
            }
        val receiptChange = editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (event.cause == SelectionChangeEvent.CAUSE_SEARCH) {
                runOnUiThread { updateSearchResults() }
            }
        }
        closeSearchAction = {
            searchPanelVisible = false
            replacePanelVisible = false
            replaceAllEnabled = false
            editorSearcher.stopSearch()
            receiptSearch.unsubscribe()
            receiptChange.unsubscribe()
            editor.requestFocus()
            editor.invalidate()
        }
        searchTxt(findText)
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
            searchSummary = "${if (currentPosition > 0) "$currentPosition/" else ""}$totalResults"
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
            val target = safeEditText ?: return
            if (!viewModel.writable || text.isEmpty()) return
            val editable = target.editableText
            val length = editable.length
            val selectionStart = target.selectionStart.takeIf { it >= 0 } ?: length
            val selectionEnd = target.selectionEnd.takeIf { it >= 0 } ?: selectionStart
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, length)
            val end = maxOf(selectionStart, selectionEnd).coerceIn(start, length)
            editable.replace(start, end, text)
            target.setSelection(start + text.length)
            snapshotSafeEditor()
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
        if (viewModel.useSafeEditor) {
            if (viewModel.writable) safeEditText?.onTextContextMenuItem(android.R.id.undo)
        } else {
            editor.undo()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRedoClicked() {
        if (viewModel.useSafeEditor) {
            if (viewModel.writable) safeEditText?.onTextContextMenuItem(android.R.id.redo)
        } else {
            editor.redo()
        }
    }

}
