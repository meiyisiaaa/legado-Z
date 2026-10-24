package io.legado.app.ui.rss.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewpager.widget.ViewPager
import io.legado.app.R

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun RssSortContent(
    title: String,
    sorts: List<Pair<String, String>>,
    selectedIndex: Int,
    pager: ViewPager,
    hasSearch: Boolean,
    hasLogin: Boolean,
    onBack: () -> Unit,
    onSortSelected: (Int) -> Unit,
    onAction: (Int) -> Unit,
    onSearch: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val actions = buildList {
        if (hasLogin) add(R.id.menu_login to R.string.login)
        add(R.id.menu_refresh_sort to R.string.refresh_sort)
        add(R.id.menu_set_source_variable to R.string.set_source_variable)
        add(R.id.menu_edit_source to R.string.edit_source)
        add(R.id.menu_switch_layout to R.string.switchLayout)
        add(R.id.menu_read_record to R.string.read_record)
        add(R.id.menu_clear to R.string.clear)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                        }
                    },
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (hasSearch) {
                            IconButton(onClick = { searchExpanded = !searchExpanded; if (!searchExpanded) query = "" }) {
                                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search))
                            }
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            actions.forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                    menuExpanded = false
                                    onAction(id)
                                })
                            }
                        }
                    }
                )
                if (searchExpanded) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search)) },
                        trailingIcon = {
                            TextButton(onClick = { onSearch(query); searchExpanded = false }) { Text(stringResource(R.string.search)) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query); searchExpanded = false })
                    )
                }
                if (sorts.size > 1) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FlowRow(
                            modifier = Modifier.selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            sorts.forEachIndexed { index, sort ->
                                val selected = index == selectedIndex
                                Surface(
                                    modifier = Modifier.selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onSortSelected(index) }
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = sort.first.ifBlank { stringResource(R.string.all) },
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            AndroidView(
                factory = { pager },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}
