package io.legado.app.ui.book.toc.rule

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.ui.widget.compose.LegadoTheme

internal enum class TxtTocRuleImportAction { Local, Online, QrCode, Default, Help }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TxtTocRuleManageContent(
    rules: List<TxtTocRule>,
    selectedIds: Set<Long>,
    onAdd: () -> Unit,
    onImport: (TxtTocRuleImportAction) -> Unit,
    onSelectionChange: (Long, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onReverseSelection: () -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onDeleteSelected: () -> Unit,
    onEnableSelected: () -> Unit,
    onDisableSelected: () -> Unit,
    onExportSelected: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    onBack: () -> Unit
) {
    var importExpanded by remember { mutableStateOf(false) }
    val allSelected = rules.isNotEmpty() && selectedIds.size >= rules.size
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.txt_toc_rule)) },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                    }
                    Box {
                        IconButton(onClick = { importExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_import), contentDescription = stringResource(R.string.import_local))
                        }
                        RuleImportMenu(importExpanded, { importExpanded = false }, onImport)
                    }
                }
            )
        },
        bottomBar = {
            RuleSelectionBar(
                selectedCount = selectedIds.size,
                totalCount = rules.size,
                allSelected = allSelected,
                onSelectAll = onSelectAll,
                onReverse = onReverseSelection,
                onDelete = onDeleteSelected,
                onEnable = onEnableSelected,
                onDisable = onDisableSelected,
                onExport = onExportSelected
            )
        }
    ) { padding ->
        TxtTocRuleList(
            rules = rules,
            selectedIds = selectedIds,
            selectedName = null,
            selectionMode = true,
            modifier = Modifier.fillMaxSize().padding(padding),
            onSelectionChange = onSelectionChange,
            onSelectName = {},
            onToggleEnabled = onToggleEnabled,
            onEdit = onEdit,
            onDelete = onDelete,
            onMove = onMove,
            onDragFinished = onDragFinished
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TxtTocRulePickerContent(
    rules: List<TxtTocRule>,
    selectedName: String?,
    onSelectedNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: (TxtTocRuleImportAction) -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    var importExpanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_toc_rule)) },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add))
                    }
                    Box {
                        IconButton(onClick = { importExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_import), contentDescription = stringResource(R.string.import_local))
                        }
                        RuleImportMenu(importExpanded, { importExpanded = false }, onImport)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onConfirm, enabled = rules.any { it.name == selectedName }) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    ) { padding ->
        TxtTocRuleList(
            rules = rules,
            selectedIds = emptySet(),
            selectedName = selectedName,
            selectionMode = false,
            modifier = Modifier.fillMaxSize().padding(padding),
            onSelectionChange = { _, _ -> },
            onSelectName = onSelectedNameChange,
            onToggleEnabled = onToggleEnabled,
            onEdit = onEdit,
            onDelete = onDelete,
            onMove = onMove,
            onDragFinished = onDragFinished
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TxtTocRuleList(
    rules: List<TxtTocRule>,
    selectedIds: Set<Long>,
    selectedName: String?,
    selectionMode: Boolean,
    modifier: Modifier,
    onSelectionChange: (Long, Boolean) -> Unit,
    onSelectName: (String) -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDragFinished: () -> Unit
) {
    val listState = rememberLazyListState()
    val currentSelectedIds = rememberUpdatedState(selectedIds)
    val currentRules = rememberUpdatedState(rules)
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragPosition by remember { mutableStateOf(0f) }
    var selectionDrag by remember { mutableStateOf<Boolean?>(null) }
    var selectionDragValue by remember { mutableStateOf(false) }
    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(rules.size, selectionMode) {
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        point.y >= it.offset && point.y <= it.offset + it.size
                    }
                    val index = item?.index?.takeIf { it in currentRules.value.indices } ?: -1
                    draggingIndex = index
                    dragPosition = point.y
                    if (selectionMode && point.x < size.width * 0.22f && index in currentRules.value.indices) {
                        selectionDrag = true
                        selectionDragValue = currentRules.value[index].id !in currentSelectedIds.value
                        onSelectionChange(currentRules.value[index].id, selectionDragValue)
                    } else {
                        selectionDrag = false
                    }
                },
                onDragEnd = {
                    if (selectionDrag != true && draggingIndex >= 0) onDragFinished()
                    draggingIndex = -1
                    selectionDrag = null
                },
                onDragCancel = {
                    if (selectionDrag != true && draggingIndex >= 0) onDragFinished()
                    draggingIndex = -1
                    selectionDrag = null
                }
            ) { change, amount ->
                change.consume()
                if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                dragPosition += amount.y
                val targetIndex = listState.layoutInfo.visibleItemsInfo
                    .minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - dragPosition) }
                    ?.index ?: draggingIndex
                if (targetIndex !in currentRules.value.indices || targetIndex == draggingIndex) return@detectDragGesturesAfterLongPress
                if (selectionDrag == true) {
                    onSelectionChange(currentRules.value[targetIndex].id, selectionDragValue)
                    draggingIndex = targetIndex
                } else {
                    onMove(draggingIndex, targetIndex)
                    draggingIndex = targetIndex
                }
            }
        },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (rules.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
            TxtTocRuleRow(
                rule = rule,
                checked = if (selectionMode) rule.id in selectedIds else rule.name == selectedName,
                selectionMode = selectionMode,
                isDragging = index == draggingIndex && selectionDrag != true,
                onCheckedChange = { checked ->
                    if (selectionMode) onSelectionChange(rule.id, checked) else if (checked) onSelectName(rule.name)
                },
                onToggleEnabled = { onToggleEnabled(rule, it) },
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
                onMoveTop = { onMove(index, 0); onDragFinished() },
                onMoveBottom = { onMove(index, rules.lastIndex); onDragFinished() }
            )
        }
    }
}

