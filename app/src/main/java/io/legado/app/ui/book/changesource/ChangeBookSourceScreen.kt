package io.legado.app.ui.book.changesource

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook

@Composable
internal fun ChangeBookSourceScreen(
    books: List<SearchBook>,
    listState: LazyListState,
    oldBookUrl: String?,
    revision: Int,
    loadWordCount: Boolean,
    onChangeTo: (SearchBook) -> Unit,
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
        items(books, key = SearchBook::bookUrl) { book ->
            val selected = book.bookUrl == oldBookUrl
            key(revision, book.bookUrl) {
                ChangeSourceCard(
                    book = book,
                    selected = selected,
                    loadWordCount = loadWordCount,
                    score = getScore(book),
                    onClick = { if (!selected) onChangeTo(book) },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChangeSourceCard(
    book: SearchBook,
    selected: Boolean,
    loadWordCount: Boolean,
    score: Int,
    onClick: () -> Unit,
    onSetScore: (Int) -> Unit,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    onEdit: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = { menuExpanded = true }
        ),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    book.originName,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    Text(
                        "Current source",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(
                book.getDisplayLastChapterTitle().orEmpty(),
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (loadWordCount && !book.chapterWordCountText.isNullOrBlank()) {
                Text(
                    book.chapterWordCountText.orEmpty(),
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (loadWordCount && book.respondTime >= 0) {
                Text(
                    stringResource(R.string.respondTime, book.respondTime),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSetScore(if (score > 0) 0 else 1) }) {
                    Text(if (score > 0) "Recommended ✓" else "Recommend")
                }
                TextButton(onClick = { onSetScore(if (score < 0) 0 else -1) }) {
                    Text(if (score < 0) "Not recommended ✓" else "Not recommended")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { menuExpanded = true }) { Text("More") }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.to_top)) }, onClick = { menuExpanded = false; onTop() })
                DropdownMenuItem(text = { Text(stringResource(R.string.to_bottom)) }, onClick = { menuExpanded = false; onBottom() })
                DropdownMenuItem(text = { Text(stringResource(R.string.edit_source)) }, onClick = { menuExpanded = false; onEdit() })
                DropdownMenuItem(text = { Text(stringResource(R.string.disable_book_source)) }, onClick = { menuExpanded = false; onDisable() })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuExpanded = false; onDelete() })
            }
        }
    }
}
