package io.legado.app.ui.rss.source.manage

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class GroupManageDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val viewModel: RssSourceViewModel by activityViewModels()
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
                var addDialog by remember { mutableStateOf(false) }
                var editingGroup by remember { mutableStateOf<String?>(null) }
                var input by remember(addDialog, editingGroup) { mutableStateOf(editingGroup.orEmpty()) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.group_manage)) },
                            actions = {
                                IconButton(onClick = { input = ""; addDialog = true }) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_group))
                                }
                            }
                        )
                    },
                    bottomBar = {
                        Button(onClick = { dismissAllowingStateLoss() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                ) { padding ->
                    if (groups.isEmpty()) {
                        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                            Text(stringResource(R.string.empty), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(padding),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(groups, key = { it }) { group ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(group, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        TextButton(onClick = { input = group; editingGroup = group }) {
                                            Text(stringResource(R.string.edit))
                                        }
                                        TextButton(onClick = { viewModel.delGroup(group) }) {
                                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                }
                            }
                        }
                    }
                }
                if (addDialog || editingGroup != null) {
                    val isEdit = editingGroup != null
                    AlertDialog(
                        onDismissRequest = { addDialog = false; editingGroup = null },
                        title = { Text(stringResource(if (isEdit) R.string.group_edit else R.string.add_group)) },
                        text = {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.group_name)) },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (isEdit) {
                                    editingGroup?.let { viewModel.upGroup(it, input) }
                                } else if (input.isNotBlank()) {
                                    viewModel.addGroup(input)
                                }
                                addDialog = false
                                editingGroup = null
                            }) { Text(stringResource(R.string.ok)) }
                        },
                        dismissButton = { TextButton(onClick = { addDialog = false; editingGroup = null }) { Text(stringResource(R.string.cancel)) } }
                    )
                }
            }
        }
        binding.root.removeAllViews()
        binding.root.addView(host, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        lifecycleScope.launch {
            appDb.rssSourceDao.flowGroups().conflate().collect { rows ->
                groups.clear()
                groups.addAll(rows)
            }
        }
    }
}
