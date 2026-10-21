package io.legado.app.ui.rss.subscription

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.RuleSub

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun RuleSubContent(
    subscriptions: List<RuleSub>,
    editing: RuleSub?,
    typeLabels: List<String>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (RuleSub) -> Unit,
    onEdit: (RuleSub) -> Unit,
    onDelete: (RuleSub) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    onSave: (RuleSub) -> Unit,
    onDismissEditor: () -> Unit
) {
    var menuTarget by remember { mutableStateOf<Long?>(null) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.rule_subscription)) },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                    }
                }
            )
        }
    ) { padding ->
        if (subscriptions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(28.dp), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.rule_sub_empty_msg), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(subscriptions.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                point.y >= it.offset && point.y <= it.offset + it.size
                            }
                            draggingIndex = item?.index ?: -1
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
                    ) { change, dragAmount ->
                        change.consume()
                        if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                        dragPosition += dragAmount.y
                        val targetIndex = listState.layoutInfo.visibleItemsInfo
                            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragPosition) }
                            ?.index ?: draggingIndex
                        if (targetIndex != draggingIndex && targetIndex in subscriptions.indices) {
                            onMove(draggingIndex, targetIndex)
                            draggingIndex = targetIndex
                        }
                    }
                },
            ) {
                itemsIndexed(subscriptions, key = { _, item -> item.id }) { index, item ->
                    RuleSubCard(
                        item = item,
                        typeLabel = typeLabels.getOrElse(item.type) { typeLabels.firstOrNull().orEmpty() },
                        menuExpanded = menuTarget == item.id,
                        isDragging = draggingIndex == index,
                        canMoveUp = index > 0,
                        canMoveDown = index < subscriptions.lastIndex,
                        onOpen = { onOpen(item) },
                        onEdit = { onEdit(item) },
                        onMoveUp = { onMove(index, index - 1); onDragFinished() },
                        onMoveDown = { onMove(index, index + 1); onDragFinished() },
                        onMenu = { menuTarget = item.id },
                        onDismissMenu = { menuTarget = null },
                        onDelete = { menuTarget = null; onDelete(item) }
                    )
                }
            }
        }
    }
    editing?.let { RuleSubEditor(it, typeLabels, onSave, onDismissEditor) }
}

@Composable
private fun RuleSubCard(
    item: RuleSub,
    typeLabel: String,
    menuExpanded: Boolean,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onDelete: () -> Unit
) {
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val accessibilityActions = buildList {
        if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
        if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
    }
    Row(
        Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { customActions = accessibilityActions }
            .background(if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        typeLabel,
                        Modifier.clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(item.url, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = onMenu) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = onDelete)
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSubEditor(
    initial: RuleSub,
    typeLabels: List<String>,
    onSave: (RuleSub) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember(initial.id) { mutableIntStateOf(initial.type.coerceIn(0, (typeLabels.size - 1).coerceAtLeast(0))) }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var url by remember(initial.id) { mutableStateOf(initial.url) }
    var autoUpdate by remember(initial.id) { mutableStateOf(initial.autoUpdate) }
    var silentUpdate by remember(initial.id) { mutableStateOf(initial.silentUpdate) }
    var interval by remember(initial.id) { mutableStateOf(initial.updateInterval.takeIf { it > 0 }?.toString().orEmpty()) }
    var typeMenu by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_subscription)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.book_type))
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { typeMenu = true }) { Text(typeLabels.getOrElse(type) { typeLabels.firstOrNull().orEmpty() }) }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            typeLabels.forEachIndexed { index, label ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { type = index; typeMenu = false })
                            }
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = autoUpdate,
                        role = Role.Checkbox,
                        onValueChange = {
                            autoUpdate = it
                            if (it && (interval.toIntOrNull() ?: 0) == 0) interval = "24"
                            if (!it) {
                                interval = "0"
                                silentUpdate = false
                            }
                        }
                    )
                ) {
                    Checkbox(checked = autoUpdate, onCheckedChange = null)
                    Text(stringResource(R.string.auto_update), modifier = Modifier.weight(1f))
                }
                val silentUpdateEnabled = autoUpdate && interval.toIntOrNull() != 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = silentUpdate,
                        enabled = silentUpdateEnabled,
                        role = Role.Checkbox,
                        onValueChange = { silentUpdate = it }
                    )
                ) {
                    Checkbox(
                        checked = silentUpdate,
                        enabled = silentUpdateEnabled,
                        onCheckedChange = null
                    )
                    Text(stringResource(R.string.silent_update), modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.update_interval))
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { value ->
                            interval = value.filter(Char::isDigit)
                            if (interval.toIntOrNull() == 0) {
                                silentUpdate = false
                                autoUpdate = false
                            } else if (autoUpdate) {
                                // A positive interval is required before silent updates can be enabled.
                            }
                        },
                        enabled = autoUpdate,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Text(stringResource(R.string.time_hour))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(
                    type = type,
                    name = name,
                    url = url,
                    autoUpdate = autoUpdate,
                    silentUpdate = silentUpdate,
                    updateInterval = interval.toIntOrNull() ?: 0
                ))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
