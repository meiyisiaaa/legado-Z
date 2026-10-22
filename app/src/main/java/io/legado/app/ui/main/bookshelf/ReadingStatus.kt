package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.Book

/** A view of saved reading position, independent of groups and update counts. */
internal enum class ReadingStatus {
    ALL, NOT_STARTED, READING, AT_LAST_CHAPTER;

    fun includes(book: Book): Boolean {
        val started = book.durChapterIndex > 0 || book.durChapterPos > 0
        val atLastChapter = started && book.totalChapterNum > 0 &&
            book.durChapterIndex >= book.totalChapterNum - 1
        return when (this) {
            ALL -> true
            NOT_STARTED -> !started
            READING -> started && !atLastChapter
            AT_LAST_CHAPTER -> atLastChapter
        }
    }
}
