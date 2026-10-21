package io.legado.app.ui.book.read

import android.app.Application
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Compose editor for the current chapter's cached text. */
class ContentEditDialog : BaseDialogFragment(R.layout.dialog_content_edit_compose) {

    private val viewModel by viewModels<ContentEditViewModel>()
    private var editorValue by mutableStateOf(TextFieldValue())
    private var contentLoaded by mutableStateOf(false)
    private var chapterTitle by mutableStateOf(ReadBook.curTextChapter?.title.orEmpty())
    private var editingChapter by mutableStateOf<BookChapter?>(null)
    private var editedTitle by mutableStateOf("")

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    val isLoading by viewModel.loadStateLiveData.observeAsState(false)
                    ContentEditContent(
                        title = chapterTitle,
                        value = editorValue,
                        isLoading = isLoading,
                        onValueChange = { editorValue = it },
                        onTitleClick = ::beginEditTitle,
                        onSave = {
                            save()
                            dismissAllowingStateLoss()
                        },
                        onReset = {
                            viewModel.initContent(reset = true) { content ->
                                editorValue = textFieldValue(content)
                                contentLoaded = true
                                ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                            }
                        },
                        onCopyAll = {
                            requireContext().sendToClip("$chapterTitle\n${editorValue.text}")
                        },
                        modifier = Modifier
                    )
                    editingChapter?.let {
                        EditChapterTitleDialog(
                            title = editedTitle,
                            onTitleChange = { editedTitle = it },
                            onDismiss = { editingChapter = null },
                            onConfirm = ::saveTitle
                        )
                    }
                }
            }
        }
        viewModel.initContent {
            editorValue = textFieldValue(it)
            contentLoaded = true
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        save()
    }

    private fun textFieldValue(text: String): TextFieldValue {
        val offset = ReadBook.durChapterPos.coerceIn(0, text.length)
        return TextFieldValue(text = text, selection = TextRange(offset))
    }

    private fun beginEditTitle() {
        lifecycleScope.launch {
            val book = ReadBook.book ?: return@launch
            val chapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            } ?: return@launch
            editingChapter = chapter
            editedTitle = chapter.title
        }
    }

    private fun saveTitle() {
        val chapter = editingChapter ?: return
        chapter.title = editedTitle
        editingChapter = null
        lifecycleScope.launch {
            withContext(IO) { chapter.update() }
            chapterTitle = chapter.getDisplayTitle()
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    private fun save() {
        if (!contentLoaded) return
        val content = editorValue.text
        Coroutine.async {
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@async
            BookHelp.saveText(book, chapter, content)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    class ContentEditViewModel(application: Application) : BaseViewModel(application) {
        val loadStateLiveData = MutableLiveData<Boolean>()
        var content: String? = null

        fun initContent(reset: Boolean = false, success: (String) -> Unit) {
            execute {
                val book = ReadBook.book ?: return@execute null
                val chapter = appDb.bookChapterDao
                    .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?: return@execute null
                if (reset) {
                    content = null
                    BookHelp.delContent(book, chapter)
                    if (!book.isLocal) ReadBook.bookSource?.let { bookSource ->
                        WebBook.getContentAwait(bookSource, book, chapter)
                    }
                }
                content ?: let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    val cachedContent = BookHelp.getContent(book, chapter) ?: return@let null
                    contentProcessor.getContent(book, chapter, cachedContent, includeTitle = false)
                        .toString()
                }
            }.onStart {
                loadStateLiveData.postValue(true)
            }.onSuccess {
                content = it
                success.invoke(it ?: "")
            }.onFinally {
                loadStateLiveData.postValue(false)
            }
        }
    }
}
