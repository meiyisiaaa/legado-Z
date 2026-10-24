package io.legado.app.ui.book.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.image.CoverImageView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun ExploreShowContent(
    books: List<SearchBook>,
    listState: LazyListState,
    revision: Int,
    page: Int,
    previousPage: Int,
    bottomLoading: Boolean,
    topLoading: Boolean,
    bottomHasMore: Boolean,
    topHasMore: Boolean,
    bottomError: String?,
    topError: String?,
    isBookInShelf: (SearchBook) -> Boolean,
    onBookClick: (SearchBook) -> Unit,
    onLoadBottom: () -> Unit,
    onLoadTop: () -> Unit,
    onRetryBottom: () -> Unit,
    onRetryTop: () -> Unit
) {
    var errorDialog by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(listState, books.size, revision, bottomLoading, topLoading, bottomHasMore, topHasMore, previousPage) {
        var previousTopPosition: Pair<Int, Int>? = null
        snapshotFlow {
            val layout = listState.layoutInfo
            val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(first, listState.firstVisibleItemScrollOffset, last)
        }.distinctUntilChanged().collectLatest { (first, topOffset, last) ->
            if (bottomHasMore && !bottomLoading && last >= books.lastIndex - 2) onLoadBottom()
            val currentTopPosition = first to topOffset
            val previous = previousTopPosition
            val movedTowardTop = previous != null && (
                first < previous.first ||
                    (first == 0 && previous.first == 0 && currentTopPosition.second < previous.second)
                )
            if (movedTowardTop && topHasMore && previousPage > 1 && !topLoading && first <= 1) onLoadTop()
            previousTopPosition = currentTopPosition
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (previousPage > 1 || topLoading || topError != null) {
            item(key = "previous-page") {
                ExploreLoadMoreRow(
                    loading = topLoading,
                    error = topError,
                    hasMore = topHasMore,
                    text = if (topError != null) "Earlier results failed to load"
                    else "Load earlier results · page ${(previousPage - 1).coerceAtLeast(1)}",
                    onClick = { topError?.let { errorDialog = it to true } ?: onRetryTop() }
                )
            }
        }
        items(books, key = SearchBook::bookUrl) { book ->
            key(revision, book.bookUrl) {
                ExploreBookCard(book, isBookInShelf(book), onBookClick = { onBookClick(book) })
            }
        }
        item(key = "next-page") {
            val empty = books.isEmpty() && !bottomLoading && bottomError == null
            ExploreLoadMoreRow(
                loading = bottomLoading,
                error = bottomError,
                hasMore = bottomHasMore,
                text = when {
                    empty -> stringResource(R.string.empty)
                    bottomHasMore -> "Load more · page ${page + 1}"
                    else -> stringResource(R.string.bottom_line)
                },
                onClick = { bottomError?.let { errorDialog = it to false } ?: onRetryBottom() }
            )
        }
    }

    errorDialog?.let { (message, isTopError) ->
        AlertDialog(
            onDismissRequest = { errorDialog = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    errorDialog = null
                    if (isTopError) onRetryTop() else onRetryBottom()
                }) { Text(stringResource(R.string.retry)) }
            },
            dismissButton = { TextButton(onClick = { errorDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ExploreLoadMoreRow(
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            loading -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error != null -> Text(stringResource(R.string.error_load_msg, stringResource(R.string.retry)), color = MaterialTheme.colorScheme.error)
            else -> Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreBookCard(book: SearchBook, inBookshelf: Boolean, onBookClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onBookClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AndroidView(
                factory = { CoverImageView(it).apply { load(book, AppConfig.loadCoverOnlyWifi) } },
                update = { it.load(book, AppConfig.loadCoverOnlyWifi) },
                modifier = Modifier.size(width = 78.dp, height = 108.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        book.name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (inBookshelf) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(
                    stringResource(R.string.author_show, book.author),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.getKindList().takeIf { it.isNotEmpty() }?.let { kinds ->
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        kinds.take(3).forEach { kind ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    kind,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                book.latestChapterTitle?.takeIf { it.isNotEmpty() }?.let { latestChapterTitle ->
                    Text(
                        stringResource(R.string.lasted_show, latestChapterTitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    book.trimIntro(context),
                    modifier = Modifier.weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
