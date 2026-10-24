package io.legado.app.data

import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ComicBookshelfGroupTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            instrumentation.targetContext, AppDatabase::class.java
        ).allowMainThreadQueries().addCallback(AppDatabase.dbCallback).build()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun comicGroupSelectsImageFlagsAndExcludesTemporaryReadingBooks() = runBlocking {
        database.bookDao.insert(
            Book(bookUrl = "image", name = "Image", type = BookType.image),
            Book(bookUrl = "local-image", name = "Local image", type = BookType.image or BookType.local),
            Book(bookUrl = "text-image", name = "Text image", type = BookType.image or BookType.text),
            Book(bookUrl = "temporary-image", name = "Temporary image", type = BookType.image or BookType.notShelf),
            Book(bookUrl = "text", name = "Text", type = BookType.text, group = 1),
            Book(bookUrl = "audio", name = "Audio", type = BookType.audio, group = 2),
            Book(bookUrl = "video", name = "Video", type = BookType.video, group = 4),
            Book(bookUrl = "local-text", name = "Local text", type = BookType.text or BookType.local)
        )

        val books = withTimeout(10_000) {
            database.bookDao.flowByGroup(BookGroup.IdImage).first()
        }

        assertEquals(setOf("image", "local-image", "text-image"), books.map { it.bookUrl }.toSet())
    }

    @Test
    fun emptyComicGroupIsVisibleAndItsHiddenPreferenceIsRespected() {
        val emptyGroups = shownGroups().map { it.groupId }
        assertTrue(emptyGroups.contains(BookGroup.IdImage))
        assertFalse(emptyGroups.contains(BookGroup.IdAudio))
        assertFalse(emptyGroups.contains(BookGroup.IdVideo))

        database.bookDao.insert(
            Book(bookUrl = "audio", name = "Audio", type = BookType.audio),
            Book(bookUrl = "video", name = "Video", type = BookType.video)
        )
        val comic = database.bookGroupDao.getByID(BookGroup.IdImage)!!
        database.bookGroupDao.update(comic.copy(show = false))

        val populatedGroups = shownGroups().map { it.groupId }
        assertFalse(populatedGroups.contains(BookGroup.IdImage))
        assertTrue(populatedGroups.contains(BookGroup.IdAudio))
        assertTrue(populatedGroups.contains(BookGroup.IdVideo))
    }

    @Test
    fun onOpenSeedsComicGroupOnceAndPreservesEveryExistingPreference() {
        val dao = database.bookGroupDao
        val seeded = dao.getByID(BookGroup.IdImage)!!
        assertEquals(-7L, seeded.groupId)
        assertEquals("漫画", seeded.groupName)
        assertEquals(-4, seeded.order)
        assertTrue(seeded.show)
        assertEquals(
            "漫画(${instrumentation.targetContext.getString(R.string.manga)})",
            seeded.getManageName(instrumentation.targetContext)
        )

        val customized = seeded.copy(
            groupName = "My comics", cover = "custom-cover", order = 17,
            enableRefresh = false, show = false, bookSort = 3, onlyUpdateRead = true
        )
        dao.update(customized)
        repeat(2) { AppDatabase.dbCallback.onOpen(database.openHelper.writableDatabase) }

        assertEquals(customized, dao.getByID(BookGroup.IdImage))
        assertEquals(1, dao.all.count { it.groupId == BookGroup.IdImage })
    }

    private fun shownGroups(): List<BookGroup> {
        val groups = database.bookGroupDao.show
        val result = AtomicReference<List<BookGroup>>()
        val emitted = CountDownLatch(1)
        val observer = Observer<List<BookGroup>> {
            result.set(it)
            emitted.countDown()
        }
        try {
            instrumentation.runOnMainSync { groups.observeForever(observer) }
            assertTrue("Visible bookshelf groups were not emitted", emitted.await(10, TimeUnit.SECONDS))
            return result.get()
        } finally {
            instrumentation.runOnMainSync { groups.removeObserver(observer) }
        }
    }
}
