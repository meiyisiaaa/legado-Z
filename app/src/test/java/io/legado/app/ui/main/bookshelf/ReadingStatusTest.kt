package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStatusTest {
    private fun assertStatus(expected: ReadingStatus, book: Book) {
        assertTrue(ReadingStatus.ALL.includes(book))
        assertEquals(listOf(expected), ReadingStatus.entries.filter {
            it != ReadingStatus.ALL && it.includes(book)
        })
    }

    @Test fun newBookAndResetPositionAreNotStartedRegardlessOfTimestamp() {
        assertStatus(ReadingStatus.NOT_STARTED, Book(totalChapterNum = 12))
        assertStatus(ReadingStatus.NOT_STARTED, Book(durChapterTime = Long.MAX_VALUE,
            totalChapterNum = 12, durChapterTitle = "Opened first chapter"))
        assertStatus(ReadingStatus.NOT_STARTED, Book(durChapterIndex = -1, durChapterPos = -1))
    }

    @Test fun positivePositionInFirstChapterIsReading() {
        assertStatus(ReadingStatus.READING, Book(totalChapterNum = 12, durChapterPos = 1))
        assertStatus(ReadingStatus.READING, Book(totalChapterNum = 12, durChapterIndex = 1))
    }

    @Test fun unknownDirectoryWithProgressIsReading() {
        assertStatus(ReadingStatus.READING, Book(totalChapterNum = 0, durChapterIndex = 100))
        assertStatus(ReadingStatus.READING, Book(totalChapterNum = -1, durChapterPos = 1))
    }

    @Test fun reachingFinalIndexIncludesZeroPositionAndStaleIndexBeyondDirectory() {
        assertStatus(ReadingStatus.AT_LAST_CHAPTER, Book(totalChapterNum = 12, durChapterIndex = 11))
        assertStatus(ReadingStatus.AT_LAST_CHAPTER, Book(totalChapterNum = 12, durChapterIndex = 15))
    }

    @Test fun singleChapterRequiresPositiveReadingPosition() {
        assertStatus(ReadingStatus.NOT_STARTED, Book(totalChapterNum = 1))
        assertStatus(ReadingStatus.AT_LAST_CHAPTER, Book(totalChapterNum = 1, durChapterPos = 1))
    }

    @Test fun newlyKnownChaptersReturnTheBookToReading() {
        val book = Book(totalChapterNum = 12, durChapterIndex = 11)
        assertStatus(ReadingStatus.AT_LAST_CHAPTER, book)
        book.totalChapterNum = 13
        assertStatus(ReadingStatus.READING, book)
    }

    @Test fun filteringDoesNotChangePositionOrGroup() {
        val book = Book(totalChapterNum = 12, durChapterIndex = 2, durChapterPos = 40, group = 8)
        val before = book.copy()
        assertFalse(ReadingStatus.AT_LAST_CHAPTER.includes(book))
        assertEquals(before.group, book.group)
        assertEquals(before.durChapterIndex, book.durChapterIndex)
        assertEquals(before.durChapterPos, book.durChapterPos)
        assertEquals(before.totalChapterNum, book.totalChapterNum)
    }
}
