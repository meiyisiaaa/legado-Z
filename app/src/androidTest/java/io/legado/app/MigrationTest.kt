package io.legado.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate90To91AddsNullableBookNoteWithoutChangingProgressOrReadConfig() {
        helper.createDatabase(TEST_DB, 90).apply {
            execSQL(
                """INSERT INTO books (bookUrl, name, author, intro, durChapterIndex, durChapterPos, readConfig)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""",
                arrayOf<Any?>("note-migration", "Migration book", "Author", "Source intro", 12, 321,
                    """{"reverseToc":true,"roleTtsEnabled":true}""")
            )
            close()
        }
        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, TEST_DB
        ).addMigrations(*DatabaseMigrations.migrations).build()
        try {
            val book = database.bookDao.getBook("note-migration")!!
            assertNull(book.userNote)
            assertEquals("Source intro", book.intro)
            assertEquals(12, book.durChapterIndex)
            assertEquals(321, book.durChapterPos)
            assertTrue(book.config.reverseToc)
            assertTrue(book.config.roleTtsEnabled)
            database.bookDao.updateUserNote(book.bookUrl, "New note")
            assertEquals("New note", database.bookDao.getBook(book.bookUrl)?.userNote)
        } finally {
            database.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        // Create earliest version of the database.
        helper.createDatabase(TEST_DB, 50).apply {
            close()
        }

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
            TEST_DB
        ).addMigrations(*DatabaseMigrations.migrations)
            .build().apply {
                openHelper.writableDatabase
                close()
            }
    }

    @Test
    @Throws(IOException::class)
    fun migrate89To90AddsNullableMainJsWithoutLosingSources() {
        helper.createDatabase(TEST_DB, 89).apply {
            execSQL(
                """INSERT INTO `book_sources`
                    (`bookSourceUrl`, `bookSourceName`, `bookSourceType`, `lastUpdateTime`, `respondTime`, `weight`)
                    VALUES (?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf<Any?>("https://example.org/source", "Existing source", 0, 1L, 2L, 3)
            )
            execSQL(
                """INSERT INTO `books`
                    (`bookUrl`, `name`, `author`, `durChapterIndex`, `durChapterPos`, `readConfig`)
                    VALUES (?, ?, ?, ?, ?, ?)""".trimIndent(),
                arrayOf<Any?>(
                    "https://example.org/book",
                    "Existing book",
                    "Existing author",
                    17,
                    238,
                    """{"reverseToc":true,"pageAnim":2,"reSegment":true,"delTag":4}"""
                )
            )
            close()
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(*DatabaseMigrations.migrations)
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase
            val columns = mutableSetOf<String>()
            sqlite.query("PRAGMA table_info(`book_sources`)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameColumn)
            }
            assertTrue("mainJs column missing after 89→90", "mainJs" in columns)

            sqlite.query(
                "SELECT bookSourceName, mainJs FROM book_sources WHERE bookSourceUrl = ?",
                arrayOf("https://example.org/source")
            ).use { cursor ->
                assertTrue("existing source was lost", cursor.moveToFirst())
                assertEquals("Existing source", cursor.getString(0))
                assertNull(cursor.getString(1))
            }

            val book = database.bookDao.getBook("https://example.org/book")
            assertTrue("existing book was lost", book != null)
            assertEquals("Existing book", book?.name)
            assertEquals("Existing author", book?.author)
            assertEquals(17, book?.durChapterIndex)
            assertEquals(238, book?.durChapterPos)
            assertEquals(true, book?.readConfig?.reverseToc)
            assertEquals(2, book?.readConfig?.pageAnim)
            assertEquals(true, book?.readConfig?.reSegment)
            assertEquals(4L, book?.readConfig?.delTag)
        } finally {
            database.close()
        }
    }
}
