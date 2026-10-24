package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.help.book.updateTo
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookNotePersistenceTest {
    private lateinit var database: AppDatabase

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun closeDatabase() = database.close()

    @Test fun editingNoteAndMetadataPreservesNewerProgressAndReadingOptions() {
        val draft = Book(bookUrl = "note-book", name = "Book", author = "Author", intro = "Source intro",
            userNote = "Before", variable = """{"source":"value"}""",
            durChapterIndex = 1, durChapterPos = 20)
        database.bookDao.insert(draft)
        val current = draft.copy(durChapterIndex = 12, durChapterPos = 321, group = 8,
            readConfig = Book.ReadConfig(reverseToc = true, roleTtsEnabled = true))
        database.bookDao.update(current)

        assertTrue(database.bookDao.updateBookInfo(draft.copy(customIntro = "Edited intro"), "  My note\nSecond line  "))
        val saved = database.bookDao.getBook(draft.bookUrl)!!
        assertEquals("  My note\nSecond line  ", saved.userNote)
        assertEquals(12, saved.durChapterIndex)
        assertEquals(321, saved.durChapterPos)
        assertEquals(8L, saved.group)
        assertTrue(saved.config.reverseToc)
        assertTrue(saved.config.roleTtsEnabled)
        assertEquals("Source intro", saved.intro)
        assertEquals("Edited intro", saved.customIntro)
        assertEquals(draft.variable, saved.variable)

        assertTrue(database.bookDao.updateBookInfo(draft, " \n\t"))
        assertNull(database.bookDao.getBook(draft.bookUrl)?.userNote)
        assertFalse(database.bookDao.updateBookInfo(draft.copy(bookUrl = "missing"), "not saved"))
    }

    @Test fun staleWholeBookUpdatesAndLegacyWebSavesCannotErasePrivateNote() {
        val stale = Book(bookUrl = "note-book", name = "Book", author = "Author", userNote = "Before")
        database.bookDao.insert(stale)
        database.bookDao.updateUserNote(stale.bookUrl, "Latest private note")
        database.bookDao.update(stale.copy(durChapterPos = 74))
        assertEquals("Latest private note", database.bookDao.getBook(stale.bookUrl)?.userNote)
        database.bookDao.insert(stale.copy(userNote = null))
        assertEquals("Latest private note", database.bookDao.getBook(stale.bookUrl)?.userNote)

        val legacy = GSON.fromJsonObject<Book>("""{"bookUrl":"note-book","name":"Book","author":"Author"}""").getOrThrow()
        database.bookDao.saveBookPreservingUserNote(legacy)
        assertEquals("Latest private note", database.bookDao.getBook(stale.bookUrl)?.userNote)
        database.bookDao.saveBookPreservingUserNote(legacy.copy(userNote = "Untrusted replacement"))
        assertEquals("Latest private note", database.bookDao.getBook(stale.bookUrl)?.userNote)
        database.bookDao.saveBookPreservingUserNote(legacy.copy(bookUrl = "replacement-source"))
        assertEquals("Latest private note", database.bookDao.getBook("replacement-source")?.userNote)
        val current = database.bookDao.getBook("replacement-source")!!
        database.bookDao.updateUserNote(current.bookUrl, "Note edited during source refresh")
        database.bookDao.replace(current.copy(userNote = null), current.copy(bookUrl = "redirected-source", userNote = null))
        assertEquals("Note edited during source refresh", database.bookDao.getBook("redirected-source")?.userNote)
    }

    @Test fun batchUpdatesPreserveEachBooksOwnLatestNote() {
        val first = Book(bookUrl = "first", name = "First", userNote = "First note")
        val second = Book(bookUrl = "second", name = "Second", userNote = "Second note")
        database.bookDao.insert(first, second)
        database.bookDao.updateUserNote(first.bookUrl, "First latest")
        database.bookDao.updateUserNote(second.bookUrl, "Second latest")
        database.bookDao.update(first.copy(canUpdate = false), second.copy(canUpdate = false))
        assertEquals("First latest", database.bookDao.getBook(first.bookUrl)?.userNote)
        assertEquals("Second latest", database.bookDao.getBook(second.bookUrl)?.userNote)
    }

    @Test fun fullBookBackupRoundTripAndSourceCopiesKeepTheNoteSeparateFromIntroAndVariables() {
        val book = Book(bookUrl = "old-source", name = "Book", author = "Author", intro = "Source intro",
            customIntro = "Custom intro", userNote = "Private note\n第二段", variable = """{"key":"value"}""")
        val restored = GSON.fromJsonArray<Book>(GSON.toJson(listOf(book))).getOrThrow().single()
        assertEquals(book.userNote, restored.userNote)
        assertEquals("Source intro", restored.intro)
        assertEquals("Custom intro", restored.customIntro)
        assertEquals(book.variable, restored.variable)
        assertEquals(book.userNote, book.copy().userNote)
        assertEquals(book.userNote, book.migrateTo(Book(bookUrl = "new-source"), emptyList()).userNote)
        assertEquals(book.userNote, book.updateTo(Book(bookUrl = "refreshed-source")).userNote)

        database.bookDao.insert(book)
        database.bookDao.updateUserNote(book.bookUrl, "Current note")
        database.bookDao.updateEntities(restored)
        assertEquals("Private note\n第二段", database.bookDao.getBook(book.bookUrl)?.userNote)
        val oldBackup = GSON.fromJsonObject<Book>("""{"bookUrl":"old-source","name":"Book","author":"Author"}""").getOrThrow()
        database.bookDao.updateEntities(oldBackup)
        assertNull(database.bookDao.getBook(book.bookUrl)?.userNote)
    }

    @Test fun singleBookSharingExcludesThePrivateNoteAndPreservesAllOtherFieldsAndBackup() {
        val book = Book(bookUrl = "share-book", name = "Shared book", author = "Author",
            intro = "Source intro", customIntro = "Custom intro", userNote = "Private reading note",
            variable = """{"key":"source-value"}""", durChapterIndex = 7, durChapterPos = 89).apply {
            infoHtml = "<p>Source details</p>"
            tocHtml = "<p>Source contents</p>"
            downloadUrls = listOf("https://example.org/download")
        }
        val backup = GSON.toJsonTree(book).asJsonObject
        val shared = GSON.fromJsonObject<com.google.gson.JsonObject>(book.toShareJson()).getOrThrow()
        assertFalse(shared.has("userNote"))
        assertEquals("Private reading note", book.userNote)
        assertEquals("Private reading note", backup.get("userNote").asString)
        assertEquals("Private reading note", GSON.fromJsonObject<Book>(GSON.toJson(book)).getOrThrow().userNote)
        backup.remove("userNote")
        assertEquals(backup, shared)
        assertEquals("<p>Source details</p>", shared.get("infoHtml").asString)
        assertEquals("<p>Source contents</p>", shared.get("tocHtml").asString)
        assertEquals("https://example.org/download", shared.getAsJsonArray("downloadUrls")[0].asString)
    }
}