@Composable
private fun TxtTocRuleRow(
    rule: TxtTocRule,
    checked: Boolean,
    selectionMode: Boolean,
    isDragging: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit
) {
    var menuExpanded by remember(rule.id) { mutableStateOf(false) }
    val enableLabel = stringResource(R.string.enable)
    val enabledState = stringResource(if (rule.enable) R.string.enabled else R.string.disabled)
    val editLabel = stringResource(R.string.edit)
    val moreLabel = stringResource(R.string.more_menu)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.semantics { contentDescription = rule.name }
                )
            } else {
                RadioButton(
                    selected = checked,
                    onClick = { onCheckedChange(true) },
                    modifier = Modifier.semantics { contentDescription = rule.name }
                )
            }
            Column(Modifier.weight(1f).clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!rule.example.isNullOrBlank()) {
                    Text(rule.example.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Switch(
                checked = rule.enable,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.semantics {
                    contentDescription = "${rule.name}, $enableLabel"
                    stateDescription = enabledState
                }
            )
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = "${rule.name}, $editLabel" }
            ) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = null)
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = "${rule.name}, $moreLabel" }
                ) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.to_top)) }, onClick = { menuExpanded = false; onMoveTop() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.to_bottom)) }, onClick = { menuExpanded = false; onMoveBottom() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun RuleImportMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onImport: (TxtTocRuleImportAction) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text(stringResource(R.string.import_local)) }, onClick = { onDismiss(); onImport(TxtTocRuleImportAction.Local) })
        DropdownMenuItem(text = { Text(stringResource(R.string.import_on_line)) }, onClick = { onDismiss(); onImport(TxtTocRuleImportAction.Online) })
        DropdownMenuItem(text = { Text(stringResource(R.string.import_by_qr_code)) }, onClick = { onDismiss(); onImport(TxtTocRuleImportAction.QrCode) })
        DropdownMenuItem(text = { Text(stringResource(R.string.import_default_rule)) }, onClick = { onDismiss(); onImport(TxtTocRuleImportAction.Default) })
        DropdownMenuItem(text = { Text(stringResource(R.string.help)) }, onClick = { onDismiss(); onImport(TxtTocRuleImportAction.Help) })
    }
}

@Composable
private fun RuleSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onReverse: () -> Unit,
    onDelete: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onExport: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectAllLabel = stringResource(R.string.select_all)
    Surface(Modifier.fillMaxWidth().navigationBarsPadding(), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = onSelectAll,
                enabled = totalCount > 0,
                modifier = Modifier.semantics { contentDescription = selectAllLabel }
            )
            Text(
                text = stringResource(if (allSelected) R.string.select_cancel_count else R.string.select_all_count, selectedCount, totalCount),
                modifier = Modifier.weight(1f).clickable(enabled = totalCount > 0) { onSelectAll(!allSelected) },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onReverse, enabled = selectedCount > 0 || totalCount > 0) { Text(stringResource(R.string.revert_selection)) }
            Box {
                IconButton(onClick = { menuExpanded = true }, enabled = selectedCount > 0) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.enable_selection)) }, onClick = { menuExpanded = false; onEnable() }, enabled = selectedCount > 0)
                    DropdownMenuItem(text = { Text(stringResource(R.string.disable_selection)) }, onClick = { menuExpanded = false; onDisable() }, enabled = selectedCount > 0)
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_selection)) }, onClick = { menuExpanded = false; onExport() }, enabled = selectedCount > 0)
                }
            }
            TextButton(onClick = onDelete, enabled = selectedCount > 0) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TxtTocRuleListPreview() {
    LegadoTheme {
        TxtTocRuleList(
            rules = listOf(TxtTocRule(name = "章节标题", rule = "第.+章", example = "第 12 章 山雨欲来")),
            selectedIds = emptySet(), selectedName = null, selectionMode = true, modifier = Modifier.fillMaxSize(),
            onSelectionChange = { _, _ -> }, onSelectName = {}, onToggleEnabled = { _, _ -> }, onEdit = {},
            onDelete = {}, onMove = { _, _ -> }, onDragFinished = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TxtTocRuleManagePreview() {
    LegadoTheme {
        TxtTocRuleManageContent(
            rules = listOf(TxtTocRule(id = 1, name = "章节标题", rule = "第(.+?)章", example = "第十二章")),
            selectedIds = setOf(1), onAdd = {}, onImport = {}, onSelectionChange = { _, _ -> },
            onSelectAll = {}, onReverseSelection = {}, onToggleEnabled = { _, _ -> }, onEdit = {},
            onDelete = {}, onDeleteSelected = {}, onEnableSelected = {}, onDisableSelected = {},
            onExportSelected = {}, onMove = { _, _ -> }, onDragFinished = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TxtTocRulePickerPreview() {
    LegadoTheme {
        TxtTocRulePickerContent(
            rules = listOf(TxtTocRule(id = 1, name = "章节标题", rule = "第(.+?)章", example = "第十二章")),
            selectedName = "章节标题", onSelectedNameChange = {}, onAdd = {}, onImport = {},
            onToggleEnabled = { _, _ -> }, onEdit = {}, onDelete = {}, onMove = { _, _ -> },
            onDragFinished = {}, onCancel = {}, onConfirm = {}
        )
    }
}
