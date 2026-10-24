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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
            Text(stringResource(R.string.empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
    ) {
        itemsIndexed(rules, key = { _, rule -> rule.name }) { index, rule ->
            var menuExpanded by remember(rule.name) { mutableStateOf(false) }
            val enableLabel = stringResource(R.string.enable)
            val enabledState = stringResource(if (rule.enabled) R.string.enabled else R.string.disabled)
            val editLabel = stringResource(R.string.edit)
            val deleteLabel = stringResource(R.string.delete)
            val moreLabel = stringResource(R.string.more_menu)
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
                        modifier = Modifier.size(48.dp).semantics { contentDescription = rule.name }
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
                        onCheckedChange = { onToggle(rule, it) },
                        modifier = Modifier.semantics {
                            contentDescription = "${rule.name}, $enableLabel"
                            stateDescription = enabledState
                        }
                    )
                    IconButton(
                        onClick = { onEdit(rule) },
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = "${rule.name}, $editLabel"
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = { onDelete(rule) },
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = "${rule.name}, $deleteLabel"
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_outline_delete),
                            contentDescription = null
                        )
                    }
                    androidx.compose.foundation.layout.Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = "${rule.name}, $moreLabel"
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = null
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_up)) },
                                enabled = index > 0,
                                onClick = { menuExpanded = false; onMove(rule, -1) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_down)) },
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
