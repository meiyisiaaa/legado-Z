package io.legado.app.ui.book.source.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal data class SourceDebugSuggestion(
    val label: String,
    val value: String,
    val choices: List<Pair<String, String>> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceDebugContent(
    title: String,
    queryHintRes: Int = R.string.search_book_key,
    query: String,
    suggestions: List<SourceDebugSuggestion>,
    logs: List<String>,
    loading: Boolean,
    showHelp: Boolean,
    showScan: Boolean = true,
    menuItems: List<Pair<Int, Int>>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onScan: () -> Unit,
    onAction: (Int) -> Unit,
    onBack: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var openChoices by remember { mutableStateOf<SourceDebugSuggestion?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (showScan) {
                        IconButton(onClick = onScan) {
                            Icon(painterResource(R.drawable.ic_scan), contentDescription = stringResource(R.string.scan_qr_code))
                        }
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        menuItems.forEach { (id, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = { menuExpanded = false; onAction(id) }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text(stringResource(queryHintRes)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) })
            )
            if (showHelp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (suggestions.isNotEmpty()) {
                        Text("调试搜索>>输入关键字，如：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            suggestions.take(2).forEach { suggestion ->
                                DebugSuggestionChip(suggestion, openChoices == suggestion, { openChoices = suggestion }, { openChoices = null }, onSearch)
                            }
                        }
                        suggestions.drop(2).forEach { suggestion ->
                            val heading = when {
                                suggestion.label.contains("发现") -> "调试发现>>输入发现URL，如："
                                suggestion.label.contains("详情") -> "调试详情页>>输入详情页URL，如："
                                suggestion.label.contains("目录") -> "调试目录页>>输入目录页URL，如："
                                suggestion.label.contains("正文") -> "调试正文页>>输入正文页URL，如："
                                else -> null
                            }
                            if (heading != null) {
                                Text(heading, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DebugSuggestionChip(suggestion, openChoices == suggestion, { openChoices = suggestion }, { openChoices = null }, onSearch)
                            }
                        }
                    }
                }
            }
            if (loading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在调试…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(logs, key = { index, text -> "$index:${text.hashCode()}" }) { _, message ->
                    SelectionContainer {
                        Text(
                            text = message,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugSuggestionChip(
    suggestion: SourceDebugSuggestion,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = false,
            onClick = { onSearch(suggestion.value) },
            label = { Text(suggestion.label, maxLines = 1) }
        )
        if (suggestion.choices.isNotEmpty()) {
            Box {
                androidx.compose.material3.IconButton(onClick = onExpand) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                    suggestion.choices.forEach { (label, value) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onDismiss(); onSearch(value) })
                    }
                }
            }
        }
    }
}
