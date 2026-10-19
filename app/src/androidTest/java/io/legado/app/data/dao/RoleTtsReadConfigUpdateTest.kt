package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoleTtsReadConfigUpdateTest {
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun roleConfigUpdatesPreserveProgressAndOtherReadConfig() {
        val book = Book(
            bookUrl = "role-tts-test",
            name = "多角色测试书",
            author = "测试",
            durChapterPos = 173,
            readConfig = Book.ReadConfig(
                reverseToc = true,
                ttsEngine = "engine-before",
                roleTtsEnabled = false,
                roleTtsConfigJson = "old-config"
            )
        )
        database.bookDao.insert(book)

        database.bookDao.updateRoleTtsConfig(book.bookUrl, "new-config")
        database.bookDao.updateRoleTtsEnabled(book.bookUrl, true)

        val saved = database.bookDao.getBook(book.bookUrl)!!
        assertEquals(173, saved.durChapterPos)
        assertTrue(saved.config.reverseToc)
        assertEquals("engine-before", saved.config.ttsEngine)
        assertEquals("new-config", saved.config.roleTtsConfigJson)
        assertTrue(saved.config.roleTtsEnabled)
    }
}
