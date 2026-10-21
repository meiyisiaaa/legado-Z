package io.legado.app.ui.book.group

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.compose.LegadoTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun GroupManageContent(
    groups: List<BookGroup>,
    onAdd: () -> Unit,
    onEdit: (BookGroup) -> Unit,
    onShowChange: (BookGroup, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(0f) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_manage)) },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyGroupState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(groups.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                point.y >= it.offset && point.y <= it.offset + it.size
                            }
                            draggingIndex = hit?.index ?: -1
                            dragPosition = point.y
                        },
                        onDragEnd = {
                            if (draggingIndex >= 0) onDragFinished()
                            draggingIndex = -1
                        },
                        onDragCancel = {
                            if (draggingIndex >= 0) onDragFinished()
                            draggingIndex = -1
                        }
                    ) { change, amount ->
                        change.consume()
                        if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                        dragPosition += amount.y
                        val target = listState.layoutInfo.visibleItemsInfo
                            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragPosition) }
                            ?.index ?: draggingIndex
                        if (target != draggingIndex && target in groups.indices) {
                            onMove(draggingIndex, target)
                            draggingIndex = target
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(groups, key = { _, item -> item.groupId }) { index, group ->
                    GroupManageRow(
                        group = group,
                        isDragging = draggingIndex == index,
                        onEdit = { onEdit(group) },
                        onShowChange = { onShowChange(group, it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun GroupSelectContent(
    groups: List<BookGroup>,
    selectedGroupId: Long,
    onSelectedGroupIdChange: (Long) -> Unit,
    onAdd: () -> Unit,
    onEdit: (BookGroup) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(0f) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_select)) },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            EmptyGroupState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(groups.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                point.y >= it.offset && point.y <= it.offset + it.size
                            }
                            draggingIndex = hit?.index ?: -1
                            dragPosition = point.y
                        },
                        onDragEnd = {
                            if (draggingIndex >= 0) onDragFinished()
                            draggingIndex = -1
                        },
                        onDragCancel = {
                            if (draggingIndex >= 0) onDragFinished()
                            draggingIndex = -1
                        }
                    ) { change, amount ->
                        change.consume()
                        if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                        dragPosition += amount.y
                        val target = listState.layoutInfo.visibleItemsInfo
                            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragPosition) }
                            ?.index ?: draggingIndex
                        if (target != draggingIndex && target in groups.indices) {
                            onMove(draggingIndex, target)
                            draggingIndex = target
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(groups, key = { _, item -> item.groupId }) { index, group ->
                    GroupSelectRow(
                        group = group,
                        selected = (selectedGroupId and group.groupId) > 0,
                        isDragging = draggingIndex == index,
                        onSelected = { checked ->
                            onSelectedGroupIdChange(
                                if (checked) selectedGroupId + group.groupId else selectedGroupId - group.groupId
                            )
                        },
                        onEdit = { onEdit(group) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupManageRow(
    group: BookGroup,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onShowChange: (Boolean) -> Unit
) {
    val displayName = group.getManageName(LocalContext.current)
    val enableLabel = stringResource(R.string.enable)
    val showState = stringResource(if (group.show) R.string.enabled else R.string.disabled)
    val editLabel = stringResource(R.string.edit)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.sort),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (group.show) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = "$displayName, $editLabel" }
            ) { Text(editLabel) }
            Switch(
                checked = group.show,
                onCheckedChange = onShowChange,
                modifier = Modifier.semantics {
                    contentDescription = "$displayName, $enableLabel"
                    stateDescription = showState
                }
            )
        }
    }
}

@Composable
private fun GroupSelectRow(
    group: BookGroup,
    selected: Boolean,
    isDragging: Boolean,
    onSelected: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onSelected(!selected) }.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val editLabel = stringResource(R.string.edit)
            Checkbox(
                checked = selected,
                onCheckedChange = onSelected,
                modifier = Modifier.semantics { contentDescription = group.groupName }
            )
            Text(group.groupName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = "${group.groupName}, $editLabel" }
            ) { Text(editLabel) }
        }
    }
}

