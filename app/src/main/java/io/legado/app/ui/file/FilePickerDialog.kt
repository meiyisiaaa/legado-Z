package io.legado.app.ui.file

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogFileChooserBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.FileUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class FilePickerDialog : BaseDialogFragment(R.layout.dialog_file_chooser) {

    companion object {
        const val tag = "FileChooserDialog"

        fun show(
            manager: FragmentManager,
            mode: Int = HandleFileContract.FILE,
            title: String? = null,
            initPath: String? = null,
            isShowHideDir: Boolean = false,
            allowExtensions: Array<String>? = null,
        ) {
            FilePickerDialog().apply {
                arguments = Bundle().apply {
                    putInt("mode", mode)
                    putString("title", title)
                    putBoolean("isShowHideDir", isShowHideDir)
                    putString("initPath", initPath)
                    putStringArray("allowExtensions", allowExtensions)
                }
            }.show(manager, tag)
        }
    }

    private val binding by viewBinding(DialogFileChooserBinding::bind)
    private val viewModel by viewModels<FilePickerViewModel>()
    private val files = mutableStateListOf<File>()
    private val pathItems = mutableStateListOf<File>()
    private var selectedFile by mutableStateOf<File?>(null)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.82f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.setBackgroundResource(R.color.background_card)
        viewModel.filesLiveData.observe(viewLifecycleOwner) { rows ->
            selectedFile = null
            files.clear()
            files.addAll(rows.orEmpty())
        }
        viewModel.initData(arguments)
        pathItems.clear()
        pathItems.addAll(viewModel.subDocs)
        val title = arguments?.getString("title") ?: if (viewModel.isSelectDir) {
            getString(R.string.folder_chooser)
        } else {
            getString(R.string.file_chooser)
        }
        binding.composeView.setContent {
            LegadoTheme {
                FilePickerContent(
                    title = title,
                    files = files.toList(),
                    pathItems = pathItems.toList(),
                    rootDoc = viewModel.rootDoc,
                    currentDir = viewModel.lastDir,
                    isSelectFile = viewModel.isSelectFile,
                    allowExtensions = viewModel.allowExtensions,
                    selectedFile = selectedFile,
                    onSelectFile = { selectedFile = it },
                    onNavigateRoot = ::navigateRoot,
                    onNavigateTo = ::navigateTo,
                    onEnterDirectory = ::enterDirectory,
                    onCreateFolder = ::createFolder,
                    onConfirm = ::confirmSelection
                )
            }
        }
    }

    private fun navigateRoot() {
        viewModel.subDocs.clear()
        pathItems.clear()
        viewModel.upFiles(viewModel.rootDoc)
    }

    private fun navigateTo(index: Int) {
        if (index <= 0) {
            navigateRoot()
            return
        }
        val nextPath = viewModel.subDocs.take(index).toMutableList()
        viewModel.subDocs = nextPath
        pathItems.clear()
        pathItems.addAll(nextPath)
        viewModel.upFiles(nextPath.lastOrNull())
    }

    private fun enterDirectory(file: File) {
        val nextPath = viewModel.subDocs.toMutableList().apply { add(file) }
        viewModel.subDocs = nextPath
        pathItems.clear()
        pathItems.addAll(nextPath)
        viewModel.upFiles(file)
    }

    private fun confirmSelection() {
        if (viewModel.isSelectDir) {
            viewModel.lastDir?.let { returnResult(it.path) }
        } else {
            selectedFile?.let { returnResult(it.path) } ?: toastOnUi("请选择文件")
        }
    }

    private fun returnResult(path: String) {
        val data = Intent().setData(Uri.fromFile(File(path)))
        (parentFragment as? CallBack)?.onResult(data)
        (activity as? CallBack)?.onResult(data)
        dismissAllowingStateLoss()
    }

    private fun createFolder() {
        alert(R.string.create_folder) {
            val editBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "文件夹名"
            }
            customView { editBinding.root }
            okButton {
                val name = editBinding.editView.text?.toString()
                if (name.isNullOrBlank()) {
                    toastOnUi("文件夹名不能为空")
                } else {
                    viewModel.createFolder(name.trim())
                }
            }
            cancelButton()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        activity?.finish()
    }

    interface CallBack {
        fun onResult(data: Intent)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilePickerContent(
    title: String,
    files: List<File>,
    pathItems: List<File>,
    rootDoc: File?,
    currentDir: File?,
    isSelectFile: Boolean,
    allowExtensions: Array<String>?,
    selectedFile: File?,
    onSelectFile: (File) -> Unit,
    onNavigateRoot: () -> Unit,
    onNavigateTo: (Int) -> Unit,
    onEnterDirectory: (File) -> Unit,
    onCreateFolder: () -> Unit,
    onConfirm: () -> Unit
) {
    val crumbs = listOfNotNull(rootDoc) + pathItems
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_menu),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_folder)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_create_folder_outline), contentDescription = null)
                        },
                        onClick = { menuExpanded = false; onCreateFolder() }
                    )
                }
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(crumbs.size, key = { crumbs[it].absolutePath }) { index ->
                if (index > 0) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (index == 0) "root" else crumbs[index].name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (index == crumbs.lastIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = if (index == 0) onNavigateRoot else { { onNavigateTo(index) } }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (files.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    val isParent = file == currentDir && currentDir != rootDoc
                    val extensionAllowed = allowExtensions.isNullOrEmpty() ||
                        allowExtensions.any { it.equals(FileUtils.getExtension(file.path), ignoreCase = true) }
                    val allowed = isParent || file.isDirectory || isSelectFile && extensionAllowed
                    val selected = file == selectedFile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .combinedClickable(
                                enabled = allowed,
                                onClick = {
                                    when {
                                        isParent -> onNavigateTo((pathItems.size - 1).coerceAtLeast(0))
                                        file.isDirectory -> onEnterDirectory(file)
                                        isSelectFile -> onSelectFile(file)
                                    }
                                }
                            )
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.background
                            )
                            .padding(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                when {
                                    isParent -> R.drawable.ic_back
                                    file.isDirectory -> R.drawable.ic_folder_outline
                                    else -> R.drawable.ic_code
                                }
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isParent) ".." else file.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (allowed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (file != files.lastOrNull()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 34.dp, end = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(3.dp),
            onClick = onConfirm
        ) {
            Text(stringResource(R.string.ok), modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
