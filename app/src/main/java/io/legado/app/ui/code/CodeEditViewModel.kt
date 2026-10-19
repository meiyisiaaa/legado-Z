package io.legado.app.ui.code

import android.app.Application
import android.content.Intent
import android.os.Bundle
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.BackstageWebView
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import org.eclipse.tm4e.core.registry.IThemeSource
import org.jsoup.Jsoup
import splitties.init.appCtx

class CodeEditViewModel(application: Application) : BaseViewModel(application) {
    private val themeFileNames = arrayOf(
        "d_monokai_dimmed",
        "d_monokai",
        "d_modern",
        "l_modern",
        "d_solarized",
        "l_solarized",
        "d_abyss",
        "l_quiet"
    )

    var initialText = ""
    var cursorPosition = 0
    var language: TextMateLanguage? = null
    private var languageName = "source.js"
    private val themeRegistry: ThemeRegistry = ThemeRegistry.getInstance()
    var writable = true
    var title: String? = null
    var useSafeEditor = false
        internal set
    var safeDraft: String? = null
        private set
    var safeCursorPosition = 0
        private set
    var editorDraft: String? = null
        private set
    var editorDraftCursorPosition = 0
        private set
    private var initStarted = false
    var dataReady = false
        private set
    private var ownedInputToken: String? = null
    private val ownedOutputTokens = mutableSetOf<String>()
    private var outputOwnerCleared = false
    private var processDraftToken: String? = null
    private var pendingProcessDraft: ProcessDraft? = null
    private var processDraftUnavailable = false
    private var initCallback: (() -> Unit)? = null
    private var restoreFailureCallback: (() -> Unit)? = null

    private data class ProcessDraft(val token: String, val cursorPosition: Int)

    companion object {
        internal const val STATE_PROCESS_DRAFT_TOKEN = "codeEditProcessDraftToken"
        internal const val STATE_PROCESS_DRAFT_CURSOR = "codeEditProcessDraftCursor"
        private const val STATE_PROCESS_DRAFT_REQUIRED = "codeEditProcessDraftRequired"
    }

