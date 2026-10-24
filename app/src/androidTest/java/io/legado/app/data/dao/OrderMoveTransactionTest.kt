package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrderMoveTransactionTest {
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
    fun bookSourceMoveReadsAllRowsAndPreservesConcurrentMetadata() {
        val dao = database.bookSourceDao
        dao.insert(
            BookSource(bookSourceUrl = "a", bookSourceName = "A", customOrder = 7, enabled = true),
            BookSource(bookSourceUrl = "b", bookSourceName = "hidden", customOrder = 7, enabled = false),
            BookSource(bookSourceUrl = "c", bookSourceName = "C", customOrder = 7, enabled = true),
            BookSource(bookSourceUrl = "d", bookSourceName = "D", customOrder = 7, enabled = true)
        )
        val visibleRows = runBlocking { dao.flowEnabled().first() }
        assertEquals(listOf("a", "c", "d"), visibleRows.map { it.bookSourceUrl })
        val descendingVisibleKeys = visibleRows.asReversed().map { it.bookSourceUrl }
        assertEquals(listOf("d", "c", "a"), descendingVisibleKeys)

        val latest = dao.getBookSource("a")!!.copy(
            bookSourceName = "edited while dragging",
            bookSourceComment = "latest concurrent comment"
        )
        dao.update(latest)

        // The visible descending list moved A before D; in canonical ascending order this is after D.
        dao.moveRelativeTo("a", "d", after = true)

        assertEquals(listOf("b", "c", "d", "a"), dao.allPartForOrder.map { it.bookSourceUrl })
        assertEquals(listOf(0, 1, 2, 3), dao.allPartForOrder.map { it.customOrder })
        assertEquals("edited while dragging", dao.getBookSource("a")?.bookSourceName)
        assertEquals("latest concurrent comment", dao.getBookSource("a")?.bookSourceComment)
        assertFalse(dao.getBookSource("b")!!.enabled)
    }

    @Test
    fun rssSourceMoveKeepsFilteredRowsAndOnlyChangesOrderColumn() {
        val dao = database.rssSourceDao
        dao.insert(
            RssSource(sourceUrl = "a", sourceName = "A", customOrder = 4, enabled = true),
            RssSource(sourceUrl = "b", sourceName = "hidden", customOrder = 4, enabled = false),
            RssSource(sourceUrl = "c", sourceName = "C", customOrder = 4, enabled = true),
            RssSource(sourceUrl = "d", sourceName = "D", customOrder = 4, enabled = true)
        )
        val visibleRows = runBlocking { dao.flowEnabled().first() }
        assertEquals(listOf("a", "c", "d"), visibleRows.map { it.sourceUrl })
        val descendingVisibleKeys = visibleRows.asReversed().map { it.sourceUrl }
        assertEquals(listOf("d", "c", "a"), descendingVisibleKeys)

        dao.update(dao.getByKey("a")!!.copy(
            sourceName = "edited while dragging",
            sourceComment = "latest concurrent comment",
            sourceGroup = "concurrent group"
        ))

        dao.moveRelativeTo("a", "d", after = true)

        assertEquals(listOf("b", "c", "d", "a"), dao.allForOrder.map { it.sourceUrl })
        assertEquals(listOf(0, 1, 2, 3), dao.allForOrder.map { it.customOrder })
        assertEquals("edited while dragging", dao.getByKey("a")?.sourceName)
        assertEquals("latest concurrent comment", dao.getByKey("a")?.sourceComment)
        assertEquals("concurrent group", dao.getByKey("a")?.sourceGroup)
        assertFalse(dao.getByKey("b")!!.enabled)
    }

    @Test
    fun replaceRuleMoveKeepsDisabledRuleAndConcurrentRuleEdits() {
        val dao = database.replaceRuleDao
        dao.insert(
            ReplaceRule(id = 1, name = "A", order = 9, isEnabled = true),
            ReplaceRule(id = 2, name = "hidden", order = 9, isEnabled = false),
            ReplaceRule(id = 3, name = "C", order = 9, isEnabled = true),
            ReplaceRule(id = 4, name = "D", order = 9, isEnabled = true)
        )
        val visibleRows = runBlocking { dao.flowEnabled().first() }
        assertEquals(listOf(1L, 3L, 4L), visibleRows.map { it.id })
        val descendingVisibleKeys = visibleRows.asReversed().map { it.id }
        assertEquals(listOf(4L, 3L, 1L), descendingVisibleKeys)

        dao.update(dao.findById(1)!!.copy(
            name = "edited while dragging",
            replacement = "latest replacement",
            scope = "https://latest.example"
        ))

        dao.moveRelativeTo(1, 4, after = true)

        assertEquals(listOf(2L, 3L, 4L, 1L), dao.allForOrder.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), dao.allForOrder.map { it.order })
        assertEquals("edited while dragging", dao.findById(1)?.name)
        assertEquals("latest replacement", dao.findById(1)?.replacement)
        assertEquals("https://latest.example", dao.findById(1)?.scope)
        assertTrue(dao.findById(2)!!.isEnabled.not())
    }
}
