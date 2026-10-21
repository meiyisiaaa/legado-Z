package io.legado.app.ui.file

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.ui.widget.compose.LegadoTheme
import java.io.File
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FilePickerDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteringTemporaryDirectoryUpdatesBreadcrumbAndParentEntryReturnsToRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "file-picker-${UUID.randomUUID()}")
        assertTrue(root.mkdir())
        val download = File(root, "Download")
        assertTrue(download.mkdir())

        val files = mutableStateListOf<File>().apply { addAll(root.listFiles().orEmpty().toList()) }
        val pathItems = mutableStateListOf<File>()
        var currentDir = root

        fun loadFiles(directory: File) {
            files.clear()
            if (directory == root) {
                files.addAll(directory.listFiles().orEmpty().toList())
            } else {
                files.add(directory)
                files.addAll(directory.listFiles().orEmpty().toList())
            }
        }

        fun navigateRoot() {
            currentDir = root
            pathItems.clear()
            loadFiles(root)
        }

        try {
            composeRule.setContent {
                LegadoTheme {
                    FilePickerContent(
                        title = "选择目录",
                        files = files.toList(),
                        pathItems = pathItems.toList(),
                        rootDoc = root,
                        currentDir = currentDir,
                        isSelectFile = false,
                        allowExtensions = null,
                        selectedFile = null,
                        onSelectFile = {},
                        onNavigateRoot = ::navigateRoot,
                        onNavigateTo = { index ->
                            if (index <= 0) {
                                navigateRoot()
                            } else {
                                val nextPath = pathItems.take(index)
                                pathItems.clear()
                                pathItems.addAll(nextPath)
                                currentDir = nextPath.lastOrNull() ?: root
                                loadFiles(currentDir)
                            }
                        },
                        onEnterDirectory = { directory ->
                            currentDir = directory
                            pathItems.add(directory)
                            loadFiles(directory)
                        },
                        onCreateFolder = {},
                        onConfirm = {}
                    )
                }
            }

            composeRule.onNodeWithText("Download").performClick()
            composeRule.onNodeWithText("root").assertIsDisplayed()
            composeRule.onAllNodesWithText("Download").assertCountEquals(1)
            composeRule.onNodeWithText("..").assertIsDisplayed()

            composeRule.onNodeWithText("..").performClick()
            composeRule.onNodeWithText("root").assertIsDisplayed()
            composeRule.onAllNodesWithText("Download").assertCountEquals(1)
            composeRule.onNodeWithText("..").assertDoesNotExist()
        } finally {
            root.deleteRecursively()
        }
    }
}