    fun initSora() {
        //初始化sora加载
        FileProviderRegistry.getInstance().addFileProvider(
            AssetsFileResolver(appCtx.assets)
        )
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    fun initData(
        intent: Intent,
        savedInstanceState: Bundle?,
        success: () -> Unit,
        onRestoreFailure: () -> Unit
    ) {
        if (dataReady) {
            success()
            return
        }
        prepareProcessRestore(savedInstanceState)
        initCallback = success
        restoreFailureCallback = onRestoreFailure
        if (initStarted) return
        initStarted = true
        val cacheKey = intent.getStringExtra("cacheKey")
        if (cacheKey == null && intent.getBooleanExtra(CodeTextTransfer.EXTRA_USE_TEXT_FILE, false)) {
            ownedInputToken = intent.getStringExtra(CodeTextTransfer.EXTRA_TEXT_FILE_TOKEN)
        }
        execute {
            if (processDraftUnavailable) {
                throw IllegalStateException("Unable to restore the editor draft")
            }
            var restoredDraftText: String? = null
            val pendingDraft = pendingProcessDraft
            if (pendingDraft != null) {
                restoredDraftText = CodeTextTransfer.read(context, pendingDraft.token)
            }
            if (cacheKey != null) {
                val cacheText = CacheManager.getFromMemory(cacheKey) as? String
                writable = false
                initialText = cacheText ?: restoredDraftText ?: throw Exception("未获取到查看文本")
            } else {
                writable = !intent.getBooleanExtra(CodeTextTransfer.EXTRA_READ_ONLY, false)
                val incoming = CodeTextTransfer.readInput(context, intent)
                ownedInputToken = incoming.ownedToken
                initialText = incoming.text
            }
            useSafeEditor = EditSafety.requiresSafeEditor(initialText) ||
                (restoredDraftText?.let { EditSafety.requiresSafeEditor(it) } == true)
            restoredDraftText?.let(::applyProcessDraft)
            if (isHtmlStr(initialText)) {
                languageName = "text.html.basic"
            } else {
                intent.getStringExtra("languageName")?.let { languageName = it }
            }
            language = TextMateLanguage.create(languageName, AppConfig.editAutoComplete)
            cursorPosition = intent.getIntExtra("cursorPosition", 0)
            title = intent.getStringExtra("title")
        }.onSuccess {
            dataReady = true
            initCallback?.invoke()
            initCallback = null
            restoreFailureCallback = null
        }.onError {
            val failedRestore = pendingProcessDraft != null || processDraftUnavailable
            if (failedRestore) {
                processDraftUnavailable = true
            }
            ownedInputToken?.let { runCatching { CodeTextTransfer.delete(context, it) } }
            ownedInputToken = null
            context.toastOnUi("error\n${it.localizedMessage}")
            it.printOnDebug()
            initCallback = null
            val failureCallback = if (failedRestore) restoreFailureCallback else null
            restoreFailureCallback = null
            failureCallback?.invoke()
        }
    }

    fun removeInitCallback(callback: () -> Unit, restoreFailure: () -> Unit) {
        if (initCallback === callback) initCallback = null
        if (restoreFailureCallback === restoreFailure) restoreFailureCallback = null
    }

    fun updateSafeDraft(text: String, cursorPosition: Int) {
        safeDraft = text
        safeCursorPosition = cursorPosition.coerceIn(0, text.length)
    }

    fun updateEditorDraft(text: String, cursorPosition: Int) {
        editorDraft = text
        editorDraftCursorPosition = cursorPosition.coerceIn(0, text.length)
    }

    fun updateSafeCursor(cursorPosition: Int) {
        safeCursorPosition = cursorPosition.coerceAtLeast(0)
    }

    internal fun saveProcessState(outState: Bundle, text: String, cursorPosition: Int) {
        val previousToken = processDraftToken
        try {
            val token = previousToken?.also {
                CodeTextTransfer.writeReplacing(context, it, text)
            } ?: CodeTextTransfer.write(context, text).also { processDraftToken = it }
            outState.putBoolean(STATE_PROCESS_DRAFT_REQUIRED, true)
            outState.putString(STATE_PROCESS_DRAFT_TOKEN, token)
            outState.putInt(STATE_PROCESS_DRAFT_CURSOR, cursorPosition.coerceIn(0, text.length))
        } catch (error: Exception) {
            outState.putBoolean(STATE_PROCESS_DRAFT_REQUIRED, true)
            if (previousToken == null) {
                processDraftToken?.let { runCatching { CodeTextTransfer.delete(context, it) } }
                processDraftToken = null
            } else {
                outState.putString(STATE_PROCESS_DRAFT_TOKEN, previousToken)
                outState.putInt(STATE_PROCESS_DRAFT_CURSOR, cursorPosition.coerceAtLeast(0))
            }
            context.toastOnUi(error.localizedMessage ?: "Unable to preserve editor draft")
        }
    }

    internal fun prepareProcessRestore(savedInstanceState: Bundle?) {
        val savedState = savedInstanceState ?: return
        if (!savedState.getBoolean(STATE_PROCESS_DRAFT_REQUIRED, false)) return
        val token = savedState.getString(STATE_PROCESS_DRAFT_TOKEN)
        if (token == null) {
            processDraftUnavailable = true
            return
        }
        processDraftToken = token
        pendingProcessDraft = ProcessDraft(
            token = token,
            cursorPosition = savedState.getInt(STATE_PROCESS_DRAFT_CURSOR, 0)
        )
    }

    internal fun preservePendingProcessState(outState: Bundle) {
        val pending = pendingProcessDraft
        if (pending != null) {
            outState.putBoolean(STATE_PROCESS_DRAFT_REQUIRED, true)
            outState.putString(STATE_PROCESS_DRAFT_TOKEN, pending.token)
            outState.putInt(STATE_PROCESS_DRAFT_CURSOR, pending.cursorPosition)
        } else if (processDraftUnavailable) {
            outState.putBoolean(STATE_PROCESS_DRAFT_REQUIRED, true)
        }
    }

    internal fun restorePendingProcessDraft() {
        val pending = pendingProcessDraft ?: return
        val text = CodeTextTransfer.read(context, pending.token)
        useSafeEditor = EditSafety.requiresSafeEditor(initialText) || EditSafety.requiresSafeEditor(text)
        applyProcessDraft(text)
    }

    private fun applyProcessDraft(text: String) {
        val pending = pendingProcessDraft ?: return
        if (useSafeEditor) {
            updateSafeDraft(text, pending.cursorPosition)
        } else {
            updateEditorDraft(text, pending.cursorPosition)
        }
        pendingProcessDraft = null
    }

    internal fun releaseProcessState() {
        processDraftToken?.let { runCatching { CodeTextTransfer.delete(context, it) } }
        processDraftToken = null
        pendingProcessDraft = null
    }

    fun createResult(text: String?, cursorPosition: Int): Intent =
        CodeTextTransfer.createResult(context, text, cursorPosition) { token ->
            synchronized(ownedOutputTokens) {
                if (outputOwnerCleared) {
                    throw CancellationException("Code editor ViewModel has been cleared")
                }
                ownedOutputTokens += token
            }
        }

    fun releaseOutput(result: Intent) {
        result.getStringExtra(CodeTextTransfer.EXTRA_TEXT_FILE_TOKEN)?.let { token ->
            synchronized(ownedOutputTokens) { ownedOutputTokens -= token }
        }
    }

    override fun onCleared() {
        synchronized(ownedOutputTokens) {
            outputOwnerCleared = true
            ownedOutputTokens.forEach { runCatching { CodeTextTransfer.delete(context, it) } }
            ownedOutputTokens.clear()
        }
        ownedInputToken?.let { runCatching { CodeTextTransfer.delete(context, it) } }
        ownedInputToken = null
        releaseProcessState()
        super.onCleared()
    }

    private fun isHtmlStr(text: String): Boolean {
        val trimmedText = text.trim()
        val htmlRegex = Regex("""^(?:\[[\s\d.]])?<(?:html|!DOCTYPE)""", RegexOption.IGNORE_CASE)
        return htmlRegex.containsMatchIn(trimmedText) && trimmedText.endsWith(">")
    }

    fun loadTextMateThemes(index: Int) {
        val theme = themeFileNames.getOrElse(index) { "d_monokai" }
        val themeModel = themeRegistry.findThemeByFileName(theme)
        if (themeModel == null) {
            val themeAssetsPath = "textmate/$theme.json"
            val themeSource = IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(themeAssetsPath),
                themeAssetsPath,
                null
            )
            themeRegistry.loadTheme(ThemeModel(themeSource, theme).apply {
                isDark = theme.startsWith("d_")
            })
        } else {
            themeRegistry.setTheme(themeModel)
        }
    }

