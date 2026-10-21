package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.compose.LegadoTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AllBookmarkScreen(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkLongClick: (Bookmark, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(bookmarks) {
        bookmarks.groupBy { it.bookName to it.bookAuthor }.toList()
    }

    if (bookmarks.isEmpty()) {
        Box(modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("—", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEachIndexed { groupIndex, (book, items) ->
            val (bookName, author) = book
            stickyHeader(key = "bookmark-book-$groupIndex-${bookName.hashCode()}-${author.hashCode()}") {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(
                                    text = (groupIndex + 1).toString().padStart(2, '0'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = items.size.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            itemsIndexed(items, key = { _, bookmark -> "bookmark-${bookmark.time}" }) { index, bookmark ->
                BookmarkCard(
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
private fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = bookmark.chapterName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bookmark.bookText.isNotEmpty()) {
                Text(
                    text = bookmark.bookText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (bookmark.content.isNotEmpty()) {
                Text(
                    text = bookmark.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AllBookmarkScreenPreview() {
    LegadoTheme {
        AllBookmarkScreen(
            bookmarks = listOf(
                Bookmark(
                    time = 1L,
                    bookName = "星海回响",
                    bookAuthor = "银河漫游者",
                    chapterName = "第 42 章 · 望向星河",
                    bookText = "他们第一次意识到，星海并没有边界。",
                    content = "星河、文明与漫长的等待"
                )
            ),
            onBookmarkClick = {},
            onBookmarkLongClick = { _, _ -> }
        )
    }
}
