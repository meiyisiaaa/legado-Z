package io.legado.app.ui.rss.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewpager.widget.ViewPager
import io.legado.app.R

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RssFavoritesContent(
    groups: List<String>,
    selectedIndex: Int,
    pager: ViewPager,
    deleteDialog: Int?,
    onBack: () -> Unit,
    onSelectGroup: (Int) -> Unit,
    onRequestDeleteGroup: () -> Unit,
    onRequestDeleteAll: () -> Unit,
    onConfirmDeleteGroup: () -> Unit,
    onConfirmDeleteAll: () -> Unit,
    onDismissDialog: () -> Unit
) {
    var groupMenuExpanded by remember { mutableStateOf(false) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.favorites), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { groupMenuExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_groups), contentDescription = stringResource(R.string.group))
                    }
                    DropdownMenu(expanded = groupMenuExpanded, onDismissRequest = { groupMenuExpanded = false }) {
                        groups.forEachIndexed { index, group ->
                            DropdownMenuItem(
                                text = { Text(group) },
                                onClick = { groupMenuExpanded = false; onSelectGroup(index) }
                            )
                        }
                    }
                    IconButton(onClick = { actionMenuExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = actionMenuExpanded, onDismissRequest = { actionMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_select_group)) }, onClick = {
                            actionMenuExpanded = false
                            onRequestDeleteGroup()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_all)) }, onClick = {
                            actionMenuExpanded = false
                            onRequestDeleteAll()
                        })
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (groups.size > 1) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groups.forEachIndexed { index, group ->
                        val selected = index == selectedIndex
                        Surface(
                            modifier = Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelectGroup(index) }
                            ),
                            shape = RoundedCornerShape(20.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                group,
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(28.dp)) {
                    Text(stringResource(R.string.rss_favorite_groups_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.rss_favorite_groups_empty_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                AndroidView(factory = { pager }, modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    when (deleteDialog) {
        1 -> AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text(stringResource(R.string.draw)) },
            text = { Text("${stringResource(R.string.sure_del)}\n<${groups.getOrNull(selectedIndex).orEmpty()}>${stringResource(R.string.group)}") },
            confirmButton = { TextButton(onClick = onConfirmDeleteGroup) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = onDismissDialog) { Text(stringResource(R.string.no)) } }
        )
        2 -> AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text(stringResource(R.string.draw)) },
            text = { Text("${stringResource(R.string.sure_del)}\n<${stringResource(R.string.all)}> ${stringResource(R.string.favorite)}") },
            confirmButton = { TextButton(onClick = onConfirmDeleteAll) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = onDismissDialog) { Text(stringResource(R.string.no)) } }
        )
    }
}
