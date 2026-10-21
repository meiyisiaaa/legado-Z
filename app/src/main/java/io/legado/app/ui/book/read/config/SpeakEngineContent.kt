package io.legado.app.ui.book.read.config

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import io.legado.app.R
import io.legado.app.data.entities.HttpTTS
import io.legado.app.ui.widget.compose.LegadoTheme

internal data class SpeakEngineSystemItem(val name: String, val label: String)

internal enum class SpeakEngineMenuAction {
    CLEAR_CACHE, ADD, IMPORT_DEFAULT, IMPORT_LOCAL, IMPORT_ONLINE, EXPORT_ALL, EXPORT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun SpeakEngineContent(
    systems: List<SpeakEngineSystemItem>,
    httpEngines: List<HttpTTS>,
    selectedEngine: String?,
    menuExpanded: Boolean,
    deleteEngine: HttpTTS?,
    showOnlineImport: Boolean,
    onlineUrl: String,
    savedUrls: List<String>,
    exportUrl: String?,
    exportSummary: String?,
    shibbolethUrl: String?,
    showShibboleth: Boolean,
    onSelect: (String) -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMenuAction: (SpeakEngineMenuAction) -> Unit,
    onClick: (HttpTTS) -> Unit,
    onEdit: (HttpTTS) -> Unit,
    onLongClick: (HttpTTS) -> Unit,
    onDeleteRequest: (HttpTTS?) -> Unit,
    onDeleteConfirm: () -> Unit,
    onOnlineImportChange: (Boolean) -> Unit,
    onOnlineUrlChange: (String) -> Unit,
    onDeleteSavedUrl: (String) -> Unit,
    onImportOnline: () -> Unit,
    onCopyExportUrl: () -> Unit,
    onShowShibboleth: () -> Unit,
    onDismissExport: () -> Unit,
    onCopyShibboleth: () -> Unit,
    onDismissShibboleth: () -> Unit,
    onApplyBook: () -> Unit,
    onApplyGlobal: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultSystemName = stringResource(R.string.system_tts_default)
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.speak_engine)) },
            actions = {
                IconButton(onClick = { onMenuAction(SpeakEngineMenuAction.CLEAR_CACHE) }) {
                    Icon(painterResource(R.drawable.ic_clear), contentDescription = stringResource(R.string.clear))
                }
                IconButton(onClick = { onMenuAction(SpeakEngineMenuAction.ADD) }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                }
                IconButton(onClick = { onMenuExpandedChange(true) }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                    MenuItem(R.string.import_default_rule) { onMenuAction(SpeakEngineMenuAction.IMPORT_DEFAULT) }
                    MenuItem(R.string.import_local) { onMenuAction(SpeakEngineMenuAction.IMPORT_LOCAL) }
                    MenuItem(R.string.import_on_line) { onMenuAction(SpeakEngineMenuAction.IMPORT_ONLINE) }
                    MenuItem(R.string.export_all) { onMenuAction(SpeakEngineMenuAction.EXPORT_ALL) }
                    MenuItem(R.string.export) { onMenuAction(SpeakEngineMenuAction.EXPORT) }
                }
            }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { EngineHeader(stringResource(R.string.system_tts)) }
            item {
                EngineChoiceRow(
                    label = defaultSystemName,
                    selected = selectedEngine == "",
                    summary = null,
                    onClick = { onSelect(systemSelection("", defaultSystemName)) }
                )
            }
            items(systems, key = { "system:${it.name}" }) { item ->
                EngineChoiceRow(
                    label = item.label,
                    selected = selectedEngine == item.name,
                    summary = item.name,
                    onClick = { onSelect(systemSelection(item.name, item.label)) }
                )
            }
            item { EngineHeader(stringResource(R.string.http_tts)) }
            items(httpEngines, key = { "http:${it.id}" }) { engine ->
                HttpEngineRow(
                    item = engine,
                    selected = selectedEngine == engine.id.toString(),
                    onClick = { onClick(engine) },
                    onLongClick = { onLongClick(engine) },
                    onEdit = { onEdit(engine) },
                    onDelete = { onDeleteRequest(engine) }
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onApplyBook, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.book))
            }
            OutlinedButton(onClick = onApplyGlobal, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.general))
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }

    deleteEngine?.let { engine ->
        AlertDialog(
            onDismissRequest = { onDeleteRequest(null) },
            title = { Text(stringResource(R.string.draw)) },
            text = { Text(stringResource(R.string.sure_del) + "\n" + engine.name) },
            confirmButton = { TextButton(onClick = onDeleteConfirm) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = { onDeleteRequest(null) }) { Text(stringResource(R.string.no)) } }
        )
    }

    if (showOnlineImport) {
        AlertDialog(
            onDismissRequest = { onOnlineImportChange(false) },
            title = { Text(stringResource(R.string.import_on_line)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = onlineUrl,
                        onValueChange = onOnlineUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("URL") }
                    )
                    savedUrls.forEach { url ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                url,
                                modifier = Modifier.weight(1f).combinedClickable(
                                    onClick = { onOnlineUrlChange(url) },
                                    onLongClick = { onDeleteSavedUrl(url) }
                                ),
                                maxLines = 2,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { onDeleteSavedUrl(url) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onImportOnline) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { onOnlineImportChange(false) }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    exportUrl?.let { url ->
        AlertDialog(
            onDismissRequest = onDismissExport,
            title = { Text(stringResource(R.string.export_success)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    exportSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.path), style = MaterialTheme.typography.labelLarge)
                    SelectionContainer {
                        OutlinedTextField(
                            value = url,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            singleLine = false
                        )
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (shibbolethUrl != null) {
                        TextButton(onClick = onShowShibboleth) {
                            Text(stringResource(R.string.shibboleth))
                        }
                    }
                    TextButton(onClick = onCopyExportUrl) { Text(stringResource(R.string.ok)) }
                }
            }
        )
    }

    if (showShibboleth && shibbolethUrl != null) {
        AlertDialog(
            onDismissRequest = onDismissShibboleth,
            title = { Text(stringResource(R.string.shibboleth)) },
            text = {
                SelectionContainer {
                    OutlinedTextField(
                        value = shibbolethUrl,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onCopyShibboleth) { Text(stringResource(R.string.ok)) }
            }
        )
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun EngineHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun EngineChoiceRow(label: String, selected: Boolean, summary: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HttpEngineRow(
    item: HttpTTS,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_outline_delete), contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

private fun systemSelection(name: String, label: String): String =
    io.legado.app.utils.GSON.toJson(io.legado.app.lib.dialogs.SelectItem(label, name))

@Preview(showBackground = true)
@Composable
private fun SpeakEngineContentPreview() {
    LegadoTheme {
        SpeakEngineContent(
            systems = emptyList(), httpEngines = emptyList(), selectedEngine = "", menuExpanded = false,
            deleteEngine = null, showOnlineImport = false, onlineUrl = "", savedUrls = emptyList(),
            exportUrl = null, exportSummary = null, shibbolethUrl = null, showShibboleth = false,
            onSelect = {}, onMenuExpandedChange = {}, onMenuAction = {}, onClick = {}, onEdit = {}, onDeleteRequest = {},
            onLongClick = {},
            onDeleteConfirm = {}, onOnlineImportChange = {}, onOnlineUrlChange = {}, onDeleteSavedUrl = {},
            onImportOnline = {}, onCopyExportUrl = {}, onShowShibboleth = {}, onDismissExport = {},
            onCopyShibboleth = {}, onDismissShibboleth = {}, onApplyBook = {}, onApplyGlobal = {}, onCancel = {}
        )
    }
}
