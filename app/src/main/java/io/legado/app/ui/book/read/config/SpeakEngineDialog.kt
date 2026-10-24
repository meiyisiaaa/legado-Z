package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File

/** Reader speech-engine selection and HTTP TTS management. */
class SpeakEngineDialog : BaseDialogFragment(R.layout.dialog_speak_engine) {

    private val viewModel: SpeakEngineViewModel by viewModels()
    private val ttsUrlKey = "ttsUrlKey"
    private var ttsEngine by mutableStateOf(ReadAloud.ttsEngine)
    private var httpEngines by mutableStateOf<List<HttpTTS>>(emptyList())
    private var menuExpanded by mutableStateOf(false)
    private var deleteEngine by mutableStateOf<HttpTTS?>(null)
    private var showOnlineImport by mutableStateOf(false)
    private var onlineUrl by mutableStateOf("")
    private var savedUrls by mutableStateOf<List<String>>(emptyList())
    private var exportUrl by mutableStateOf<String?>(null)
    private var exportSummary by mutableStateOf<String?>(null)
    private var shibbolethUrl by mutableStateOf<String?>(null)
    private var showShibboleth by mutableStateOf(false)
    private val callBack: CallBack? get() = parentFragment as? CallBack

    private val importDocResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri -> showDialogFragment(ImportHttpTtsDialog(uri.toString())) }
    }

    private val exportResult = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { uri ->
            val url = uri.toString()
            exportUrl = url
            exportSummary = if (url.isAbsUrl()) DirectLinkUpload.getSummary() else null
            shibbolethUrl = if (url.isAbsUrl()) {
                StringUtils.toShibboleth(url, StringUtils.TTS_RULE, System.currentTimeMillis())
            } else null
            showShibboleth = false
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    val systems = viewModel.sysEngines.map { SpeakEngineSystemItem(it.name, it.label) }
                    SpeakEngineContent(
                        systems = systems,
                        httpEngines = httpEngines,
                        selectedEngine = selectedEngineForUi(),
                        menuExpanded = menuExpanded,
                        deleteEngine = deleteEngine,
                        showOnlineImport = showOnlineImport,
                        onlineUrl = onlineUrl,
                        savedUrls = savedUrls,
                        exportUrl = exportUrl,
                        exportSummary = exportSummary,
                        shibbolethUrl = shibbolethUrl,
                        showShibboleth = showShibboleth,
                        onSelect = ::upTts,
                        onMenuExpandedChange = { menuExpanded = it },
                        onMenuAction = ::onMenuAction,
                        onClick = ::selectHttpEngine,
                        onEdit = { showDialogFragment(HttpTtsEditDialog(it.id)) },
                        onLongClick = ::openLoginIfConfigured,
                        onDeleteRequest = { deleteEngine = it },
                        onDeleteConfirm = {
                            deleteEngine?.let { appDb.httpTTSDao.delete(it) }
                            deleteEngine = null
                        },
                        onOnlineImportChange = { showOnlineImport = it },
                        onOnlineUrlChange = { onlineUrl = it },
                        onDeleteSavedUrl = ::deleteSavedUrl,
                        onImportOnline = ::importOnline,
                        onCopyExportUrl = {
                            exportUrl?.let { requireContext().sendToClip(it) }
                            dismissExportDialog()
                        },
                        onShowShibboleth = { showShibboleth = true },
                        onDismissExport = ::dismissExportDialog,
                        onCopyShibboleth = {
                            shibbolethUrl?.let { requireContext().sendToClip(it) }
                            showShibboleth = false
                        },
                        onDismissShibboleth = { showShibboleth = false },
                        onApplyBook = {
                            ReadBook.book?.setTtsEngine(ttsEngine)
                            callBack?.upSpeakEngineSummary()
                            ReadAloud.upReadAloudClass()
                            dismissAllowingStateLoss()
                        },
                        onApplyGlobal = {
                            ReadBook.book?.setTtsEngine(null)
                            AppConfig.ttsEngine = ttsEngine
                            callBack?.upSpeakEngineSummary()
                            ReadAloud.upReadAloudClass()
                            dismissAllowingStateLoss()
                        },
                        onCancel = { dismissAllowingStateLoss() }
                    )
                }
            }
        }
        observeHttpTts()
    }

    private fun observeHttpTts() {
        lifecycleScope.launch {
            appDb.httpTTSDao.flowAll().catch {
                AppLog.put("朗读引擎界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                httpEngines = it
            }
        }
    }

    private fun selectedEngineForUi(): String? {
        val current = ttsEngine
        if (current.isNullOrBlank()) return ""
        if (current.isJsonObject()) {
            return GSON.fromJsonObject<SelectItem<String>>(current).getOrNull()?.value
        }
        return current
    }

    private fun upTts(tts: String) {
        ttsEngine = tts
    }

    private fun openLoginIfConfigured(httpTts: HttpTTS) {
        if (httpTts.loginUrl.isNullOrBlank()) return
        startActivity<SourceLoginActivity> {
            putExtra("type", "httpTts")
            putExtra("key", httpTts.id.toString())
        }
    }

    private fun selectHttpEngine(httpTts: HttpTTS) {
        upTts(httpTts.id.toString())
        if (!httpTts.loginUrl.isNullOrBlank() && httpTts.getLoginInfo().isNullOrBlank()) {
            openLoginIfConfigured(httpTts)
        }
    }

    private fun onMenuAction(action: SpeakEngineMenuAction) {
        menuExpanded = false
        when (action) {
            SpeakEngineMenuAction.CLEAR_CACHE -> clearCache()
            SpeakEngineMenuAction.ADD -> showDialogFragment<HttpTtsEditDialog>()
            SpeakEngineMenuAction.IMPORT_DEFAULT -> viewModel.importDefault()
            SpeakEngineMenuAction.IMPORT_LOCAL -> importDocResult.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }
            SpeakEngineMenuAction.IMPORT_ONLINE -> openOnlineImport()
            SpeakEngineMenuAction.EXPORT_ALL -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "httpTts.json",
                    GSON.toJson(httpEngines).toByteArray(),
                    "application/json"
                )
            }
            SpeakEngineMenuAction.EXPORT -> exportSelected()
        }
    }

    private fun exportSelected() {
        val id = ttsEngine?.takeUnless { it.isJsonObject() }?.toLongOrNull()
        if (id == null) {
            toastOnUi(R.string.is_system_tts_no_export)
            return
        }
        val tts = httpEngines.firstOrNull { it.id == id } ?: return
        exportResult.launch {
            mode = HandleFileContract.EXPORT
            fileData = HandleFileContract.FileData(
                "httpTts_${tts.name}.json",
                GSON.toJson(tts).toByteArray(),
                "application/json"
            )
        }
    }

    fun clearCache() {
        execute {
            ReadAloud.upReadAloudClass()
            val ttsFolderPath = "${requireContext().cacheDir.absolutePath}${File.separator}httpTTS${File.separator}"
            FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach { FileUtils.delete(it.absolutePath) }
            toastOnUi(R.string.clear_cache_success)
        }
    }

    private fun openOnlineImport() {
        val aCache = ACache.get(cacheDir = false)
        savedUrls = aCache.getAsString(ttsUrlKey)?.splitNotBlank(",")?.toList().orEmpty()
        onlineUrl = ""
        showOnlineImport = true
    }

    private fun deleteSavedUrl(url: String) {
        savedUrls = savedUrls.filterNot { it == url }
        ACache.get(cacheDir = false).put(ttsUrlKey, savedUrls.joinToString(","))
    }

    private fun importOnline() {
        val url = onlineUrl
        if (url.isAbsUrl() && url !in savedUrls) {
            savedUrls = listOf(url) + savedUrls
            ACache.get(cacheDir = false).put(ttsUrlKey, savedUrls.joinToString(","))
        }
        showOnlineImport = false
        showDialogFragment(ImportHttpTtsDialog(url))
    }

    private fun dismissExportDialog() {
        exportUrl = null
        exportSummary = null
        shibbolethUrl = null
        showShibboleth = false
    }

    interface CallBack {
        fun upSpeakEngineSummary()
    }
}
