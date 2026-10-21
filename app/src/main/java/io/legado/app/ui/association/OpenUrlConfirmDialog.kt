package io.legado.app.ui.association

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.viewModels
import androidx.core.net.toUri
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

class OpenUrlConfirmDialog() : BaseDialogFragment(R.layout.dialog_open_url_confirm) {

    constructor(
        uri: String,
        mimeType: String?,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("uri", uri)
            putString("mimeType", mimeType)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    private val viewModel by viewModels<OpenUrlConfirmViewModel>()
    private var showDeleteConfirm by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments ?: return
        viewModel.initData(args)
        if (viewModel.uri.isBlank()) {
            dismiss()
            return
        }
        view.findViewById<ComposeView>(R.id.composeView).setContent {
            LegadoTheme {
                OpenUrlConfirmContent(
                    sourceName = viewModel.sourceName,
                    onCancel = { dismiss() },
                    onConfirm = {
                        openUrl()
                        dismiss()
                    },
                    onDisableSource = {
                        viewModel.disableSource { dismiss() }
                    },
                    onDeleteSource = { showDeleteConfirm = true }
                )
            }
        }
    }

    private fun openUrl() {
        try {
            val uri = viewModel.uri.toUri()
            val mimeType = viewModel.mimeType
            val targetIntent = Intent(Intent.ACTION_VIEW).apply {
                if (!mimeType.isNullOrBlank()) {
                    setDataAndType(uri, mimeType)
                } else {
                    data = uri
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (targetIntent.resolveActivity(appCtx.packageManager) != null) {
                startActivity(targetIntent)
            } else {
                toastOnUi(R.string.can_not_open)
            }
        } catch (e: Exception) {
            AppLog.put("打开链接失败", e, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.finish()
    }

    @Composable
    private fun OpenUrlConfirmContent(
        sourceName: String,
        onCancel: () -> Unit,
        onConfirm: () -> Unit,
        onDisableSource: () -> Unit,
        onDeleteSource: () -> Unit
    ) {
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(R.string.draw)) },
                text = { Text(getString(R.string.sure_del) + "\n" + sourceName) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSource { dismiss() }
                    }) { Text(stringResource(R.string.delete_source)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("跳转确认", style = MaterialTheme.typography.titleLarge)
                        if (sourceName.isNotBlank()) {
                            Text(
                                sourceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    var menuExpanded by androidx.compose.runtime.remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.more_menu),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.disable_source)) },
                                onClick = { menuExpanded = false; onDisableSource() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_source)) },
                                onClick = { menuExpanded = false; onDeleteSource() }
                            )
                        }
                    }
                }
                Text(
                    text = "正在请求跳转链接/应用，是否跳转？",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}
