package io.legado.app.ui.file

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.observe
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst
import io.legado.app.databinding.ActivityFileManageBinding
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.openFileUri
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class FileManageActivity : VMBaseActivity<ActivityFileManageBinding, FileManageViewModel>() {

    override val binding by viewBinding(ActivityFileManageBinding::inflate)
    override val viewModel by viewModels<FileManageViewModel>()

    private val dirParent = ".."
    private val currentFiles = mutableStateListOf<File>()
    private val pathItems = mutableStateListOf<File>()
    private var searchQuery by mutableStateOf("")

    override fun setupSystemBar() {
        super.setupSystemBar()
        setLightStatusBar(ColorUtils.isColorLight(ThemeStore.backgroundColor(this)))
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        pathItems.clear()
        pathItems.addAll(viewModel.subDocs)
        binding.composeView.setContent {
            LegadoTheme {
                FileManageContent(
                    files = currentFiles,
                    pathItems = pathItems,
                    searchQuery = searchQuery,
                    rootDoc = viewModel.rootDoc,
                    currentDir = pathItems.lastOrNull() ?: viewModel.rootDoc,
                    parentEntry = dirParent,
                    onSearchQueryChange = { searchQuery = it },
                    onNavigateRoot = ::navigateRoot,
                    onNavigateTo = ::navigateTo,
                    onEnterDirectory = ::enterDirectory,
                    onOpen = ::openFile,
                    onDelete = viewModel::delFile,
                    onBack = ::handleBackPress
                )
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }
        viewModel.upFiles(viewModel.lastDir)
    }

    override fun observeLiveBus() {
        viewModel.filesLiveData.observe(this) { files ->
            searchQuery = ""
            currentFiles.clear()
            currentFiles.addAll(files.orEmpty())
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

    private fun handleBackPress() {
        if (viewModel.lastDir != viewModel.rootDoc) gotoLastDir() else finish()
    }

    private fun gotoLastDir() {
        viewModel.subDocs.removeLastOrNull()
        pathItems.clear()
        pathItems.addAll(viewModel.subDocs)
        viewModel.upFiles(viewModel.lastDir)
    }

    private fun openFile(file: File) {
        openFileUri(
            FileProvider.getUriForFile(
                this,
                AppConst.authority,
                file
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileManageContent(
    files: List<File>,
    pathItems: List<File>,
    searchQuery: String,
    rootDoc: File?,
    currentDir: File?,
    parentEntry: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateRoot: () -> Unit,
    onNavigateTo: (Int) -> Unit,
    onEnterDirectory: (File) -> Unit,
    onOpen: (File) -> Unit,
    onDelete: (File) -> Unit,
    onBack: () -> Unit
) {
    val fileSnapshot = files.toList()
    val pathSnapshot = pathItems.toList()
    val visibleFiles = remember(fileSnapshot, searchQuery, currentDir) {
        if (searchQuery.isBlank()) fileSnapshot else fileSnapshot.filter {
            it == currentDir && currentDir != rootDoc || it.name.contains(searchQuery, ignoreCase = true)
        }
    }
    var menuFile by remember { mutableStateOf<File?>(null) }
    val crumbs = remember(rootDoc, pathSnapshot) { listOfNotNull(rootDoc) + pathSnapshot }
    val searchLabel = stringResource(R.string.search)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics {
                contentDescription = "返回"
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = searchLabel },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.merge(
                                TextStyle(color = MaterialTheme.colorScheme.onSurface)
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(crumbs.size, key = { crumbs[it].absolutePath }) { index ->
                if (index == 0) {
                    Text(
                        text = "root",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .combinedClickable(onClick = onNavigateRoot)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = crumbs[index].name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (index == crumbs.lastIndex) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .combinedClickable(onClick = { onNavigateTo(index) })
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (visibleFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding(),
                    contentPadding = PaddingValues(vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(visibleFiles, key = { _, file -> file.absolutePath }) { index, file ->
                    val isParent = file == currentDir && currentDir != rootDoc
                    val title = if (isParent) parentEntry else file.name
                    var expanded by remember(file) { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            isParent -> onNavigateTo((pathItems.size - 1).coerceAtLeast(0))
                                            file.isDirectory -> onEnterDirectory(file)
                                            else -> onOpen(file)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isParent) {
                                            menuFile = file
                                            expanded = true
                                        }
                                    }
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
                                text = title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isParent) {
                            DropdownMenu(
                                expanded = expanded && menuFile == file,
                                onDismissRequest = { expanded = false; menuFile = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_outline_delete),
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        expanded = false
                                        menuFile = null
                                        onDelete(file)
                                    }
                                )
                            }
                        }
                    }
                    if (index != visibleFiles.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 35.dp, end = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
