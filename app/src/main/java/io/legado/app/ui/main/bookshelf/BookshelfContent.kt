package io.legado.app.ui.main.bookshelf

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isUpError
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.toTimeAgo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookshelfContent(
    entries: List<Any>, groupId: Long, layout: Int, scrollRequest: Int,
    refreshEnabled: Boolean, updating: (Book) -> Boolean,
    onRefresh: () -> Unit, onOpen: (Any) -> Unit, onDetails: (Any) -> Unit,
    revision: Int = 0, isLoading: Boolean = false, error: String? = null,
    onRetry: () -> Unit = onRefresh, appearance: BookshelfAppearance = BookshelfAppearance()
) {
    val holder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val motion = LocalLegadoMotion.current
    val margin = with(LocalDensity.current) { appearance.marginPx.toDp() }
    val clock by produceState(0, appearance.showLastUpdateTime, entries.isEmpty()) {
        if (appearance.showLastUpdateTime && entries.isNotEmpty()) while (isActive) {
            delay(30_000)
            value++
        }
    }
    Surface(Modifier.fillMaxSize()) {
        holder.SaveableStateProvider(groupId) {
            PullToRefreshBox(isRefreshing = false, onRefresh = { if (refreshEnabled) onRefresh() },
                modifier = Modifier.fillMaxSize(),
                state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()) {
                if (isLoading) {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(stringResource(R.string.bookshelf_loading), style = MaterialTheme.typography.titleMedium)
                        repeat(4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Surface(Modifier.size(64.dp, 86.dp), shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh) {}
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(Modifier.fillMaxWidth(0.75f).height(20.dp), shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {}
                                    Surface(Modifier.fillMaxWidth(0.5f).height(14.dp), shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {}
                                }
                            }
                        }
                    }
                } else if (error != null) {
                    Column(Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.bookshelf_load_failed), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                } else if (entries.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_bottom_books_e), null,
                            Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(20.dp))
                        Text(stringResource(R.string.bookshelf_empty), style = MaterialTheme.typography.titleMedium)
                    }
                } else if (layout >= 2) {
                    val state = rememberLazyGridState()
                    LaunchedEffect(scrollRequest) {
                        if (scrollRequest > 0) {
                            if (motion.enabled) state.animateScrollToItem(0) else state.scrollToItem(0)
                        }
                    }
                    LazyVerticalGrid(GridCells.Fixed(layout), state = state,
                        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(margin), verticalArrangement = Arrangement.spacedBy(margin)) {
                        items(entries, key = ::shelfKey) { entry ->
                            ShelfEntry(entry, true, updating, onOpen, onDetails, revision + clock, Modifier.animateItem(
                                fadeInSpec = tween(motion.state), fadeOutSpec = tween(motion.state),
                                placementSpec = tween(motion.state)), appearance, layout == 1)
                        }
                    }
                    if (appearance.fastScroll && entries.size > 12) {
                        ShelfFastScroller(state.firstVisibleItemIndex, entries.size,
                            Modifier.align(Alignment.CenterEnd)) { fraction ->
                            scope.launch { state.scrollToItem((fraction * entries.lastIndex).toInt()) }
                        }
                    }
                } else {
                    val state = rememberLazyListState()
                    LaunchedEffect(scrollRequest) {
                        if (scrollRequest > 0) {
                            if (motion.enabled) state.animateScrollToItem(0) else state.scrollToItem(0)
                        }
                    }
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(margin)) {
                        items(entries, key = ::shelfKey) { entry ->
                            ShelfEntry(entry, false, updating, onOpen, onDetails, revision + clock, Modifier.animateItem(
                                fadeInSpec = tween(motion.state), fadeOutSpec = tween(motion.state),
                                placementSpec = tween(motion.state)), appearance, layout == 1)
                        }
                    }
                    if (appearance.fastScroll && entries.size > 12) {
                        ShelfFastScroller(state.firstVisibleItemIndex, entries.size,
                            Modifier.align(Alignment.CenterEnd)) { fraction ->
                            scope.launch { state.scrollToItem((fraction * entries.lastIndex).toInt()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfFastScroller(first: Int, total: Int, modifier: Modifier, onSeek: (Float) -> Unit) {
    val fraction = first.toFloat() / (total - 1)
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.bookshelf_fast_scroll_accessibility)
    Canvas(modifier.width(48.dp).fillMaxHeight().semantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        setProgress { onSeek(it.coerceIn(0f, 1f)); true }
    }.pointerInput(total) {
        detectDragGestures(onDragStart = { onSeek((it.y / size.height).coerceIn(0f, 1f)) }) { change, _ ->
            change.consume()
            onSeek((change.position.y / size.height).coerceIn(0f, 1f))
        }
    }) {
        val thumb = 36.dp.toPx().coerceAtMost(size.height)
        val top = fraction * (size.height - thumb)
        drawLine(color, Offset(size.width - 6.dp.toPx(), top),
            Offset(size.width - 6.dp.toPx(), top + thumb), 4.dp.toPx(), StrokeCap.Round)
    }
}

private fun shelfKey(entry: Any): String = when (entry) {
    is Book -> "book:${entry.name}\u0000${entry.author}"
    is BookGroup -> "group:${entry.groupId}"
    else -> error("Unsupported shelf entry")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfEntry(entry: Any, grid: Boolean, updating: (Book) -> Boolean,
    onOpen: (Any) -> Unit, onDetails: (Any) -> Unit, revision: Int, modifier: Modifier,
    appearance: BookshelfAppearance, compactList: Boolean) {
    val motion = LocalLegadoMotion.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && motion.enabled) 0.985f else 1f,
        tween(motion.press), label = "bookshelf press")
    val clickable = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(MaterialTheme.shapes.extraSmall).combinedClickable(
            interactionSource = interactions, indication = if (motion.enabled) LocalIndication.current else null,
            onClick = { onOpen(entry) }, onLongClick = { onDetails(entry) },
            onLongClickLabel = stringResource(R.string.bookshelf_long_press_action))
    when (entry) {
        is Book -> {
            val bookModifier = clickable.semantics(mergeDescendants = true) { contentDescription = entry.name }
            val isUpdating = remember(entry, revision) { !entry.isLocal && updating(entry) }
            val status = when {
                entry.isUpError -> stringResource(R.string.update_book_fail)
                appearance.showLastUpdateTime && !entry.isLocal -> entry.latestChapterTime.toTimeAgo()
                else -> ""
            }
            if (grid) Column(bookModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Box {
                    BookCover(entry, Modifier.fillMaxWidth())
                    if (appearance.bookName == 2) CoverTitle(entry.name, Modifier.align(Alignment.BottomCenter))
                    BookUpdateBadge(entry, isUpdating, appearance, Modifier.align(Alignment.TopEnd))
                }
                if (appearance.bookName == 0) GridTitle(entry.name)
            } else Row(bookModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BookCover(entry, Modifier.width(if (compactList) 48.dp else 66.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BookUpdateBadge(entry, isUpdating, appearance, Modifier.padding(start = 8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_author), null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        if (compactList) {
                            Text(entry.author, Modifier.widthIn(max = 112.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!entry.durChapterTitle.isNullOrBlank()) {
                                Text(" · ", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(entry.durChapterTitle.orEmpty(), Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else Spacer(Modifier.weight(1f))
                        } else Text(entry.author, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (status.isNotBlank()) Text(status, Modifier.padding(start = 6.dp).widthIn(max = 96.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.isUpError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!compactList) BookMetadataLine(R.drawable.ic_history, entry.durChapterTitle.orEmpty())
                    BookMetadataLine(R.drawable.ic_book_last, entry.latestChapterTitle.orEmpty())
                }
            }
        }
        is BookGroup -> {
            val groupModifier = clickable.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = entry.groupName }
            if (grid) Column(groupModifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Box {
                    GroupCover(entry, Modifier.fillMaxWidth())
                    if (appearance.bookName == 2) CoverTitle(entry.groupName, Modifier.align(Alignment.BottomCenter))
                }
                if (appearance.bookName == 0) GridTitle(entry.groupName)
            } else Row(groupModifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GroupCover(entry, Modifier.width(66.dp))
                Spacer(Modifier.width(12.dp))
                Text(entry.groupName, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GridTitle(name: String) {
    Text(name, Modifier.fillMaxWidth().padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CoverTitle(name: String, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f)) {
        Text(name, Modifier.padding(horizontal = 6.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookMetadataLine(icon: Int, text: String) {
    if (text.isBlank()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookUpdateBadge(book: Book, updating: Boolean, appearance: BookshelfAppearance, modifier: Modifier) {
    val updatingDescription = stringResource(R.string.toc_updateing)
    if (updating) {
        if (LocalLegadoMotion.current.enabled) CircularProgressIndicator(modifier.size(22.dp)
            .semantics { contentDescription = updatingDescription }, strokeWidth = 2.dp)
        else Icon(painterResource(R.drawable.ic_refresh_black_24dp), updatingDescription, modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary)
        return
    }
    val unread = book.getUnreadChapterNum()
    if (!appearance.showUnread || unread <= 0) return
    val fresh = book.lastCheckCount > 0
    val description = if (fresh) {
        stringResource(R.string.bookshelf_unread_with_new_chapters, unread, book.lastCheckCount)
    } else {
        stringResource(R.string.bookshelf_unread_chapters, unread)
    }
    Surface(modifier.semantics { contentDescription = description }, shape = MaterialTheme.shapes.extraSmall,
        color = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (fresh) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(unread.toString(), Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (fresh) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BookCover(book: Book, modifier: Modifier) {
    if (LocalInspectionMode.current) {
        Surface(modifier.aspectRatio(0.75f), shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                Text(book.name, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val path = book.getDisplayCover()
    AndroidView(factory = { CoverImageView(it) }, modifier = modifier.aspectRatio(0.75f)
        .clip(MaterialTheme.shapes.extraSmall), update = {
        val identity = listOf(path, book.name, book.author)
        if (it.tag != identity) { it.tag = identity; it.load(book, false) }
        it.contentDescription = null
        it.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })
}

@Composable
private fun GroupCover(group: BookGroup, modifier: Modifier) {
    if (LocalInspectionMode.current) {
        Surface(modifier.aspectRatio(0.75f), color = MaterialTheme.colorScheme.surfaceContainerHigh) {}
        return
    }
    AndroidView(factory = { CoverImageView(it) }, modifier = modifier.aspectRatio(0.75f)
        .clip(MaterialTheme.shapes.extraSmall), update = {
        val identity = listOf(group.groupId, group.cover)
        if (it.tag != identity) { it.tag = identity; it.load(group.cover) }
        it.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    })
}
