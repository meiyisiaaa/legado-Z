package io.legado.app.ui.book.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.help.book.isLocal
import io.legado.app.model.CacheBook

@Composable
internal fun CacheScreen(
    books: List<Book>,
    cacheChapters: Map<String, Set<String>>,
    revision: Int,
    isLoading: Boolean,
    onDownload: (Book) -> Unit,
    onExport: (Int) -> Unit,
    exportProgress: (String) -> Int?,
    exportMessage: (String) -> String?,
    loadError: String? = null,
    onRetry: () -> Unit = {}
) {
    if (books.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                loadError != null -> CacheLoadError(loadError, onRetry)
                isLoading -> CacheLoadingIndicator()
                else -> Text(stringResource(R.string.no_book), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            loadError != null -> item(key = "cache-error") {
                CacheLoadError(
                    error = loadError,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
            isLoading -> item(key = "cache-loading") {
                val loadingDescription = stringResource(R.string.data_loading)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = loadingDescription
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }
        }
        items(books, key = Book::bookUrl) { book ->
            key(revision, book.bookUrl) {
                CacheBookCard(
                    book = book,
                    cachedCount = cacheChapters[book.bookUrl]?.size,
                    onDownload = { onDownload(book) },
                    onExport = { onExport(books.indexOf(book)) },
                    exportProgress = exportProgress(book.bookUrl),
                    exportMessage = exportMessage(book.bookUrl)
                )
            }
        }
    }
}

@Composable
private fun CacheLoadingIndicator() {
    val loadingDescription = stringResource(R.string.data_loading)
    CircularProgressIndicator(
        modifier = Modifier.semantics {
            contentDescription = loadingDescription
            liveRegion = LiveRegionMode.Polite
        }
    )
}

@Composable
private fun CacheLoadError(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun CacheBookCard(
    book: Book,
    cachedCount: Int?,
    onDownload: () -> Unit,
    onExport: () -> Unit,
    exportProgress: Int?,
    exportMessage: String?
) {
    val local = book.isLocal
    val download = CacheBook.cacheBookMap[book.bookUrl]
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(book.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.author_show, book.getRealAuthor()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when {
                            local -> stringResource(R.string.local_book)
                            cachedCount == null -> stringResource(R.string.loading)
                            else -> stringResource(R.string.download_count, cachedCount, book.totalChapterNum)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (!local) {
                    IconButton(onClick = onDownload) {
                        Icon(
                            painter = painterResource(
                                if (download?.isStop() == false) R.drawable.ic_stop_black_24dp else R.drawable.ic_play_24dp
                            ),
                            contentDescription = stringResource(
                                if (download?.isStop() == false) R.string.stop else R.string.start
                            ),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TextButton(onClick = onExport) { Text(stringResource(R.string.export)) }
            }
            if (!exportMessage.isNullOrBlank()) {
                Text(
                    exportMessage,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (exportProgress != null) {
                val total = book.totalChapterNum.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { exportProgress.coerceIn(0, total).toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "$exportProgress / ${book.totalChapterNum}",
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
