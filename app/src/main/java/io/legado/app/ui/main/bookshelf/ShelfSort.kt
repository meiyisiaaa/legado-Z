package io.legado.app.ui.main.bookshelf

import io.legado.app.data.entities.Book
import io.legado.app.utils.cnCompare
import kotlin.math.max

internal fun sortShelfBooks(books: List<Book>, sort: Int): List<Book> = when (sort) {
    1 -> books.sortedByDescending { it.latestChapterTime }
    2 -> books.sortedWith { a, b -> a.name.cnCompare(b.name) }
    3 -> books.sortedBy { it.order }
    4 -> books.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
    5 -> books.sortedWith { a, b -> a.author.cnCompare(b.author) }
    else -> books.sortedByDescending { it.durChapterTime }
}
