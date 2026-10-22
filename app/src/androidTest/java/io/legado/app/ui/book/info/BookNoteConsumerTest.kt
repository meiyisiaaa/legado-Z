package io.legado.app.ui.book.info

import android.app.Application
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BookNoteConsumerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dao get() = appDb.bookDao

    @Test fun detailSourceChangeKeepsLatestNoteAndPerformsBookDeleteReaderCleanup() {
        assertDetailSourceChange("Latest note saved after loading details")
    }

    @Test fun detailSourceChangeDoesNotRestoreANoteClearedAfterLoadingDetails() {
        assertDetailSourceChange(null)
    }

    private fun assertDetailSourceChange(latestNote: String?) {
        val id = "book-note-source-${UUID.randomUUID()}"
        val old = Book(bookUrl = "$id-old", name = id, author = "Note fixture", type = BookType.text,
            origin = "$id-source", userNote = "Stale detail note")
        val target = old.copy(bookUrl = "$id-new", userNote = null)
        val previousReader = ReadBook.book
        val viewModel = BookInfoViewModel(instrumentation.targetContext.applicationContext as Application)
        val changed = CountDownLatch(1)
        val observer = Observer<Book> { if (it.bookUrl == target.bookUrl) changed.countDown() }
        dao.insert(old)
        dao.updateUserNote(old.bookUrl, latestNote)
        try {
            instrumentation.runOnMainSync {
                ReadBook.book = old
                viewModel.bookData.value = old
                viewModel.inBookshelf = true
                viewModel.bookData.observeForever(observer)
                viewModel.changeTo(BookSource(bookSourceUrl = target.origin), target, emptyList())
            }
            assertTrue("Details source replacement did not finish", changed.await(10, TimeUnit.SECONDS))
            assertNull(dao.getBook(old.bookUrl))
            assertEquals(latestNote, dao.getBook(target.bookUrl)?.userNote)
            instrumentation.runOnMainSync { assertNull(ReadBook.book) }
        } finally {
            instrumentation.runOnMainSync {
                viewModel.bookData.removeObserver(observer)
                viewModel.viewModelScope.cancel()
                ReadBook.book = previousReader
            }
            listOf(old.bookUrl, target.bookUrl).forEach { url -> dao.getBook(url)?.let { dao.delete(it) } }
        }
    }

    @Test fun localMergeKeepsCurrentLocalNoteIncludingAnExplicitClear() {
        listOf("Latest local note", null).forEach { latestNote ->
            val id = "book-note-local-${UUID.randomUUID()}"
            val staleLocal = Book(bookUrl = "$id-local", name = "$id-before", author = "Note fixture",
                type = BookType.text or BookType.local, userNote = "Stale local note")
            val onlineSnapshot = Book(bookUrl = "$id-online", name = id, author = "Note fixture",
                coverUrl = "source-cover", intro = "Source introduction", userNote = "Online note")
            dao.insert(staleLocal)
            dao.updateUserNote(staleLocal.bookUrl, latestNote)
            try {
                val merged = LocalBook.mergeBook(staleLocal, onlineSnapshot)
                assertEquals(latestNote, merged.userNote)
                assertEquals(latestNote, dao.getBook(staleLocal.bookUrl)?.userNote)
                assertEquals(id, merged.name)
                assertEquals("Source introduction", merged.intro)
                assertEquals("source-cover", merged.coverUrl)
            } finally {
                dao.getBook(staleLocal.bookUrl)?.let { dao.delete(it) }
            }
        }
    }

    @Test fun localMergeInheritsLatestSourceNoteOnlyWhenTheLocalRecordIsAbsent() {
        listOf("Latest online note", null).forEach { latestNote ->
            val id = "book-note-inherit-${UUID.randomUUID()}"
            val sourceSnapshot = Book(bookUrl = "$id-online", name = id, author = "Note fixture",
                userNote = "Stale online note")
            val newLocal = Book(bookUrl = "$id-local", name = "$id-file", type = BookType.text or BookType.local,
                userNote = "Stale local snapshot note")
            dao.insert(sourceSnapshot)
            dao.updateUserNote(sourceSnapshot.bookUrl, latestNote)
            try {
                val merged = LocalBook.mergeBook(newLocal, sourceSnapshot)
                assertEquals(latestNote, merged.userNote)
                assertEquals(latestNote, dao.getBook(newLocal.bookUrl)?.userNote)
            } finally {
                listOf(sourceSnapshot.bookUrl, newLocal.bookUrl).forEach { url ->
                    dao.getBook(url)?.let { dao.delete(it) }
                }
            }
        }
    }
}
