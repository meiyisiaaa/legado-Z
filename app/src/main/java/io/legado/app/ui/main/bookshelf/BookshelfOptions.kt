package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal data class BookshelfOptions(
    val groupStyle: Int, val layout: Int, val sort: Int, val bookName: Int,
    val margin: Int, val unread: Boolean, val lastUpdate: Boolean,
    val updateCount: Boolean, val fastScroll: Boolean
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BookshelfOptionsContent(initial: BookshelfOptions, onChange: (BookshelfOptions) -> Unit) {
    var options by remember { mutableStateOf(initial) }
    fun update(value: BookshelfOptions) { options = value; onChange(value) }
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupStyleField(stringResource(R.string.group_style), stringArrayResource(R.array.group_style).toList(),
            options.groupStyle) { update(options.copy(groupStyle = it)) }
        ToggleField(stringResource(R.string.show_unread), options.unread) { update(options.copy(unread = it)) }
        ToggleField(stringResource(R.string.show_last_update_time), options.lastUpdate) { update(options.copy(lastUpdate = it)) }
        ToggleField(stringResource(R.string.show_wait_up_count), options.updateCount) { update(options.copy(updateCount = it)) }
        ToggleField(stringResource(R.string.show_bookshelf_fast_scroller), options.fastScroll) { update(options.copy(fastScroll = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioChoices(stringResource(R.string.view), listOf(R.string.layout_list, R.string.layout_list_compact,
                R.string.layout_grid2, R.string.layout_grid3, R.string.layout_grid4, R.string.layout_grid5,
                R.string.layout_grid6).map { stringResource(it) }, options.layout, Modifier.weight(1f)) {
                update(options.copy(layout = it))
            }
            RadioChoices(stringResource(R.string.sort), listOf(R.string.bookshelf_px_0, R.string.bookshelf_px_1,
                R.string.bookshelf_px_2, R.string.bookshelf_px_3, R.string.bookshelf_px_4, R.string.bookshelf_px_5)
                .map { stringResource(it) }, options.sort, Modifier.weight(1f)) { update(options.copy(sort = it)) }
        }
        if (options.layout >= 2) {
            Text(stringResource(R.string.book_name), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            FlowRow(Modifier.fillMaxWidth().selectableGroup()) {
                listOf(R.string.show, R.string.hide, R.string.overlay).forEachIndexed { index, label ->
                    RadioChoice(stringResource(label), index == options.bookName) { update(options.copy(bookName = index)) }
                }
            }
        }
        Text("${stringResource(R.string.margin)}  ${options.margin}", style = MaterialTheme.typography.titleSmall)
        Slider(options.margin.toFloat(), { update(options.copy(margin = it.toInt())) },
            modifier = Modifier.semantics { contentDescription = "书架间距" }, valueRange = 0f..60f, steps = 59)
    }
}

@Composable
private fun GroupStyleField(label: String, choices: List<String>, selected: Int, onChoose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton(onClick = { expanded = true }, modifier = Modifier
                .semantics { contentDescription = "$label：${choices.getOrElse(selected) { choices.first() }}" }) {
                Text(choices.getOrElse(selected) { choices.first() })
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                choices.forEachIndexed { index, choice ->
                    DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onChoose(index) })
                }
            }
        }
    }
}

@Composable
private fun RadioChoices(label: String, choices: List<String>, selected: Int,
                         modifier: Modifier = Modifier, onChoose: (Int) -> Unit) {
    Column(modifier.selectableGroup()) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        choices.forEachIndexed { index, choice ->
            RadioChoice(choice, index == selected, Modifier.fillMaxWidth()) { onChoose(index) }
        }
    }
}

@Composable
private fun RadioChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onChoose: () -> Unit) {
    Row(modifier.heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onChoose)
        .padding(end = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToggleField(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null)
    }
}
