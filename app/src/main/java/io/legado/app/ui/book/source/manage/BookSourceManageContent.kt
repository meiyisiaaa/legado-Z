package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BookSourceManageContent(
    sources: List<BookSourcePart>,
    groups: List<String>,
    query: String,
    selectedUrls: Set<String>,
    selectionVersion: Int,
    sort: BookSourceSort,
    sortAscending: Boolean,
    groupByDomain: Boolean,
    sourceHost: (String) -> String,
    debugMessages: Map<String, String>,
    isChecking: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTopAction: (Int) -> Unit,
    onSort: (BookSourceSort) -> Unit,
    onToggleSortDirection: () -> Unit,
    onToggleGroupByDomain: () -> Unit,
    onSelectionAction: (Int) -> Unit,
    onSelectionChange: (BookSourcePart, Boolean) -> Unit,
    onEnabledChange: (BookSourcePart, Boolean) -> Unit,
    onExploreChange: (BookSourcePart, Boolean) -> Unit,
    onEdit: (BookSourcePart) -> Unit,
    onLogin: (BookSourcePart) -> Unit,
    onMove: (String, String, Boolean, () -> Unit) -> Unit,
    onSearchBook: (BookSourcePart) -> Unit,
    onDebug: (BookSourcePart) -> Unit,
    onTop: (BookSourcePart) -> Unit,
    onBottom: (BookSourcePart) -> Unit,
    onDelete: (BookSourcePart) -> Unit,
    onSelectAll: () -> Unit,
    onRevertSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    var actionsOpen by remember { mutableStateOf(false) }
    var groupsOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var selectedActionsOpen by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf<BookSourcePart?>(null) }
    // Keep the selection count in the composition key so legacy adapter updates are reflected.
    remember(selectionVersion) { selectedUrls.size }
    val listState = rememberLazyListState()
    val allSelected = sources.isNotEmpty() && selectedUrls.size >= sources.size
    val enableLabel = stringResource(R.string.enable)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_source)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            DropdownMenuItem(
                                text = { Text((if (!sortAscending) "✓  " else "") + stringResource(R.string.sort_desc)) },
                                onClick = { sortOpen = false; onToggleSortDirection() }
                            )
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            listOf(
                                BookSourceSort.Default to R.string.sort_manual,
                                BookSourceSort.Weight to R.string.sort_auto,
                                BookSourceSort.Name to R.string.sort_by_name,
                                BookSourceSort.Url to R.string.sort_by_url,
                                BookSourceSort.Update to R.string.sort_by_lastUpdateTime,
                                BookSourceSort.Respond to R.string.sort_by_respondTime,
                                BookSourceSort.Enable to R.string.is_enabled
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text((if (sort == value) "✓  " else "") + stringResource(label)) },
                                    onClick = { sortOpen = false; onSort(value) }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { groupsOpen = true }) {
                            Icon(painterResource(R.drawable.ic_groups), contentDescription = stringResource(R.string.group))
                        }
                        DropdownMenu(expanded = groupsOpen, onDismissRequest = { groupsOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.group_manage)) },
                                onClick = { groupsOpen = false; onTopAction(R.id.menu_group_manage) }
                            )
                            listOf(
                                stringResource(R.string.enabled) to R.string.enabled,
                                stringResource(R.string.disabled) to R.string.disabled,
                                stringResource(R.string.need_login) to R.string.need_login,
                                stringResource(R.string.no_group) to R.string.no_group,
                                stringResource(R.string.enabled_explore) to R.string.enabled_explore,
                                stringResource(R.string.disabled_explore) to R.string.disabled_explore
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = { groupsOpen = false; onQueryChange(value) }
                                )
                            }
                            groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group) },
                                    onClick = { groupsOpen = false; onQueryChange("group:$group") }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { actionsOpen = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            listOf(
                                R.id.menu_add_book_source to R.string.add_book_source,
                                R.id.menu_ai_source_assistant to R.string.ai_source_assistant,
                                R.id.menu_import_local to R.string.import_local,
                                R.id.menu_import_onLine to R.string.import_on_line,
                                R.id.menu_import_qr to R.string.import_by_qr_code
                            ).forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    onClick = { actionsOpen = false; onTopAction(id) }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text((if (groupByDomain) "✓  " else "") + stringResource(R.string.group_sources_by_domain))
                                },
                                onClick = { actionsOpen = false; onToggleGroupByDomain() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.help)) },
                                onClick = { actionsOpen = false; onTopAction(R.id.menu_help) }
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).semantics { contentDescription = enableLabel },
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
                            selectedUrls.size,
                            sources.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onRevertSelection, enabled = selectedUrls.isNotEmpty()) {
                    Text(stringResource(R.string.revert_selection), maxLines = 1)
                }
                TextButton(onClick = onDeleteSelected, enabled = selectedUrls.isNotEmpty()) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
                Box {
                    IconButton(
                        onClick = { selectedActionsOpen = true },
                        enabled = selectedUrls.isNotEmpty(),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = selectedActionsOpen, onDismissRequest = { selectedActionsOpen = false }) {
                        listOf(
                            R.id.menu_enable_selection to R.string.enable_selection,
                            R.id.menu_disable_selection to R.string.disable_selection,
                            R.id.menu_enable_explore to R.string.enable_explore,
                            R.id.menu_disable_explore to R.string.disable_explore,
                            R.id.menu_add_group to R.string.add_group,
                            R.id.menu_remove_group to R.string.remove_group,
                            R.id.menu_top_sel to R.string.selection_to_top,
                            R.id.menu_bottom_sel to R.string.selection_to_bottom,
                            R.id.menu_export_selection to R.string.export_selection,
                            R.id.menu_export_main_js to R.string.export_main_js_file,
                            R.id.menu_share_source to R.string.share_selected_source,
                            R.id.menu_check_source to R.string.check_select_source,
                            R.id.menu_check_selected_interval to R.string.check_selected_interval
                        ).forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                enabled = selectedUrls.isNotEmpty(),
                                onClick = { selectedActionsOpen = false; onSelectionAction(id) }
                            )
                        }
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_book_source)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) }
            )
            if (sources.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (query.isBlank()) {
                        TextButton(onClick = { onTopAction(R.id.menu_add_book_source) }) {
                            Text(stringResource(R.string.add_book_source))
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    if (groupByDomain) {
                        sources.groupBy { sourceHost(it.bookSourceUrl) }.forEach { (host, domainSources) ->
                            stickyHeader(key = "host:$host") {
                                Text(
                                    host,
                                    Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(domainSources, key = { it.bookSourceUrl }) { source ->
                                BookSourceRow(
                                    source = source,
                                    selected = source.bookSourceUrl in selectedUrls,
                                    menuExpanded = sourceMenu == source,
                                    debugMessage = debugMessages[source.bookSourceUrl],
                                    isChecking = isChecking,
                                    canMove = sort == BookSourceSort.Default && !groupByDomain,
                                    listState = listState,
                                    sources = sources,
                                    onMove = onMove,
                                    onMenu = { sourceMenu = source },
                                    onDismissMenu = { sourceMenu = null },
                                    onSelected = { onSelectionChange(source, it) },
                                    onEnabled = { onEnabledChange(source, it) },
                                    onExplore = { onExploreChange(source, it) },
                                    onEdit = { onEdit(source) },
                                    onLogin = { onLogin(source) },
                                    onSearch = { onSearchBook(source) },
                                    onDebug = { onDebug(source) },
                                    onTop = { onTop(source) },
                                    onBottom = { onBottom(source) },
                                    onDelete = { onDelete(source) }
                                )
                            }
                        }
                    } else {
                        items(sources, key = { it.bookSourceUrl }) { source ->
                            BookSourceRow(
                                source = source,
                                selected = source.bookSourceUrl in selectedUrls,
                                menuExpanded = sourceMenu == source,
                                debugMessage = debugMessages[source.bookSourceUrl],
                                isChecking = isChecking,
                                canMove = sort == BookSourceSort.Default && !groupByDomain,
                                listState = listState,
                                sources = sources,
                                onMove = onMove,
                                onMenu = { sourceMenu = source },
                                onDismissMenu = { sourceMenu = null },
                                onSelected = { onSelectionChange(source, it) },
                                onEnabled = { onEnabledChange(source, it) },
                                onExplore = { onExploreChange(source, it) },
                                onEdit = { onEdit(source) },
                                onLogin = { onLogin(source) },
                                onSearch = { onSearchBook(source) },
                                onDebug = { onDebug(source) },
                                onTop = { onTop(source) },
                                onBottom = { onBottom(source) },
                                onDelete = { onDelete(source) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookSourceRow(
    source: BookSourcePart,
    selected: Boolean,
    menuExpanded: Boolean,
    debugMessage: String?,
    isChecking: Boolean,
    canMove: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    sources: List<BookSourcePart>,
    onMove: (String, String, Boolean, () -> Unit) -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSelected: (Boolean) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onExplore: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
    onDebug: () -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onDelete: () -> Unit
) {
    val loginLabel = stringResource(R.string.login)
    val enableLabel = stringResource(R.string.enable)
    val enableExploreLabel = stringResource(if (source.enabledExplore) R.string.disable_explore else R.string.enable_explore)
    val finalCheckMessage = debugMessage?.contains(Regex("成功|失败")) == true
    val moveModifier = if (canMove) {
        Modifier.pointerInput(source.bookSourceUrl, sources) {
            var initialIndex = -1
            var targetIndex = -1
            var dragY = 0f
            var validDrag = false
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    initialIndex = sources.indexOfFirst { it.bookSourceUrl == source.bookSourceUrl }
                    val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == source.bookSourceUrl }
                    if (initialIndex >= 0 && visibleItem != null) {
                        targetIndex = initialIndex
                        dragY = visibleItem.offset.toFloat() + point.y
                        validDrag = true
                    }
                },
                onDragEnd = {
                    if (validDrag && targetIndex != initialIndex) {
                        val movedDown = targetIndex > initialIndex
                        val neighborIndex = if (movedDown) targetIndex - 1 else targetIndex + 1
                        sources.getOrNull(neighborIndex)?.let { target ->
                            onMove(source.bookSourceUrl, target.bookSourceUrl, movedDown) {}
                        }
                    }
                    validDrag = false
                },
                onDragCancel = { validDrag = false }
            ) { change, dragAmount ->
                if (!validDrag) return@detectDragGesturesAfterLongPress
                change.consume()
                dragY += dragAmount.y
                targetIndex = listState.layoutInfo.visibleItemsInfo
                    .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragY) }
                    ?.index?.coerceIn(sources.indices) ?: targetIndex
            }
        }
    } else Modifier

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp).then(moveModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = onSelected)
            Text(
                text = source.getDisPlayNameGroup(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Switch(
                checked = source.enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = enableLabel }
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
            }
            Box {
                IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                }
                if (source.hasExploreUrl) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (source.enabledExplore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.search)) }, onClick = { onDismissMenu(); onSearch() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.debug)) }, onClick = { onDismissMenu(); onDebug() })
                    if (canMove) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.to_top)) }, onClick = { onDismissMenu(); onTop() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.to_bottom)) }, onClick = { onDismissMenu(); onBottom() })
                    }
                    if (source.hasLoginUrl) {
                        DropdownMenuItem(
                            text = { Text(loginLabel) },
                            onClick = { onDismissMenu(); onLogin() }
                        )
                    }
                    if (source.hasExploreUrl) {
                        DropdownMenuItem(
                            text = { Text(enableExploreLabel) },
                            onClick = { onDismissMenu(); onExplore(!source.enabledExplore) }
                        )
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { onDismissMenu(); onDelete() })
                }
            }
        }
        if (!debugMessage.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 58.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = debugMessage,
                    modifier = Modifier.weight(1f),
                    color = if (finalCheckMessage && debugMessage.contains("失败")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isChecking && !finalCheckMessage) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}
