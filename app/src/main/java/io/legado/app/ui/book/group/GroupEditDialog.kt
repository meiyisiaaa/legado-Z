package io.legado.app.ui.book.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.BookGroup
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.launch
import io.legado.app.utils.readUri
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.FileOutputStream

class GroupEditDialog() : BaseDialogFragment(0) {

    constructor(bookGroup: BookGroup? = null) : this() {
        arguments = Bundle().apply { putParcelable("group", bookGroup?.copy()) }
    }

    private val viewModel by viewModels<GroupViewModel>()
    private var bookGroup: BookGroup? = null
    private var coverPath by mutableStateOf<String?>(null)
    private var initialName by mutableStateOf("")
    private var initialSort by mutableStateOf(-1)
    private var initialEnableRefresh by mutableStateOf(true)
    private var initialOnlyRead by mutableStateOf(false)
    private val selectImage = registerForActivityResult(HandleFileContract()) { result ->
        val uri = result.uri ?: return@registerForActivityResult
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            coverPath = uri.toString()
            return@registerForActivityResult
        }
        readUri(uri) { fileDoc, inputStream ->
            try {
                val folder = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use { tmp ->
                    MD5Utils.md5Encode(tmp) + suffix
                }
                val file = FileUtils.createFileIfNotExist(folder, "covers", fileName)
                FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
                coverPath = file.absolutePath
            } catch (e: Exception) {
                appCtx.toastOnUi(e.localizedMessage)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        @Suppress("DEPRECATION")
        bookGroup = arguments?.getParcelable<BookGroup>("group")?.copy()
        initialName = bookGroup?.groupName.orEmpty()
        initialSort = bookGroup?.bookSort ?: -1
        initialEnableRefresh = bookGroup?.enableRefresh ?: true
        initialOnlyRead = bookGroup?.onlyUpdateRead ?: false
        coverPath = bookGroup?.cover
        return LegadoComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    GroupEditContent(
                        initialName = initialName,
                        initialSort = initialSort,
                        initialEnableRefresh = initialEnableRefresh,
                        initialOnlyRead = initialOnlyRead,
                        canDelete = bookGroup?.let { it.groupId > 0 || it.groupId == Long.MIN_VALUE } == true,
                        onCoverClick = ::chooseCover,
                        onCancel = { dismissAllowingStateLoss() },
                        onDelete = {
                            bookGroup?.let { group ->
                                viewModel.delGroup(group) { dismissAllowingStateLoss() }
                            }
                        },
                        onSave = ::saveGroup,
                        coverPreview = { CoverPreview(coverPath) }
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    private fun chooseCover() {
        if (!coverPath.isNullOrEmpty()) {
            val actions = arrayListOf(getString(io.legado.app.R.string.select_image), getString(io.legado.app.R.string.delete))
            context?.selector(items = actions) { _, index ->
                when (index) {
                    0 -> launchImagePicker()
                    1 -> coverPath = null
                }
            }
        } else {
            launchImagePicker()
        }
    }

    private fun launchImagePicker() {
        selectImage.launch { mode = HandleFileContract.IMAGE }
    }

    private fun saveGroup(
        name: String,
        sort: Int,
        enableRefresh: Boolean,
        onlyUpdateRead: Boolean
    ) {
        if (name.isBlank()) {
            requireContext().toastOnUi("分组名称不能为空")
            return
        }
        bookGroup?.let { group ->
            group.groupName = name
            group.cover = coverPath
            group.bookSort = sort
            group.enableRefresh = enableRefresh
            group.onlyUpdateRead = onlyUpdateRead
            viewModel.upGroup(group) { dismissAllowingStateLoss() }
        } ?: viewModel.addGroup(
            name,
            sort,
            enableRefresh,
            onlyUpdateRead,
            coverPath
        ) { dismissAllowingStateLoss() }
    }

    @androidx.compose.runtime.Composable
    private fun CoverPreview(path: String?) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            factory = { context ->
                CoverImageView(context).apply {
                    load(path = path, fragment = this@GroupEditDialog, lifecycle = viewLifecycleOwner.lifecycle)
                }
            },
            update = { cover ->
                if (cover.bitmapPath != path) {
                    cover.load(path = path, fragment = this@GroupEditDialog, lifecycle = viewLifecycleOwner.lifecycle)
                }
            }
        )
    }
}
