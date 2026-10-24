package io.legado.app.ui.book.manage

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookshelfManageContentSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bookSelectionAndRowActionsIncludeTheBookName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val book = Book(bookUrl = "book://sample", name = "示例书")
        var selectedGroupBook: Book? = null
        var deletedBook: Book? = null

        composeRule.setContent {
            LegadoTheme {
                BookshelfManageContent(
                    books = listOf(book),
                    selected = setOf(book.bookUrl),
                    groups = listOf(BookGroup(groupId = 1, groupName = "稍后阅读")),
                    canReorder = false,
                    openInfoOnTitle = false,
                    onToggleSelection = {},
                    onOpenBook = {},
                    onDeleteBook = { deletedBook = it },
                    onSelectGroup = { selectedGroupBook = it },
                    onMove = { _, _ -> },
                    onMoveFinished = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(book.name).assertIsOn()
        composeRule.onNodeWithContentDescription("${book.name}, ${context.getString(R.string.group)}")
            .performClick()
        composeRule.onNodeWithContentDescription("${book.name}, ${context.getString(R.string.delete)}")
            .performClick()
        composeRule.runOnIdle {
            assertEquals(book, selectedGroupBook)
            assertEquals(book, deletedBook)
        }
    }
}
