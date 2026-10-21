package io.legado.app.ui.book.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart

enum class SearchScopeMode { Group, Source }

@Composable
fun SearchScopeContent(
    mode: SearchScopeMode,
    groups: List<String>,
    sources: List<BookSourcePart>,
    selectedGroups: Set<String>,
    selectedSource: BookSourcePart?,
    filterText: String,
    isFilterVisible: Boolean,
    onModeChange: (SearchScopeMode) -> Unit,
    onFilterTextChange: (String) -> Unit,
    onFilterToggle: () -> Unit,
    onGroupToggle: (String, Boolean) -> Unit,
    onSourceSelect: (BookSourcePart) -> Unit,
    onAllSources: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.search_scope), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (mode == SearchScopeMode.Group) stringResource(R.string.group)
                    else stringResource(R.string.book_source),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mode == SearchScopeMode.Source) {
                IconButton(onClick = onFilterToggle) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.screen),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (mode == SearchScopeMode.Source && isFilterVisible) {
            OutlinedTextField(
                value = filterText,
                onValueChange = onFilterTextChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                label = { Text(stringResource(R.string.search_book_source)) },
                singleLine = true,
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search_hint), contentDescription = null)
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScopeModeButton(
                text = stringResource(R.string.group),
                selected = mode == SearchScopeMode.Group,
                onClick = { onModeChange(SearchScopeMode.Group) },
                modifier = Modifier.weight(1f)
            )
            ScopeModeButton(
                text = stringResource(R.string.book_source),
                selected = mode == SearchScopeMode.Source,
                onClick = { onModeChange(SearchScopeMode.Source) },
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                SearchScopeMode.Group -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(groups, key = { "group-$it" }) { group ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = group in selectedGroups,
                                    onClick = { onGroupToggle(group, group !in selectedGroups) },
                                    role = Role.Checkbox
                                ),
                            color = if (group in selectedGroups) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = group in selectedGroups,
                                    onCheckedChange = { onGroupToggle(group, it) }
                                )
                                Text(group, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                            }
                        }
                    }
                }
                SearchScopeMode.Source -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sources, key = { "source-${it.bookSourceUrl}" }) { source ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = source == selectedSource,
                                    onClick = { onSourceSelect(source) },
                                    role = Role.RadioButton
                                ),
                            color = if (source == selectedSource) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = source == selectedSource, onClick = null)
                                Text(
                                    source.bookSourceName,
                                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    if (sources.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.empty),
                                modifier = Modifier.fillMaxWidth().padding(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onAllSources) { Text(stringResource(R.string.all_source)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            FilledTonalButton(onClick = onConfirm, modifier = Modifier.padding(start = 4.dp)) {
                Text(stringResource(R.string.ok))
            }
        }
    }
}

@Composable
private fun ScopeModeButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text)
    }
}
