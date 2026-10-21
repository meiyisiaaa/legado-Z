package io.legado.app.ui.book.info.edit

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ActivityBookInfoEditBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.removeType
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.readUri
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import splitties.views.bottomPadding
import java.io.FileOutputStream

class BookInfoEditActivity :
    VMBaseActivity<ActivityBookInfoEditBinding, BookInfoEditViewModel>(),
    ChangeCoverDialog.CallBack {

    private val selectCover = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            coverChangeTo(uri)
        }
    }

    override val binding by viewBinding(ActivityBookInfoEditBinding::inflate)
    override val viewModel by viewModels<BookInfoEditViewModel>()
    private var uiState by mutableStateOf(BookInfoEditUiState())
    private val coverImageView by lazy {
        CoverImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.img_cover)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        installComposeContent()
        initView()
        viewModel.bookData.observe(this) { upView(it) }
        if (viewModel.bookData.value == null) {
            intent.getStringExtra("bookUrl")?.let {
                viewModel.loadBook(it)
            }
        }
    }

    private fun installComposeContent() {
        binding.composeContainer.addView(
            LegadoComposeView(this).apply {
                setContent {
                    LegadoTheme {
                        BookInfoEditScreen(
                            state = uiState,
                            onStateChange = { uiState = it },
                            coverContent = { modifier ->
                                AndroidView(factory = { coverImageView }, modifier = modifier.fillMaxSize())
                            },
                            onSelectCover = {
                                selectCover.launch { mode = HandleFileContract.IMAGE }
                            },
                            onChangeCover = {
                                viewModel.bookData.value?.let { book ->
                                    showDialogFragment(ChangeCoverDialog(book.name, book.author))
                                }
                            },
                            onRefreshCover = {
                                viewModel.book?.customCoverUrl = uiState.coverUrl
                                upCover()
                            }
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveData()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val insets = windowInsets.getInsets(typeMask)
            view.bottomPadding = insets.bottom
            windowInsets
        }
    }

    private fun upView(book: Book) {
        uiState = uiState.copy(
            name = book.name,
            author = book.author,
            type = when {
                book.isVideo -> 4
                book.isImage -> 2
                book.isAudio -> 1
                else -> 0
            },
            coverUrl = book.getDisplayCover().orEmpty(),
            intro = book.getDisplayIntro().orEmpty()
        )
        upCover()
    }

    private fun upCover() {
        viewModel.book?.let {
            coverImageView.load(it, false)
        }
    }

    private fun saveData() {
        val book = viewModel.book ?: return
        val oldBook = book.copy()
        book.name = uiState.name
        book.author = uiState.author
        val local = if (book.isLocal) BookType.local else 0
        val bookType = when (uiState.type) {
            4 -> BookType.video or local
            2 -> BookType.image or local
            1 -> BookType.audio or local
            else -> BookType.text or local
        }
        book.removeType(BookType.video, BookType.local, BookType.image, BookType.audio, BookType.text)
        book.addType(bookType)
        val customCoverUrl = uiState.coverUrl
        book.customCoverUrl = if (customCoverUrl == book.coverUrl) null else customCoverUrl
        val customIntro = uiState.intro
        book.customIntro = if (customIntro == book.intro) null else customIntro
        BookHelp.updateCacheFolder(oldBook, book)
        viewModel.saveBook(book) {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.book?.customCoverUrl = coverUrl
        uiState = uiState.copy(coverUrl = coverUrl)
        upCover()
    }

    private fun coverChangeTo(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            coverChangeTo(uri.toString())
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                inputStream.use {
                    var file = this.externalFiles
                    val suffix = if (fileDoc.name.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        "." + fileDoc.name.substringAfterLast(".")
                    }
                    val fileName = uri.inputStream(this).getOrThrow().use {
                        MD5Utils.md5Encode(it) + suffix
                    }
                    file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    coverChangeTo(file.absolutePath)
                }
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

}
