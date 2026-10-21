package io.legado.app.ui.dict.rule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.legado.app.data.entities.DictRule

@Composable
internal fun DictRuleContent(
    rules: List<DictRule>,
    selectedNames: List<String>,
    onSelect: (DictRule, Boolean) -> Unit,
    onToggle: (DictRule, Boolean) -> Unit,
    onEdit: (DictRule) -> Unit,
    onDelete: (DictRule) -> Unit,
    onMove: (DictRule, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rules.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("暂无字典规则", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        itemsIndexed(rules, key = { _, rule -> rule.name }) { index, rule ->
            var menuExpanded by remember(rule.name) { mutableStateOf(false) }
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
                        checked = rule.name in selectedNames,
                        onCheckedChange = { onSelect(rule, it) },
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = rule.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggle(rule, it) }
                    )
                    IconButton(onClick = { onEdit(rule) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = stringResource(R.string.edit)
                        )
                    }
                    IconButton(onClick = { onDelete(rule) }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_outline_delete),
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp)
                        ) {
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
                        }
                    }
                }
                if (index != rules.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
