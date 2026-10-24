package io.legado.app.ui.book.toc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.Bookmark
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

@Composable
internal fun BookmarkScreen(
    bookmarks: List<Bookmark>,
    currentChapterIndex: Int,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkLongClick: (Bookmark, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(bookmarks, currentChapterIndex) {
        if (bookmarks.isNotEmpty()) {
            val anchor = bookmarks.indexOfLast { it.chapterIndex < currentChapterIndex }
                .takeIf { it >= 0 }
                ?: bookmarks.indexOfFirst { it.chapterIndex >= currentChapterIndex }.coerceAtLeast(0)
            listState.scrollToItem(anchor)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        if (bookmarks.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.time }) { index, bookmark ->
                BookmarkRow(
                    bookmark = bookmark,
                    onClick = { onBookmarkClick(bookmark) },
                    onLongClick = { onBookmarkLongClick(bookmark, index) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Text(
            text = bookmark.chapterName,
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (bookmark.bookText.isNotEmpty()) {
            Text(
                text = bookmark.bookText,
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (bookmark.content.isNotEmpty()) {
            Text(
                text = bookmark.content,
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookmarkScreenPreview() {
    LegadoTheme {
        BookmarkScreen(
            bookmarks = listOf(
                Bookmark(
                    time = 1L,
                    chapterName = "第 42 章 · 望向星河",
                    bookText = "“他们第一次意识到，星海并没有边界。”",
                    content = "星河、文明与漫长的等待"
                )
            ),
            currentChapterIndex = 0,
            onBookmarkClick = {},
            onBookmarkLongClick = { _, _ -> }
        )
    }
}