    fun formatCode(editor: CodeEditor) {
        execute {
            val text = editor.text.toString()
            if (languageName.contains("markdown")) {
                context.toastOnUi("markdown不需要格式化")
                return@execute text
            }
            val isHtml = languageName.contains("html")
            if (isHtml) {
                return@execute formatCodeHtml(text)
            }
            var result = ""
            var start = 0
            val indexS = text.indexOf("<js>")
            if (indexS >= 0) {
                if (indexS > 0) {
                    result += text.substring(start, indexS).trim()
                }
                val indexE = text.indexOf("</js>", indexS)
                val jsCode = text.substring(indexS + 4, indexE)
                result += "<js>\n"
                result += webFormatCode(jsCode)
                result += "\n</js>"
                start = indexE + 5
            }
            val indexS2 = text.indexOf("@js:")
            if (indexS2 >= 0) {
                if (indexS2 > start) {
                    result += text.substring(start, indexS2).trim()
                }
                val jsCode = text.substring(indexS2 + 4)
                result += "@js:\n"
                result += webFormatCode(jsCode)
                start = text.length
            } else {
                val indexS2 = text.indexOf("@webjs:")
                if (indexS2 >= 0) {
                    if (indexS2 > start) {
                        result += text.substring(start, indexS2).trim()
                    }
                    val jsCode = text.substring(indexS2 + 7)
                    result += "@webjs:\n"
                    result += webFormatCode(jsCode)
                    start = text.length
                }
            }
            if (start == 0) {
                result += webFormatCode(text)
                start = text.length
            }
            if (text.length > start) {
                result += text.substring(start).trim()
            }
            result
        }.onSuccess {
            editor.setText(it)
        }.onError {
            AppLog.put("格式化失败",it, true)
        }
    }

    private suspend fun webFormatCode(jsCode: String): String? {
        CacheManager.putMemory("web_format_code", jsCode)
        return BackstageWebView(
            url = null,
            html = """<html><body><script src="https://cdnjs.cloudflare.com/ajax/libs/js-beautify/1.15.4/beautify.min.js"></script>
                <script>
                window.re = js_beautify($nameCache.getFromMemory('web_format_code'), {
                indent_size: 4,
                indent_char: ' ',
                preserve_newlines: true,
                max_preserve_newlines: 5,
                brace_style: 'collapse',
                space_before_conditional: true,
                unescape_strings: false,
                jslint_happy: false,
                end_with_newline: false,
                wrap_line_length: 0,
                comma_first: false
                });
                </script></body></html>""".trimIndent(),
            javaScript = "window.re",
            cacheFirst = true,
            timeout = 5000,
            isRule = true
        ).getStrResponse().body
    }

    private fun formatCodeHtml(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.outputSettings()
            .indentAmount(4)
            .prettyPrint(true)
        return doc.outerHtml()
    }

}
