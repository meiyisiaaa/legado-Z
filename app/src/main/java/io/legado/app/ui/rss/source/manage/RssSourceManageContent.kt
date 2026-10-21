package io.legado.app.ui.rss.source.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourceManageContent(
    sources: List<RssSource>,
    groups: List<String>,
    query: String,
    selectedUrls: Set<String>,
    selectionVersion: Int,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTopAction: (Int) -> Unit,
    onSelectionAction: (Int) -> Unit,
    onSelectionChange: (RssSource, Boolean) -> Unit,
    onEnabledChange: (RssSource, Boolean) -> Unit,
    onEdit: (RssSource) -> Unit,
    onTop: (RssSource) -> Unit,
    onBottom: (RssSource) -> Unit,
    onMove: (String, String, Boolean, () -> Unit) -> Unit,
    onDelete: (RssSource) -> Unit,
    onSelectAll: () -> Unit,
    onRevertSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    var topMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var selectionMenu by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf<RssSource?>(null) }
    val selectedCount = selectedUrls.size
    val allSelected = sources.isNotEmpty() && selectedCount >= sources.size
    val enableLabel = stringResource(R.string.enable)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The host publishes a version because the legacy adapter selection is not part of the source model.
    remember(selectionVersion) { selectedCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rss_source)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    BoxWithMenu(
                        expanded = filterMenu,
                        onExpand = { filterMenu = true },
                        onDismiss = { filterMenu = false },
                        icon = R.drawable.ic_groups,
                        description = stringResource(R.string.group)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.group_manage)) },
                            onClick = { filterMenu = false; onTopAction(R.id.menu_group_manage) }
                        )
                        listOf(
                            stringResource(R.string.enabled) to R.string.enabled,
                            stringResource(R.string.disabled) to R.string.disabled,
                            stringResource(R.string.need_login) to R.string.need_login,
                            stringResource(R.string.no_group) to R.string.no_group
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                onClick = { filterMenu = false; onQueryChange(value) }
                            )
                        }
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group) },
                                onClick = { filterMenu = false; onQueryChange("group:$group") }
                            )
                        }
                    }
                    BoxWithMenu(
                        expanded = topMenu,
                        onExpand = { topMenu = true },
                        onDismiss = { topMenu = false },
                        icon = R.drawable.ic_more_vert,
                        description = stringResource(R.string.menu)
                    ) {
                        listOf(
                            R.id.menu_add to R.string.add_rss_source,
                            R.id.menu_import_local to R.string.import_local,
                            R.id.menu_import_onLine to R.string.import_on_line,
                            R.id.menu_import_qr to R.string.import_by_qr_code,
                            R.id.menu_import_default to R.string.import_default_rule,
                            R.id.menu_help to R.string.help
                        ).forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                onClick = { topMenu = false; onTopAction(id) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { if (it) onSelectAll() else onRevertSelection() },
                        enabled = sources.isNotEmpty()
                    )
                    Text(
                        text = stringResource(
                            if (allSelected) R.string.select_cancel_count else R.string.select_all_count,
                            selectedCount,
                            sources.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onRevertSelection, enabled = selectedCount > 0) {
                    Text(stringResource(R.string.revert_selection), maxLines = 1)
                }
                TextButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
                BoxWithMenu(
                    expanded = selectionMenu,
                    onExpand = { selectionMenu = true },
                    onDismiss = { selectionMenu = false },
                    icon = R.drawable.ic_more_vert,
                    description = stringResource(R.string.menu),
                    enabled = selectedCount > 0
                ) {
                    listOf(
                        R.id.menu_enable_selection to R.string.enable_selection,
                        R.id.menu_disable_selection to R.string.disable_selection,
                        R.id.menu_add_group to R.string.add_group,
                        R.id.menu_remove_group to R.string.remove_group,
                        R.id.menu_top_sel to R.string.selection_to_top,
                        R.id.menu_bottom_sel to R.string.selection_to_bottom,
                        R.id.menu_export_selection to R.string.export_selection,
                        R.id.menu_share_source to R.string.share_selected_source,
                        R.id.menu_check_selected_interval to R.string.check_selected_interval
                    ).forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = { selectionMenu = false; onSelectionAction(id) }
                        )
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            val searchLabel = stringResource(R.string.search_rss_source)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                    .semantics { contentDescription = searchLabel },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_rss_source)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) }
            )
            if (sources.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.rss_source_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    itemsIndexed(sources, key = { _, source -> source.sourceUrl }) { sourceIndex, source ->
                        val sourceLabel = source.getDisplayNameGroup()
                        val moveUpLabel = stringResource(R.string.move_up)
                        val moveDownLabel = stringResource(R.string.move_down)
                        val reorderActions = buildList {
                            sources.getOrNull(sourceIndex - 1)?.let { target ->
                                add(CustomAccessibilityAction(moveUpLabel) {
                                    onMove(source.sourceUrl, target.sourceUrl, false) {}
                                    true
                                })
                            }
                            sources.getOrNull(sourceIndex + 1)?.let { target ->
                                add(CustomAccessibilityAction(moveDownLabel) {
                                    onMove(source.sourceUrl, target.sourceUrl, true) {}
                                    true
                                })
                            }
                        }
                        val dragModifier = Modifier.pointerInput(source.sourceUrl, sources) {
                            var initialIndex = -1
                            var targetIndex = -1
                            var pointerY = 0f
                            var dragging = false
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point ->
                                    initialIndex = sources.indexOfFirst { it.sourceUrl == source.sourceUrl }
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == source.sourceUrl }
                                        ?.let { item ->
                                            targetIndex = item.index
                                            pointerY = item.offset + point.y
                                            dragging = initialIndex >= 0
                                        }
                                },
                                onDragEnd = {
                                    if (dragging && targetIndex != initialIndex) {
                                        val movedDown = targetIndex > initialIndex
                                        val neighborIndex = if (movedDown) targetIndex - 1 else targetIndex + 1
                                        sources.getOrNull(neighborIndex)?.let { target ->
                                            onMove(source.sourceUrl, target.sourceUrl, movedDown) {}
                                        }
                                    }
                                    dragging = false
                                },
                                onDragCancel = { dragging = false }
                            ) { change, dragAmount ->
                                if (!dragging) return@detectDragGesturesAfterLongPress
                                change.consume()
                                pointerY += dragAmount.y
                                val info = listState.layoutInfo
                                val visibleTarget = info.visibleItemsInfo.minByOrNull {
                                    kotlin.math.abs(it.offset + it.size / 2f - pointerY)
                                }
                                targetIndex = visibleTarget?.index?.coerceIn(sources.indices) ?: targetIndex
                                val edge = 40.dp.toPx()
                                val scroll = when {
                                    pointerY < info.viewportStartOffset + edge -> -18.dp.toPx()
                                    pointerY > info.viewportEndOffset - edge -> 18.dp.toPx()
                                    else -> 0f
                                }
                                if (scroll != 0f) scope.launch { listState.scrollBy(scroll) }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .toggleable(
                                    value = source.sourceUrl in selectedUrls,
                                    role = Role.Checkbox,
                                    onValueChange = { onSelectionChange(source, it) }
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .then(dragModifier),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = source.sourceUrl in selectedUrls,
                                onCheckedChange = null,
                                modifier = Modifier.semantics { customActions = reorderActions }
                            )
                            Text(
                                text = sourceLabel,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Switch(
                                checked = source.enabled,
                                onCheckedChange = { onEnabledChange(source, it) },
                                modifier = Modifier.semantics {
                                    contentDescription = "$enableLabel, $sourceLabel"
                                }
                            )
                            IconButton(onClick = { onEdit(source) }, modifier = Modifier.size(40.dp)) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
                            }
                            BoxWithMenu(
                                expanded = sourceMenu?.sourceUrl == source.sourceUrl,
                                onExpand = { sourceMenu = source },
                                onDismiss = { sourceMenu = null },
                                icon = R.drawable.ic_more_vert,
                                description = stringResource(R.string.more_menu)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.to_top)) },
                                    onClick = { sourceMenu = null; onTop(source) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.to_bottom)) },
                                    onClick = { sourceMenu = null; onBottom(source) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    onClick = { sourceMenu = null; onDelete(source) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithMenu(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    icon: Int,
    description: String,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = onExpand, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(icon), contentDescription = description)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = content)
    }
}
