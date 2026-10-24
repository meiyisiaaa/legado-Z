package io.legado.app.ui.widget.keyboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun KeyboardAssistsConfigContent(
    assists: List<KeyboardAssist>,
    lineCount: Int,
    editorVisible: Boolean,
    editingAssist: KeyboardAssist?,
    onClose: () -> Unit,
    onChooseLineCount: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (KeyboardAssist) -> Unit,
    onDelete: (KeyboardAssist) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    onEditDismiss: () -> Unit,
    onEditSave: (String, String) -> Unit
) {
    val listState = rememberLazyListState()
    val currentAssists = rememberUpdatedState(assists)
    val motion = LocalLegadoMotion.current
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(0f) }
    var orderChanged by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(R.drawable.ic_baseline_close),
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                title = {
                    Column(
                        Modifier.clickable(role = Role.Button, onClick = onChooseLineCount),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.assists_key_config),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.show_line_number, lineCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.add)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        if (assists.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(assists.size) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { point ->
                            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                point.y >= it.offset && point.y <= it.offset + it.size
                            }
                            draggingIndex = item?.index?.takeIf { it in currentAssists.value.indices } ?: -1
                            dragPosition = point.y
                            orderChanged = false
                        },
                        onDragEnd = {
                            if (orderChanged) onDragFinished()
                            draggingIndex = -1
                            orderChanged = false
                        },
                        onDragCancel = {
                            if (orderChanged) onDragFinished()
                            draggingIndex = -1
                            orderChanged = false
                        }
                    ) { change, amount ->
                        change.consume()
                        if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                        dragPosition += amount.y
                        val targetIndex = listState.layoutInfo.visibleItemsInfo
                            .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragPosition) }
                            ?.index ?: draggingIndex
                        if (targetIndex !in currentAssists.value.indices || targetIndex == draggingIndex) {
                            return@detectDragGesturesAfterLongPress
                        }
                        onMove(draggingIndex, targetIndex)
                        draggingIndex = targetIndex
                        orderChanged = true
                    }
                },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = assists,
                    key = { _, assist -> "${assist.type}:${assist.key}" }
                ) { index, assist ->
                    KeyboardAssistRow(
                        assist = assist,
                        isDragging = index == draggingIndex,
                        onEdit = { onEdit(assist) },
                        onDelete = { onDelete(assist) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(motion.state),
                            fadeOutSpec = tween(motion.state),
                            placementSpec = tween(motion.state)
                        )
                    )
                }
            }
        }
    }

    if (editorVisible) {
        KeyboardAssistEditDialog(
            original = editingAssist,
            onDismiss = onEditDismiss,
            onSave = onEditSave
        )
    }
}

@Composable
private fun KeyboardAssistRow(
    assist: KeyboardAssist,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalLegadoMotion.current
    val containerColor by animateColorAsState(
        targetValue = if (isDragging) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(motion.press),
        label = "keyboardAssistDrag"
    )
    Column(modifier.fillMaxWidth().background(containerColor)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.sort),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 12.dp).size(20.dp)
            )
            Text(
                text = assist.key,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_clear_all),
                    contentDescription = "${stringResource(R.string.delete)} ${assist.key}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun KeyboardAssistEditDialog(
    original: KeyboardAssist?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var key by rememberSaveable(original?.serialNo, original?.key) { mutableStateOf(original?.key.orEmpty()) }
    var value by rememberSaveable(original?.serialNo, original?.value) { mutableStateOf(original?.value.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LegadoDialogHeader(title = "辅助按键", onClose = onDismiss) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("key") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("value") },
                    minLines = 3,
                    maxLines = 7,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key, value) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Preview(showBackground = true)
@Composable
private fun KeyboardAssistsConfigPreview() {
    LegadoTheme {
        KeyboardAssistsConfigContent(
            assists = listOf(
                KeyboardAssist(key = "第一项", value = "可插入的辅助文本"),
                KeyboardAssist(key = "章节标记", value = "第\\d+章")
            ),
            lineCount = 3,
            editorVisible = false,
            editingAssist = null,
            onClose = {},
            onChooseLineCount = {},
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onMove = { _, _ -> },
            onDragFinished = {},
            onEditDismiss = {},
            onEditSave = { _, _ -> }
        )
    }
}
