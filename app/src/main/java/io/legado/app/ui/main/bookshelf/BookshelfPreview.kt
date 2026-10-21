package io.legado.app.ui.main.bookshelf

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.legado.app.data.entities.Book
import io.legado.app.ui.widget.compose.LegadoTheme

@Preview(name = "书架·浅色", widthDp = 390, heightDp = 720)
@Preview(name = "书架·深色", widthDp = 390, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookshelfPreview() {
    LegadoTheme {
        BookshelfContent(listOf(Book(bookUrl = "preview://sample", name = "预览书籍", author = "预览作者",
            totalChapterNum = 120, durChapterIndex = 27, durChapterTitle = "第二十八章")),
            groupId = -1L, layout = 3, scrollRequest = 0, refreshEnabled = true,
            updating = { false }, onRefresh = {}, onOpen = {}, onDetails = {})
    }
}
