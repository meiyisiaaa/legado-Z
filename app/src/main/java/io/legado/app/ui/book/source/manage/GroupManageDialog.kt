package io.legado.app.ui.book.source.manage

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.legadoPressFeedback
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class GroupManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val viewModel: BookSourceViewModel by activityViewModels()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val groups = mutableStateListOf<String>()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onFragmentCreated(view: View, savedInstanceState: android.os.Bundle?) {
        val host = LegadoComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        host.setContent {
            LegadoTheme {
                BookSourceGroupManageContent(
                    groups = groups,
                    onAdd = viewModel::addGroup,
                    onRename = viewModel::upGroup,
                    onDelete = viewModel::delGroup,
                    onClose = ::dismiss
                )
            }
        }
        binding.root.removeAllViews()
        binding.root.addView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lifecycleScope.launch {
            appDb.bookSourceDao.flowGroups().conflate().collect { rows ->
                groups.clear()
                groups.addAll(rows)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookSourceGroupManageContent(
    groups: List<String>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    var addDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var input by remember(addDialog, editingGroup) { mutableStateOf(editingGroup.orEmpty()) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_manage)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_clear), contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = { input = ""; addDialog = true }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_group))
                    }
                }
            )
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.empty), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(groups, key = { it }) { group ->
                    val editLabel = stringResource(R.string.edit)
                    val deleteLabel = stringResource(R.string.delete)
                    val editInteractions = remember { MutableInteractionSource() }
                    val deleteInteractions = remember { MutableInteractionSource() }
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(group, Modifier.weight(1f).padding(end = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            IconButton(
                                onClick = { input = group; editingGroup = group },
                                interactionSource = editInteractions,
                                modifier = Modifier.size(48.dp).legadoPressFeedback(editInteractions)
                                    .semantics { contentDescription = "$group, $editLabel" }
                            ) {
                                Icon(painterResource(R.drawable.ic_edit), contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = { onDelete(group) },
                                interactionSource = deleteInteractions,
                                modifier = Modifier.size(48.dp).legadoPressFeedback(deleteInteractions)
                                    .semantics { contentDescription = "$group, $deleteLabel" }
                            ) {
                                Icon(painterResource(R.drawable.ic_outline_delete), contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
    if (addDialog || editingGroup != null) {
        val isEdit = editingGroup != null
        AlertDialog(
            onDismissRequest = { addDialog = false; editingGroup = null },
            shape = RoundedCornerShape(12.dp),
            title = { Text(stringResource(if (isEdit) R.string.group_edit else R.string.add_group)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(modifier = Modifier.sizeIn(minHeight = 48.dp), onClick = {
                    if (isEdit) {
                        editingGroup?.let { onRename(it, input) }
                    } else if (input.isNotBlank()) {
                        onAdd(input)
                    }
                    addDialog = false
                    editingGroup = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(modifier = Modifier.sizeIn(minHeight = 48.dp),
                    onClick = { addDialog = false; editingGroup = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
