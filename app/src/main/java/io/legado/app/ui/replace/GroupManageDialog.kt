package io.legado.app.ui.replace

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

private data class GroupEditor(val group: String?, val value: String)

class GroupManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val viewModel: ReplaceRuleViewModel by activityViewModels()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val groups = mutableStateListOf<String>()
    private var editor by mutableStateOf<GroupEditor?>(null)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundColor(backgroundColor)
        initView()
        initData()
    }

    private fun initView() = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.title = getString(R.string.group_manage)
        toolBar.inflateMenu(R.menu.group_manage)
        toolBar.menu.applyTint(requireContext())
        toolBar.setOnMenuItemClickListener(this@GroupManageDialog)
        val parent = recyclerView.parent as ViewGroup
        val index = parent.indexOfChild(recyclerView)
        val params = recyclerView.layoutParams
        parent.removeView(recyclerView)
        val composeView = LegadoComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    GroupManageContent(
                        groups = groups,
                        editor = editor,
                        onEditorChange = { editor = it },
                        onEdit = { group -> editor = GroupEditor(group, group) },
                        onDelete = { viewModel.delGroup(it) },
                        onSave = { current ->
                            if (current.group == null) {
                                viewModel.addGroup(current.value)
                            } else {
                                viewModel.upGroup(current.group, current.value.takeIf { it.isNotBlank() })
                            }
                            editor = null
                        },
                        onDismissEditor = { editor = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        parent.addView(composeView, index, params)
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.replaceRuleDao.flowGroups().conflate().collect {
                groups.clear()
                groups.addAll(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> editor = GroupEditor(null, "")
        }
        return true
    }

}

@Composable
private fun GroupManageContent(
    groups: List<String>,
    editor: GroupEditor?,
    onEditorChange: (GroupEditor) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (GroupEditor) -> Unit,
    onDismissEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (groups.isEmpty()) {
            Text(
                "暂无分组",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(groups, key = { it }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        TextButton(onClick = { onEdit(group) }) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(onClick = { onDelete(group) }) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    editor?.let { initial ->
        var text by remember(initial) { mutableStateOf(initial.value) }
        AlertDialog(
            onDismissRequest = onDismissEditor,
            title = {
                Text(stringResource(if (initial.group == null) R.string.add_group else R.string.group_edit))
            },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onEditorChange(initial.copy(value = it))
                    },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { onSave(initial.copy(value = text)) }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEditor) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
