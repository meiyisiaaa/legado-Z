package io.legado.app.ui.book.toc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceBook
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ChapterListUiState(
    val chapters: List<BookChapter> = emptyList(),
    val searchKey: String? = null,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val cachedFileNames: Set<String> = emptySet(),
    val currentChapterIndex: Int = 0,
    val currentChapterInfo: String = "",
    val isLocalBook: Boolean = false,
    val countWords: Boolean = false,
    val replaceRules: List<ReplaceRule>? = null,
    val replaceBook: ReplaceBook? = null,
    val useReplace: Boolean = false,
    val replaceRevision: Int = 0
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChapterListScreen(
    state: ChapterListUiState,
    onChapterClick: (BookChapter) -> Unit,
    onChapterLongClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motionEnabled = LocalLegadoMotion.current.enabled
    val loadingDescription = stringResource(R.string.data_loading)
    val loadError = state.loadError

    fun scrollToItem(index: Int) {
        scope.launch {
            if (motionEnabled) listState.animateScrollToItem(index.coerceAtLeast(0))
            else listState.scrollToItem(index.coerceAtLeast(0))
        }
    }

    LaunchedEffect(state.chapters, state.currentChapterIndex) {
        if (state.chapters.isNotEmpty()) {
            val anchor = state.chapters.indexOfLast { it.index < state.currentChapterIndex }
                .takeIf { it >= 0 }
                ?: state.chapters.indexOfFirst { it.index >= state.currentChapterIndex }.coerceAtLeast(0)
            listState.scrollToItem(anchor)
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp).semantics {
                                contentDescription = loadingDescription
                                liveRegion = LiveRegionMode.Polite
                            },
                            strokeWidth = 2.dp
                        )
                    }
                }
                loadError != null -> item(key = "load-error") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = loadError,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                state.chapters.isEmpty() -> item(key = "empty") {
                    val emptyMessage = if (state.searchKey.isNullOrBlank()) {
                        R.string.chapter_list_empty
                    } else {
                        R.string.chapter_search_empty
                    }
                    Text(
                        stringResource(emptyMessage),
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(state.chapters, key = { _, chapter -> chapter.index }) { _, chapter ->
                ChapterRow(
                    chapter = chapter,
                    currentChapterIndex = state.currentChapterIndex,
                    isLocalBook = state.isLocalBook,
                    cached = state.isLocalBook || chapter.isVolume || chapter.getFileName() in state.cachedFileNames,
                    showWordCount = state.countWords,
                    replaceRules = state.replaceRules,
                    replaceBook = state.replaceBook,
                    useReplace = state.useReplace,
                    replaceRevision = state.replaceRevision,
                    onClick = { onChapterClick(chapter) },
                    onLongClick = { onChapterLongClick(it) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.currentChapterInfo,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        enabled = state.chapters.isNotEmpty(),
                        role = Role.Button,
                        onClick = {
                            val index = state.chapters.indexOfFirst { it.index == state.currentChapterIndex }
                                .takeIf { it >= 0 }
                                ?: state.chapters.indexOfLast { it.index < state.currentChapterIndex }
                            scrollToItem(index)
                        },
                        onLongClick = {
                            val index = state.chapters.indexOfFirst { it.index == state.currentChapterIndex }
                                .takeIf { it >= 0 }
                                ?: state.chapters.indexOfLast { it.index < state.currentChapterIndex }
                            scrollToItem(index)
                        }
                    )
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { scrollToItem(0) }, enabled = state.chapters.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_arrow_drop_up), contentDescription = "回到目录顶部")
                }
                IconButton(onClick = { scrollToItem(state.chapters.lastIndex) }, enabled = state.chapters.isNotEmpty()) {
                    Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = "跳到目录底部")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: BookChapter,
    currentChapterIndex: Int,
    isLocalBook: Boolean,
    cached: Boolean,
    showWordCount: Boolean,
    replaceRules: List<ReplaceRule>?,
    replaceBook: ReplaceBook?,
    useReplace: Boolean,
    replaceRevision: Int,
    onClick: () -> Unit,
    onLongClick: (String) -> Unit
) {
    var displayTitle by remember(chapter.bookUrl, chapter.index, replaceRevision) { mutableStateOf(chapter.title) }
    LaunchedEffect(chapter.bookUrl, chapter.index, replaceRules, useReplace, replaceRevision) {
        val rules = replaceRules
        val replace = replaceBook
        displayTitle = if (rules == null || replace == null) {
            chapter.title
        } else {
            withContext(Dispatchers.Default) {
                chapter.getDisplayTitle(rules, useReplace, replaceBook = replace)
            }
        }
    }

    val isCurrent = currentChapterIndex == chapter.index
    val titleColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        chapter.isVolume -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (chapter.isVolume) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else Color.Transparent)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = { onLongClick(displayTitle) })
            .semantics { selected = isCurrent }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chapter.isVip && !chapter.isPay) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_outline),
                contentDescription = "VIP 章节",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = displayTitle,
                color = titleColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (chapter.isVolume || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val details = listOfNotNull(
                chapter.tag?.takeIf { it.isNotBlank() },
                chapter.wordCount?.takeIf { showWordCount && it.isNotBlank() && !chapter.isVolume }
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isCurrent) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = "当前章节", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        } else if (!isLocalBook && !cached) {
            Icon(painterResource(R.drawable.ic_outline_cloud_24), contentDescription = "尚未缓存", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChapterListScreenPreview() {
    LegadoTheme {
        ChapterListScreen(
            state = ChapterListUiState(currentChapterInfo = "第 128 章（128 / 312）"),
            onChapterClick = {},
            onChapterLongClick = {},
            onRetry = {}
        )
    }
}
