package io.legado.app.ui.book.info.edit

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.AudioPlay
import io.legado.app.model.VideoPlay

class BookInfoEditViewModel(application: Application) : BaseViewModel(application) {
    var book: Book? = null
    val bookData = MutableLiveData<Book>()
    val saving = MutableLiveData(false)

    fun loadBook(bookUrl: String) {
        execute {
            book = appDb.bookDao.getBook(bookUrl)
            book?.let {
                bookData.postValue(it)
            }
        }
    }

    fun saveBook(book: Book, userNote: String, success: (() -> Unit)?) {
        if (saving.value == true) return
        saving.value = true
        execute {
            check(appDb.bookDao.updateBookInfo(book, userNote)) {
                context.getString(R.string.book_note_book_missing)
            }
            appDb.bookDao.getBook(book.bookUrl)!!
        }.onSuccess {
            // Keep active reader positions/configuration; only publish the edited metadata.
            listOfNotNull(ReadBook.book, ReadManga.book, AudioPlay.book, VideoPlay.book)
                .filter { active -> active.bookUrl == it.bookUrl }
                .forEach { active ->
                    active.name = it.name
                    active.author = it.author
                    active.type = it.type
                    active.customCoverUrl = it.customCoverUrl
                    active.customIntro = it.customIntro
                    active.userNote = it.userNote
                }
            this@BookInfoEditViewModel.book = it
            success?.invoke()
        }.onError {
            if (it is SQLiteConstraintException) {
                AppLog.put("书籍信息保存失败，存在相同书名作者书籍\n$it", it, true)
            } else {
                AppLog.put("书籍信息保存失败\n$it", it, true)
            }
        }.onFinally {
            saving.value = false
        }
    }
}
