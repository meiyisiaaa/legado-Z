package io.legado.app.ui.book.source.edit

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addJsonPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.code.addLegadoPattern
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.ui.widget.text.TextInputLayout
import io.legado.app.utils.isTrue

internal data class SourceEditSwitch(val key: String, val label: String, val checked: Boolean)
internal data class SourceEditChoice(val key: String, val label: String, val options: List<String>, val selected: Int)
internal data class SourceEditAction(
    val id: Int,
    val labelRes: Int,
    val iconRes: Int? = null,
    val checked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceEditContent(
    title: String,
    tabs: List<String>,
    selectedTab: Int,
    entities: List<EditEntity>,
    switches: List<SourceEditSwitch>,
    choices: List<SourceEditChoice>,
    quickActions: List<SourceEditAction>,
    menuActions: List<SourceEditAction>,
    onBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onSwitchChange: (String, Boolean) -> Unit,
    onChoiceChange: (String, Int) -> Unit,
    onAction: (Int) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    quickActions.forEach { action ->
                        IconButton(onClick = { onAction(action.id) }) {
                            Icon(
                                painter = painterResource(action.iconRes ?: R.drawable.ic_more_vert),
                                contentDescription = stringResource(action.labelRes)
                            )
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menuActions.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text((if (action.checked) "✓  " else "") + stringResource(action.labelRes))
                                },
                                onClick = { menuOpen = false; onAction(action.id) }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (switches.isNotEmpty() || choices.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    choices.forEach { choice ->
                        SourceEditChoiceControl(choice, onChoiceChange)
                    }
                    switches.take(3).forEach { option ->
                        SourceEditToggle(option, onSwitchChange)
                    }
                }
                if (switches.size > 3) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        switches.drop(3).forEach { option ->
                            SourceEditToggle(option, onSwitchChange)
                        }
                    }
                }
            }
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = selectedTab == index, onClick = { onTabSelected(index) }, text = { Text(tab) })
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(entities, key = { it.key }) { entity ->
                    if (entity.viewType == EditEntity.ViewType.checkBox) {
                        var checked by remember(entity) { mutableStateOf(entity.value.isTrue()) }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .toggleable(value = checked, role = Role.Checkbox) {
                                    checked = it
                                    entity.value = it.toString()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(entity.hint, modifier = Modifier.weight(1f))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    } else {
                        SourceEditField(entity)
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SourceEditToggle(option: SourceEditSwitch, onSwitchChange: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier.toggleable(
            value = option.checked,
            role = Role.Checkbox,
            onValueChange = { onSwitchChange(option.key, it) }
        ).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = option.checked, onCheckedChange = null)
        Text(option.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun SourceEditChoiceControl(choice: SourceEditChoice, onChoiceChange: (String, Int) -> Unit) {
    var expanded by remember(choice.key) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(choice.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        androidx.compose.material3.TextButton(onClick = { expanded = true }) {
            Text(choice.options.getOrNull(choice.selected).orEmpty(), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choice.options.forEachIndexed { index, value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = { expanded = false; onChoiceChange(choice.key, index) }
                )
            }
        }
    }
}

private data class EditorBinding(val entity: EditEntity, val watcher: TextWatcher)

@Composable
private fun SourceEditField(entity: EditEntity) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        factory = { context -> createSourceEditor(context, entity) },
        update = { layout -> bindSourceEditor(layout, entity) }
    )
}

private fun createSourceEditor(context: Context, entity: EditEntity): TextInputLayout {
    val inputLayout = TextInputLayout(context, null).apply {
        hint = entity.hint
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    val editor = CodeView(context).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setSingleLine(false)
        minLines = 2
        maxLines = AppConfig.sourceEditMaxLine.coerceIn(2, 10)
        addLegadoPattern()
        addJsonPattern()
        addJsPattern()
        setText(entity.value.orEmpty())
        isFocusable = true
        isFocusableInTouchMode = true
    }
    inputLayout.addView(editor)
    val watcher = watcherFor(entity)
    editor.addTextChangedListener(watcher)
    editor.setTag(R.id.tag1, EditorBinding(entity, watcher))
    return inputLayout
}

private fun bindSourceEditor(layout: TextInputLayout, entity: EditEntity) {
    layout.hint = entity.hint
    val editor = layout.editText as? CodeView ?: return
    val binding = editor.getTag(R.id.tag1) as? EditorBinding
    if (binding?.entity === entity) return
    binding?.let { editor.removeTextChangedListener(it.watcher) }
    editor.setText(entity.value.orEmpty())
    val watcher = watcherFor(entity)
    editor.addTextChangedListener(watcher)
    editor.setTag(R.id.tag1, EditorBinding(entity, watcher))
}

private fun watcherFor(entity: EditEntity) = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: Editable?) {
        entity.value = s?.toString()
    }
}
