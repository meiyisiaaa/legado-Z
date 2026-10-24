package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isImage
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.createFileIfNotExist
import io.legado.app.utils.createFileReplace
import io.legado.app.utils.createFolderReplace
import io.legado.app.utils.delete
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.find
import io.legado.app.utils.getFile
import io.legado.app.utils.inputStream
import io.legado.app.utils.longToast
import io.legado.app.utils.openInputStream
import io.legado.app.utils.openOutputStream
import io.legado.app.utils.outputStream
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.readBytes
import io.legado.app.utils.readUri
import io.legado.app.utils.stackTraceStr
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.setSelectionSafely
import kotlinx.coroutines.launch

class BgTextConfigDialog : BaseDialogFragment(R.layout.dialog_read_bg_text) {

    private enum class BgConfigChoice { Restore, Underline }

    companion object {
        const val TEXT_COLOR = 121
        const val BG_COLOR = 122
        const val TEXT_ACCENT_COLOR = 123
    }

    private val configFileName = "readConfig.zip"
    private val importFormNet = "网络导入"
    private var revision by mutableIntStateOf(0)
    private var imageNames by mutableStateOf<List<String>>(emptyList())
    private var choice by mutableStateOf<BgConfigChoice?>(null)
    private var editNameDialog by mutableStateOf(false)
    private var editName by mutableStateOf("")
    private var netImportDialog by mutableStateOf(false)
    private var netImportUrl by mutableStateOf("")
    private val selectBgImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            setBgFromUri(uri)
        }
    }
    private val selectExportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            exportConfig(uri)
        }
    }
    private val selectImportDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.path == "/$importFormNet") {
                importNetConfigAlert()
            } else {
                importConfig(uri)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        imageNames = requireContext().assets.list("bg")?.toList().orEmpty()
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                @Suppress("UNUSED_VARIABLE")
                val currentRevision = revision
                LegadoTheme {
                    BgTextConfigContent(
                        state = currentState(),
                        onEditName = {
                            editName = ReadBookConfig.durConfig.name
                            editNameDialog = true
                        },
                        onRestore = { choice = BgConfigChoice.Restore },
                        onDarkStatusIconChange = { enabled ->
                            ReadBookConfig.durConfig.setCurStatusIconDark(enabled)
                            (activity as? ReadBookActivity)?.upSystemUiVisibility()
                            revision++
                        },
                        onUnderlineClick = { choice = BgConfigChoice.Underline },
                        onTextColorClick = { showColorPicker(TEXT_COLOR, ReadBookConfig.durConfig.curTextColor()) },
                        onBackgroundColorClick = {
                            val background = if (ReadBookConfig.durConfig.curBgType() == 0) {
                                ReadBookConfig.durConfig.curBgStr().toColorInt()
                            } else "#015A86".toColorInt()
                            showColorPicker(BG_COLOR, background)
                        },
                        onAccentColorClick = {
                            showColorPicker(TEXT_ACCENT_COLOR, ReadBookConfig.durConfig.curTextAccentColor())
                        },
                        onImport = { launchImport() },
                        onExport = { selectExportDir.launch { title = getString(R.string.export_str) } },
                        onDelete = {
                            if (ReadBookConfig.deleteDur()) {
                                postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
                                dismissAllowingStateLoss()
                            } else toastOnUi("数量已是最少,不能删除.")
                        },
                        onBackgroundAlphaChange = { value ->
                            ReadBookConfig.bgAlpha = value
                            postEvent(EventBus.UP_CONFIG, arrayListOf(3))
                            revision++
                        },
                        onBackgroundAlphaFinished = { postEvent(EventBus.UP_CONFIG, arrayListOf(3)) },
                        onSelectImage = {
                            selectBgImage.launch { mode = HandleFileContract.IMAGE }
                        },
                        onAssetSelect = { name ->
                            ReadBookConfig.durConfig.setCurBg(1, name)
                            postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                            revision++
                        }
                    )
                    renderDialogs()
                }
            }
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) { revision++ }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun currentState(): BgTextConfigUiState {
        val config = ReadBookConfig.durConfig
        val bgValue = config.curBgStr()
        val bgColor = if (config.curBgType() == 0) bgValue.toColorInt() else "#015A86".toColorInt()
        return BgTextConfigUiState(
            name = config.name,
            darkStatusIcon = config.curStatusIconDark(),
            isImageBook = ReadBook.book?.isImage == true,
            underlineMode = config.underlineMode,
            underlineOptions = requireContext().resources.getStringArray(R.array.read_underline_styles).toList(),
            textColor = config.curTextColor(),
            backgroundColor = bgColor,
            accentColor = config.curTextAccentColor(),
            backgroundAlpha = ReadBookConfig.bgAlpha,
            imageNames = imageNames,
            backgroundType = config.curBgType(),
            backgroundValue = bgValue
        )
    }

    @androidx.compose.runtime.Composable
    private fun renderDialogs() {
        when (choice) {
            BgConfigChoice.Restore -> {
                val presets = DefaultData.readConfigs
                ReaderConfigChoiceDialog(
                    title = getString(R.string.restore),
                    options = presets.map { it.name },
                    onDismiss = { choice = null },
                    onSelect = { index ->
                        ReadBookConfig.durConfig = presets[index].copy()
                        choice = null
                        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 13))
                        revision++
                    }
                )
            }
            BgConfigChoice.Underline -> {
                val options = requireContext().resources.getStringArray(R.array.read_underline_styles).toList()
                ReaderConfigChoiceDialog(
                    title = getString(R.string.text_underline),
                    options = options,
                    selectedIndex = ReadBookConfig.durConfig.underlineMode,
                    onDismiss = { choice = null },
                    onSelect = { index ->
                        ReadBookConfig.durConfig.underlineMode = index
                        choice = null
                        postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
                        revision++
                    }
                )
            }
            null -> Unit
        }
        if (editNameDialog) {
            AlertDialog(
                onDismissRequest = { editNameDialog = false },
                title = { Text(stringResource(R.string.style_name)) },
                text = {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.name)) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        ReadBookConfig.durConfig.name = editName
                        editNameDialog = false
                        revision++
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { editNameDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
        if (netImportDialog) {
            AlertDialog(
                onDismissRequest = { netImportDialog = false },
                title = { Text(stringResource(R.string.import_on_line)) },
                text = {
                    OutlinedTextField(
                        value = netImportUrl,
                        onValueChange = { netImportUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("URL") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val url = netImportUrl
                        netImportDialog = false
                        importNetConfig(url)
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { netImportDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }

    private fun showColorPicker(dialogId: Int, color: Int) {
        ColorPickerDialog.newBuilder()
            .setColor(color)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(dialogId)
            .show(requireActivity())
    }

    private fun launchImport() {
        selectImportDoc.launch {
            mode = HandleFileContract.FILE
            title = getString(R.string.import_str)
            allowExtensions = arrayOf("zip")
            otherActions = arrayListOf(SelectItem(importFormNet, -1))
        }
    }

    private fun exportConfig(uri: Uri) {
        val exportFileName = if (ReadBookConfig.config.name.isBlank()) {
            configFileName
        } else {
            "${ReadBookConfig.config.name}.zip"
        }
        execute {
            val exportFiles = arrayListOf<File>()
            val configDir = requireContext().externalCache.getFile("readConfig")
            configDir.createFolderReplace()
            val configFile = configDir.getFile("readConfig.json")
            configFile.createFileReplace()
            val config = ReadBookConfig.getExportConfig()
            val fontPath = ReadBookConfig.textFont
            if (fontPath.isNotEmpty()) {
                val fontDoc = FileDoc.fromFile(fontPath)
                val fontName = fontDoc.name
                val fontInputStream = fontDoc.openInputStream().getOrNull()
                fontInputStream?.use {
                    val fontExportFile = FileUtils.createFileIfNotExist(configDir, fontName)
                    fontExportFile.outputStream().use { out ->
                        it.copyTo(out)
                    }
                    config.textFont = fontName
                    exportFiles.add(fontExportFile)
                }
            }
            configFile.writeText(GSON.toJson(config))
            exportFiles.add(configFile)
            repeat(3) {
                val path = ReadBookConfig.durConfig.getBgPath(it) ?: return@repeat
                val bgExportFile = copyBgImage(path, configDir) ?: return@repeat
                exportFiles.add(bgExportFile)
            }
            val configZipPath = FileUtils.getPath(requireContext().externalCache, configFileName)
            if (ZipUtils.zipFiles(exportFiles, File(configZipPath))) {
                val exportDir = FileDoc.fromDir(uri)
                exportDir.find(exportFileName)?.delete()
                val exportFileDoc = exportDir.createFileIfNotExist(exportFileName)
                exportFileDoc.openOutputStream().getOrThrow().use { out ->
                    File(configZipPath).inputStream().use {
                        it.copyTo(out)
                    }
                }
            }
        }.onSuccess {
            toastOnUi("导出成功, 文件名为 $exportFileName")
        }.onError {
            it.printOnDebug()
            AppLog.put("导出失败:${it.localizedMessage}", it)
            longToast("导出失败:${it.localizedMessage}")
        }
    }

    private fun copyBgImage(path: String, configDir: File): File? {
        val bgName = FileUtils.getName(path)
        val bgFile = File(path)
        if (bgFile.exists()) {
            val bgExportFile = File(FileUtils.getPath(configDir, bgName))
            if (!bgExportFile.exists()) {
                bgFile.copyTo(bgExportFile)
                return bgExportFile
            }
        }
        return null
    }

    private fun importNetConfigAlert() {
        netImportUrl = ""
        netImportDialog = true
    }

    private fun importNetConfig(url: String) {
        execute {
            okHttpClient.newCallResponseBody {
                url(url)
            }.bytes().let {
                importConfig(it)
            }
        }.onError {
            longToast(it.stackTraceStr)
        }
    }

    private fun importConfig(uri: Uri) {
        execute {
            importConfig(uri.readBytes(requireContext()))
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun importConfig(byteArray: ByteArray) {
        execute {
            ReadBookConfig.import(byteArray)
        }.onSuccess {
            ReadBookConfig.durConfig = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
            toastOnUi("导入成功")
        }.onError {
            it.printOnDebug()
            longToast("导入失败:${it.localizedMessage}")
        }
    }

    private fun setBgFromUri(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            lifecycleScope.launch {
                kotlin.runCatching {
                    appCtx.toastOnUi("下载图片中...")
                    val analyzeUrl = AnalyzeUrl(uri.toString())
                    val url = analyzeUrl.urlNoQuery
                    var file = requireContext().externalFiles
                    val res = okHttpClient.newCallResponse(0) {
                        addHeaders(analyzeUrl.headerMap)
                        url(url)
                    }
                    val contentType = res.header("Content-Type") ?: "image/jpeg"
                    val imageType = when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val suffix = if (url.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        ".$imageType"
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    ReadBookConfig.durConfig.setCurBg(2, fileName)
                    postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                }.onSuccess {
                    appCtx.toastOnUi("设定成功")
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, "bg", fileName)
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                ReadBookConfig.durConfig.setCurBg(2, fileName)
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }
}
