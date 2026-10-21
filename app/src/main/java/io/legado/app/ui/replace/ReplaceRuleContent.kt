package io.legado.app.ui.replace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.ReplaceRule

@Composable
internal fun ReplaceRuleContent(
    rules: List<ReplaceRule>,
    selectedIds: List<Long>,
    onSelect: (ReplaceRule, Boolean) -> Unit,
    onToggle: (ReplaceRule, Boolean) -> Unit,
    onEdit: (ReplaceRule) -> Unit,
    onDelete: (ReplaceRule) -> Unit,
    onTop: (ReplaceRule) -> Unit,
    onBottom: (ReplaceRule) -> Unit,
    onMove: (ReplaceRule, Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (rules.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("暂无替换规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAdd) {
                Text(stringResource(R.string.add_replace_rule))
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
            var menuExpanded by remember(rule.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 60.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(
                        checked = rule.id in selectedIds,
                        onCheckedChange = { onSelect(rule, it) },
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = rule.getDisplayNameGroup(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle(rule, it) }
                    )
                    IconButton(onClick = { onEdit(rule) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = stringResource(R.string.edit)
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.more_menu)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("上移") },
                                enabled = index > 0,
                                onClick = { menuExpanded = false; onMove(rule, -1) }
                            )
                            DropdownMenuItem(
                                text = { Text("下移") },
                                enabled = index < rules.lastIndex,
                                onClick = { menuExpanded = false; onMove(rule, 1) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.to_top)) },
                                onClick = { menuExpanded = false; onTop(rule) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.to_bottom)) },
                                onClick = { menuExpanded = false; onBottom(rule) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = { menuExpanded = false; onDelete(rule) }
                            )
                        }
                    }
                }
                if (index != rules.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
