package io.legado.app.ui.book.bookmark

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LegadoPageState
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.compose.legadoPressFeedback

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AllBookmarkScreen(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onBookmarkLongClick: (Bookmark, Int) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    loadError: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val groups = remember(bookmarks) {
        bookmarks.groupBy { it.bookName to it.bookAuthor }.toList()
    }
    val motion = LocalLegadoMotion.current

    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (bookmarks.isEmpty()) {
            when {
                loading -> {
                    val loadingDescription = stringResource(R.string.loading)
                    Column(
                        Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (motion.enabled) CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(loadingDescription,
                            modifier = Modifier.semantics {
                                contentDescription = loadingDescription
                                liveRegion = LiveRegionMode.Polite
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                loadError -> LegadoPageState(
                    icon = R.drawable.ic_bookmark,
                    title = stringResource(R.string.error_get_data),
                    iconTint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                else -> LegadoPageState(
                    icon = R.drawable.ic_bookmark,
                    title = stringResource(R.string.empty),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            if (loading || loadError) {
                item(key = "bookmark-load-status") {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(if (loading) R.string.loading else R.string.error_get_data),
                                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (loading) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error
                            )
                            if (!loading) {
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            groups.forEach { (book, items) ->
                val (bookName, author) = book
                stickyHeader(key = "bookmark-book-${bookName.length}:$bookName:$author") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = bookName,
                                        modifier = Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (author.isNotBlank()) {
                                        Text(
                                            text = author,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = items.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                itemsIndexed(items, key = { _, bookmark -> "bookmark-${bookmark.time}" }) { index, bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark) },
                        onLongClick = { onBookmarkLongClick(bookmark, index) },
                        modifier = Modifier.animateItem(fadeInSpec = tween(motion.state),
                            fadeOutSpec = tween(motion.state), placementSpec = tween(motion.state))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactions = remember { MutableInteractionSource() }
    Column(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                .legadoPressFeedback(interactions)
                .combinedClickable(interactionSource = interactions, indication = LocalIndication.current,
                    role = Role.Button, onClick = onClick, onLongClick = onLongClick,
                    onLongClickLabel = stringResource(R.string.edit))
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = bookmark.chapterName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (bookmark.bookText.isNotEmpty()) {
                Text(
                    text = bookmark.bookText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (bookmark.content.isNotEmpty()) {
                Text(
                    text = bookmark.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
