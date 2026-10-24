package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.DirectLinkUpload
import io.legado.app.ui.widget.compose.*
import io.legado.app.utils.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DirectLinkUploadConfig : BaseDialogFragment(0) {
    private var busy by mutableStateOf(false)
    private var result by mutableStateOf<String?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            val initial = DirectLinkUpload.getRule()
            setContent { LegadoTheme {
                DirectLinkUploadContent(initial, DirectLinkUpload.defaultRules, busy, result,
                    onClose = ::dismiss, onResultClose = { result = null },
                    onCopy = { requireContext().sendToClip(it) },
                    onPaste = {
                        runCatching {
                            GSON.fromJsonObject<DirectLinkUpload.Rule>(requireContext().getClipText().orEmpty()).getOrThrow()
                        }.onFailure { toastOnUi("剪贴板为空或格式不对") }.getOrNull()
                    },
                    onSave = { rule ->
                        runCatching { DirectLinkUpload.putConfig(rule) }
                            .onSuccess { dismiss() }
                            .onFailure { toastOnUi(it.localizedMessage ?: "规则保存失败") }
                    }, onTest = ::test)
            } }
        }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    private fun test(rule: DirectLinkUpload.Rule) {
        if (busy) return
        busy = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                result = withContext(Dispatchers.IO) {
                    DirectLinkUpload.upLoad("test.json", "{}", "application/json", rule)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { result = e.localizedMessage ?: "ERROR" }
            finally { busy = false }
        }
    }
}

@Composable
internal fun DirectLinkUploadContent(initial: DirectLinkUpload.Rule, defaults: List<DirectLinkUpload.Rule>,
    busy: Boolean, result: String?, onClose: () -> Unit, onResultClose: () -> Unit,
    onCopy: (String) -> Unit, onPaste: () -> DirectLinkUpload.Rule?,
    onSave: (DirectLinkUpload.Rule) -> Unit, onTest: (DirectLinkUpload.Rule) -> Unit) {
    var uploadUrl by rememberSaveable { mutableStateOf(initial.uploadUrl.orEmpty()) }
    var downloadRule by rememberSaveable { mutableStateOf(initial.downloadUrlRule.orEmpty()) }
    var summary by rememberSaveable { mutableStateOf(initial.summary.orEmpty()) }
    var compress by rememberSaveable { mutableStateOf(initial.compress) }
    var expiryDate by rememberSaveable { mutableIntStateOf(initial.expiryDate) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var chooseDefault by rememberSaveable { mutableStateOf(false) }
    fun update(rule: DirectLinkUpload.Rule) {
        uploadUrl = rule.uploadUrl.orEmpty()
        downloadRule = rule.downloadUrlRule.orEmpty()
        summary = rule.summary.orEmpty()
        compress = rule.compress
        expiryDate = rule.expiryDate
        invalid = false
    }
    fun rule(): DirectLinkUpload.Rule? {
        invalid = uploadUrl.isBlank() || downloadRule.isBlank() || summary.isBlank()
        return if (invalid) null else initial.copy(uploadUrl = uploadUrl, downloadUrlRule = downloadRule,
            summary = summary, compress = compress, expiryDate = expiryDate)
    }
    Surface {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            ConfigDialogToolbar(stringResource(R.string.direct_link_upload_config), onClose) {
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.menu))
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.copy_rule)) }, onClick = {
                            menu = false; rule()?.let { onCopy(GSON.toJson(it)) }
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.paste_rule)) }, onClick = {
                            menu = false; onPaste()?.let(::update)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.import_default_rule)) },
                            onClick = { menu = false; chooseDefault = true })
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(uploadUrl, { uploadUrl = it; invalid = false }, enabled = !busy,
                    label = { Text(stringResource(R.string.upload_url)) },
                    isError = invalid && uploadUrl.isBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("upload_rule_url"))
                OutlinedTextField(downloadRule, { downloadRule = it; invalid = false }, enabled = !busy,
                    label = { Text(stringResource(R.string.download_url_rule)) },
                    isError = invalid && downloadRule.isBlank(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(summary, { summary = it; invalid = false }, enabled = !busy,
                    label = { Text(stringResource(R.string.summary)) },
                    isError = invalid && summary.isBlank(), modifier = Modifier.fillMaxWidth())
                if (invalid) Text("上传 URL、下载规则和注释不能为空", color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(compress, enabled = !busy, role = Role.Checkbox, onValueChange = { compress = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(compress, onCheckedChange = null, enabled = !busy)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.is_compress))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { rule()?.let(onTest) }, enabled = !busy) { Text(stringResource(R.string.test)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClose) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = { rule()?.let(onSave) }, enabled = !busy,
                    modifier = Modifier.testTag("upload_rule_save")) { Text(stringResource(R.string.ok)) }
            }
        }
    }
    if (chooseDefault) AlertDialog(onDismissRequest = { chooseDefault = false },
        title = { Text(stringResource(R.string.import_default_rule)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(defaults) { _, item ->
                    Text(item.summary, Modifier.fillMaxWidth().clickable {
                        update(item); chooseDefault = false
                    }.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }, confirmButton = { TextButton(onClick = { chooseDefault = false }) { Text(stringResource(R.string.cancel)) } })
    result?.let { message ->
        AlertDialog(onDismissRequest = onResultClose, title = { Text("测试结果") },
            text = { SelectionContainer { Text(message, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onResultClose) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { onCopy(message); onResultClose() }) { Text(stringResource(R.string.copy_text)) } })
    }
}
