package io.legado.app.ui.book.read

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.ReplaceRule

@Composable
fun EffectiveReplacesContent(
    rules: List<ReplaceRule>,
    isChineseConverterEnabled: Boolean,
    chineseConverterType: Int,
    chineseModes: List<String>,
    onRuleClick: (ReplaceRule) -> Unit,
    onRuleDisable: (ReplaceRule) -> Unit,
    onChineseModeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showChineseModes by remember { mutableStateOf(false) }
    val contentMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = contentMaxHeight)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.effective_replaces),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_close),
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (rules.isEmpty() && !isChineseConverterEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(rules, key = { "replace-${it.id}" }) { rule ->
                    ReplaceRuleRow(
                        rule = rule,
                        onClick = {
                            if (rule.id == 0L) showChineseModes = true else onRuleClick(rule)
                        },
                        onDisable = {
                            if (rule.id == 0L) onChineseModeSelected(0) else onRuleDisable(rule)
                        }
                    )
                }
            }
        }
    }
    if (showChineseModes) {
        AlertDialog(
            onDismissRequest = { showChineseModes = false },
            title = { Text(stringResource(R.string.chinese_converter)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    chineseModes.forEachIndexed { index, mode ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showChineseModes = false
                                    onChineseModeSelected(index)
                                },
                            color = if (chineseConverterType == index)
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = mode,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChineseModes = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ReplaceRuleRow(
    rule: ReplaceRule,
    onClick: () -> Unit,
    onDisable: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = rule.name,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDisable) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_delete),
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
