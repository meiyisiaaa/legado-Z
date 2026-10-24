package io.legado.app.ui.book.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isLocal

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfManageContent(
    books: List<Book>,
    selected: Set<String>,
    groups: List<BookGroup>,
    canReorder: Boolean,
    openInfoOnTitle: Boolean,
    onToggleSelection: (Book) -> Unit,
    onOpenBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onSelectGroup: (Book) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveFinished: () -> Unit
) {
    if (books.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pointerInput(canReorder, books.size) {
            var draggingIndex = -1
            var draggedIndices = mutableSetOf<Int>()
            fun indexAt(y: Float): Int = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                y >= it.offset && y < it.offset + it.size
            }?.index ?: -1
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    draggingIndex = indexAt(offset.y)
                    draggedIndices = mutableSetOf()
                    if (!canReorder && draggingIndex >= 0) {
                        draggedIndices.add(draggingIndex)
                        books.getOrNull(draggingIndex)?.let(onToggleSelection)
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    val target = indexAt(change.position.y)
                    if (target >= 0) {
                        if (canReorder && draggingIndex >= 0 && target != draggingIndex) {
                            onMove(draggingIndex, target)
                            draggingIndex = target
                        } else if (!canReorder && draggedIndices.add(target)) {
                            books.getOrNull(target)?.let(onToggleSelection)
                        }
                    }
                },
                onDragEnd = {
                    if (canReorder) onMoveFinished()
                    draggingIndex = -1
                },
                onDragCancel = {
                    if (canReorder) onMoveFinished()
                    draggingIndex = -1
                }
            )
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(books, key = { _, book -> book.bookUrl }) { _, book ->
            val groupNames = remember(book.group, groups) {
                groups.filter { it.groupId > 0 && it.groupId and book.group > 0 }
                    .joinToString(", ") { it.groupName }
            }
            val groupLabel = stringResource(R.string.group)
            val deleteLabel = stringResource(R.string.delete)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)
                    .clickable { onToggleSelection(book) },
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (book.bookUrl in selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = book.bookUrl in selected,
                            onCheckedChange = { onToggleSelection(book) },
                            modifier = Modifier.semantics { contentDescription = book.name }
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                            Text(
                                text = book.name,
                                modifier = if (openInfoOnTitle) Modifier.clickable { onOpenBook(book) } else Modifier,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (book.author.isNotEmpty()) {
                                Text(
                                    book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (canReorder) {
                            Icon(painterResource(R.drawable.ic_baseline_drag_handle_24), "拖动调整顺序",
                                Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 46.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (book.isLocal) androidx.compose.ui.res.stringResource(R.string.local_book) else book.originName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = groupNames.ifEmpty { androidx.compose.ui.res.stringResource(R.string.no_group) },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { onSelectGroup(book) },
                            modifier = Modifier.semantics { contentDescription = "${book.name}, $groupLabel" }
                        ) {
                            Text(androidx.compose.ui.res.stringResource(R.string.group))
                        }
                        TextButton(
                            onClick = { onDeleteBook(book) },
                            modifier = Modifier.semantics { contentDescription = "${book.name}, $deleteLabel" }
                        ) {
                            Text(androidx.compose.ui.res.stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}
