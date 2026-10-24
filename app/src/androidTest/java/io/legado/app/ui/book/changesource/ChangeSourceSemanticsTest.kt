package io.legado.app.ui.book.changesource

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.widget.compose.LegadoTheme
import org.junit.Rule
import org.junit.Test

class ChangeSourceSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun currentSourceAndScoreActionsExposeSelectionState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val likeLabel = context.getString(R.string.like_source)
        val notLikeLabel = context.getString(R.string.not_like_source)

        compose.setContent {
            LegadoTheme {
                var score by remember { mutableIntStateOf(0) }
                ChangeSourceCard(
                    book = SearchBook(bookUrl = "source://one", originName = "Source one", author = "Author"),
                    selected = true,
                    loadWordCount = false,
                    score = score,
                    onClick = {},
                    onSetScore = { score = it },
                    onTop = {},
                    onBottom = {},
                    onEdit = {},
                    onDisable = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.current_source)).assertIsSelected()
        compose.onNodeWithText(likeLabel, substring = true).assertIsNotSelected().performClick()
        compose.onNodeWithText(likeLabel, substring = true).assertIsSelected()
        compose.onNodeWithText(notLikeLabel, substring = true).assertIsNotSelected().performClick()
        compose.onNodeWithText(likeLabel, substring = true).assertIsNotSelected()
        compose.onNodeWithText(notLikeLabel, substring = true).assertIsSelected()
    }
}
