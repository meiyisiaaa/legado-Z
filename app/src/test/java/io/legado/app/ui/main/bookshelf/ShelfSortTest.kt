package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfSortTest {
    @Test
    fun recentReadingAndCombinedUpdateOrderPreserveTheirDifferentMeaning() {
        val read = Book(name = "A", author = "Z", durChapterTime = 30, latestChapterTime = 1)
        val updated = Book(name = "B", author = "A", durChapterTime = 5, latestChapterTime = 40)
        assertEquals(listOf(read, updated), sortShelfBooks(listOf(updated, read), 0))
        assertEquals(listOf(updated, read), sortShelfBooks(listOf(read, updated), 4))
    }

    @Test
    fun titleAuthorAndManualOrderAreAvailableInBothShelfStyles() {
        val a = Book(name = "A", author = "Z", order = 2)
        val b = Book(name = "B", author = "A", order = 1)
        assertEquals(listOf(a, b), sortShelfBooks(listOf(b, a), 2))
        assertEquals(listOf(b, a), sortShelfBooks(listOf(a, b), 5))
        assertEquals(listOf(b, a), sortShelfBooks(listOf(a, b), 3))
    }
}
