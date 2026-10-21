package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.model.CheckSource
import io.legado.app.ui.widget.compose.*
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout

internal data class SourceCheckSettings(
    val timeout: Long, val writeComment: Boolean, val domain: Boolean,
    val search: Boolean, val discovery: Boolean, val info: Boolean,
    val category: Boolean, val content: Boolean
)

class CheckSourceConfig : BaseDialogFragment(0) {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            val initial = CheckSource.run { SourceCheckSettings(timeout, wSourceComment, checkDomain,
                checkSearch, checkDiscovery, checkInfo, checkCategory, checkContent) }
            setContent { LegadoTheme { SourceCheckContent(initial, ::dismiss) { config ->
                CheckSource.run {
                    timeout = config.timeout
                    wSourceComment = config.writeComment
                    checkDomain = config.domain
                    checkSearch = config.search
                    checkDiscovery = config.discovery
                    checkInfo = config.info
                    checkCategory = config.category
                    checkContent = config.content
                    putConfig()
                    putPrefString(PreferKey.checkSource, summary)
                }
                dismiss()
            } } }
        }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit
}

@Composable
internal fun SourceCheckContent(initial: SourceCheckSettings, onClose: () -> Unit,
    onSave: (SourceCheckSettings) -> Unit) {
    var timeout by rememberSaveable { mutableStateOf((initial.timeout / 1000).toString()) }
    var writeComment by rememberSaveable { mutableStateOf(initial.writeComment) }
    var domain by rememberSaveable { mutableStateOf(initial.domain) }
    var search by rememberSaveable { mutableStateOf(initial.search) }
    var discovery by rememberSaveable { mutableStateOf(initial.discovery) }
    var info by rememberSaveable { mutableStateOf(initial.info) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var content by rememberSaveable { mutableStateOf(initial.content) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    fun clearDetails() { info = false; category = false; content = false }
    Surface {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            ConfigDialogToolbar(stringResource(R.string.check_source_config), onClose)
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).weight(1f, fill = false)
                .verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OutlinedTextField(timeout, { value ->
                    if (value.all { it.isDigit() }) {
                        timeout = value
                        invalid = false
                    }
                },
                    label = { Text(stringResource(R.string.check_source_timeout)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = invalid, modifier = Modifier.fillMaxWidth().testTag("source_check_timeout"))
                if (invalid) Text("请输入有效的正整数秒数", color = MaterialTheme.colorScheme.error)
                SourceCheckToggle(stringResource(R.string.write_source_comment), writeComment,
                    onChange = { writeComment = it })
                Text(
                    text = stringResource(R.string.check_source_item),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    SourceCheckToggle(stringResource(R.string.domain), domain, tag = "source_check_domain",
                        onChange = { domain = it; if (!domain && !search && !discovery) search = true })
                    SourceCheckToggle(stringResource(R.string.search), search, tag = "source_check_search", onChange = {
                        search = it
                        if (!search && !discovery) { clearDetails(); if (!domain) discovery = true }
                    })
                    SourceCheckToggle(stringResource(R.string.discovery), discovery, tag = "source_check_discovery", onChange = {
                        discovery = it
                        if (!search && !discovery) { clearDetails(); if (!domain) search = true }
                    })
                    SourceCheckToggle(stringResource(R.string.source_tab_info), info, search || discovery,
                        "source_check_info") {
                        info = it; if (!info) { category = false; content = false }
                    }
                    SourceCheckToggle(stringResource(R.string.chapter_list), category, info, "source_check_category") {
                        category = it; if (!category) content = false
                    }
                    SourceCheckToggle(stringResource(R.string.main_body), content, category, "source_check_content") { content = it }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClose) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    val seconds = timeout.toLongOrNull()
                    invalid = seconds == null || seconds <= 0 || seconds > Long.MAX_VALUE / 1000
                    if (!invalid) onSave(SourceCheckSettings(seconds!! * 1000, writeComment, domain,
                        search, discovery, info, category, content))
                }, modifier = Modifier.testTag("source_check_save")) { Text(stringResource(R.string.ok)) }
            }
        }
    }
}

@Composable
private fun SourceCheckToggle(label: String, checked: Boolean, enabled: Boolean = true,
    tag: String = "", onChange: (Boolean) -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).testTag(tag)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
    }
}
