package io.legado.app.ui.book.changesource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.SearchBook

@Composable
internal fun ChangeChapterSourceContent(
    books: List<SearchBook>,
    listState: LazyListState,
    oldBookUrl: String?,
    revision: Int,
    loadWordCount: Boolean,
    onOpenToc: (SearchBook) -> Unit,
    getScore: (SearchBook) -> Int,
    onSetScore: (SearchBook, Int) -> Unit,
    onTop: (SearchBook) -> Unit,
    onBottom: (SearchBook) -> Unit,
    onEdit: (SearchBook) -> Unit,
    onDisable: (SearchBook) -> Unit,
    onDelete: (SearchBook) -> Unit
) {
    if (books.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.content_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(books, key = { _, book -> book.bookUrl }) { _, book ->
            key(revision, book.bookUrl) {
                ChangeSourceCard(
                    book = book,
                    selected = book.bookUrl == oldBookUrl,
                    loadWordCount = loadWordCount,
                    score = getScore(book),
                    onClick = { onOpenToc(book) },
                    onSetScore = { onSetScore(book, it) },
                    onTop = { onTop(book) },
                    onBottom = { onBottom(book) },
                    onEdit = { onEdit(book) },
                    onDisable = { onDisable(book) },
                    onDelete = { onDelete(book) }
                )
            }
        }
    }
}

@Composable
internal fun ChangeChapterTocContent(
    chapters: List<BookChapter>,
    listState: LazyListState,
    currentIndex: Int,
    onSelect: (BookChapter, String?) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(chapters, key = { index, chapter -> chapter.url.ifBlank { index.toString() } }) { index, chapter ->
            val selected = chapter.index == currentIndex
            Card(
                onClick = { onSelect(chapter, chapters.getOrNull(index + 1)?.url) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else if (chapter.isVolume) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            chapter.title,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (chapter.isVolume || selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!chapter.tag.isNullOrBlank() && !chapter.isVolume) {
                            Text(
                                chapter.tag.orEmpty(),
                                modifier = Modifier.padding(top = 3.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (selected) {
                        Text("Current", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