@Composable
internal fun GroupEditContent(
    initialName: String,
    initialSort: Int,
    initialEnableRefresh: Boolean,
    initialOnlyRead: Boolean,
    canDelete: Boolean,
    onCoverClick: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, Int, Boolean, Boolean) -> Unit,
    coverPreview: @Composable () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val sorts = stringArrayResource(R.array.book_sort)
    val sortLabel = stringResource(R.string.sort)
    val selectImageLabel = stringResource(R.string.select_image)
    val safeInitialSort = initialSort.takeIf { it + 1 in sorts.indices } ?: -1
    var sort by remember(initialSort) { mutableIntStateOf(safeInitialSort) }
    var enableRefresh by remember(initialEnableRefresh) { mutableStateOf(initialEnableRefresh) }
    var onlyUpdateRead by remember(initialOnlyRead) { mutableStateOf(initialOnlyRead) }
    var sortExpanded by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 620.dp).background(MaterialTheme.colorScheme.surface)
            .imePadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (canDelete) stringResource(R.string.group_edit) else stringResource(R.string.add_group),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.group_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCancel) {
                Icon(painterResource(R.drawable.ic_baseline_close), contentDescription = stringResource(R.string.cancel))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier.size(width = 90.dp, height = 120.dp)
                    .clickable(onClick = onCoverClick)
                    .semantics {
                        contentDescription = selectImageLabel
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) { coverPreview() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    shape = RoundedCornerShape(14.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sortLabel, style = MaterialTheme.typography.labelLarge)
                    val selectedSort = sorts.getOrElse(sort + 1) { sorts.first() }
                    Box {
                        TextButton(
                            onClick = { sortExpanded = true },
                            modifier = Modifier.semantics { contentDescription = "$sortLabel, $selectedSort" }
                        ) {
                            Text(selectedSort, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            sorts.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { sort = index - 1; sortExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
        }
        GroupCheckRow(
            title = stringResource(R.string.allow_drop_down_refresh),
            checked = enableRefresh,
            onCheckedChange = { enableRefresh = it }
        )
        GroupCheckRow(
            title = stringResource(R.string.only_update_read),
            checked = onlyUpdateRead,
            onCheckedChange = { onlyUpdateRead = it }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canDelete) {
                TextButton(onClick = { deleteConfirm = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            TextButton(
                onClick = { onSave(name, sort, enableRefresh, onlyUpdateRead) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.ok)) }
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.sure_del)) },
            confirmButton = {
                TextButton(onClick = { deleteConfirm = false; onDelete() }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text(stringResource(R.string.no)) } }
        )
    }
}

@Composable
private fun GroupCheckRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onCheckedChange(!checked) }.padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title }
        )
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyGroupState(modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupManageContentPreview() {
    LegadoTheme {
        GroupManageContent(
            groups = listOf(BookGroup(groupId = 1L, groupName = "稍后阅读", show = true)),
            onAdd = {}, onEdit = {}, onShowChange = { _, _ -> }, onMove = { _, _ -> },
            onDragFinished = {}, onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupSelectContentPreview() {
    LegadoTheme {
        GroupSelectContent(
            groups = listOf(BookGroup(groupId = 1L, groupName = "稍后阅读")),
            selectedGroupId = 1L,
            onSelectedGroupIdChange = {}, onAdd = {}, onEdit = {}, onMove = { _, _ -> },
            onDragFinished = {}, onCancel = {}, onConfirm = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupEditContentPreview() {
    LegadoTheme {
        GroupEditContent(
            initialName = "稍后阅读",
            initialSort = -1,
            initialEnableRefresh = true,
            initialOnlyRead = false,
            canDelete = true,
            onCoverClick = {}, onCancel = {}, onDelete = {}, onSave = { _, _, _, _ -> },
            coverPreview = { Text("封面") }
        )
    }
}
