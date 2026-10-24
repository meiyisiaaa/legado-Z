package io.legado.app.ui.book.toc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val motion = LocalLegadoMotion.current
    val loadingDescription = stringResource(R.string.data_loading)
    val loadError = state.loadError

    fun scrollToItem(index: Int) {
        scope.launch {
            if (motion.enabled) listState.animateScrollToItem(index.coerceAtLeast(0))
            else listState.scrollToItem(index.coerceAtLeast(0))
        }
    }

    val currentPosition = state.chapters.indexOfFirst { it.index == state.currentChapterIndex }
        .takeIf { it >= 0 } ?: state.chapters.indexOfLast { it.index < state.currentChapterIndex }
    val locateCurrentLabel = stringResource(R.string.toc_locate_current)

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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            when {
                state.isLoading -> item(key = "loading") {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 144.dp).padding(24.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = loadingDescription
                                liveRegion = LiveRegionMode.Polite
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (motion.enabled) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        else Icon(painterResource(R.drawable.ic_chapter_list), null, Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(loadingDescription, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
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
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
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
                    onLongClick = { onChapterLongClick(it) },
                    modifier = Modifier.animateItem(fadeInSpec = tween(motion.state),
                        fadeOutSpec = tween(motion.state), placementSpec = tween(motion.state))
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).tocClickable(
                            enabled = state.chapters.isNotEmpty(),
                            onClick = { scrollToItem(currentPosition) },
                            onLongClick = { scrollToItem(currentPosition) },
                            onClickLabel = locateCurrentLabel,
                            onLongClickLabel = locateCurrentLabel
                        ).padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.current_chapter), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.currentChapterInfo.isNotBlank()) Text(state.currentChapterInfo,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { scrollToItem(0) }, modifier = Modifier.size(48.dp),
                        enabled = state.chapters.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_arrow_drop_up), contentDescription = stringResource(R.string.toc_first_chapter))
                    }
                    IconButton(onClick = { scrollToItem(state.chapters.lastIndex) }, modifier = Modifier.size(48.dp),
                        enabled = state.chapters.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = stringResource(R.string.toc_last_chapter))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    onLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayTitle by remember(chapter.bookUrl, chapter.index, chapter.title, replaceRevision) { mutableStateOf(chapter.title) }
    LaunchedEffect(chapter.bookUrl, chapter.index, chapter.title, replaceRules, replaceBook, useReplace, replaceRevision) {
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
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val showVip = chapter.isVip && !chapter.isPay
    val showUncached = !isLocalBook && !cached
    val background by animateColorAsState(when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        chapter.isVolume -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }, tween(LocalLegadoMotion.current.state), label = "chapter selection")
    Column(modifier) {
        if (chapter.isVolume) HorizontalDivider(Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(background)
                .tocClickable(onClick = onClick, onLongClick = { onLongClick(displayTitle) },
                    onLongClickLabel = stringResource(R.string.toc_show_full_title))
                .semantics { selected = isCurrent }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(2.dp).height(24.dp).background(
                if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = displayTitle,
                    color = titleColor,
                    style = if (chapter.isVolume) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (chapter.isVolume || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val tag = chapter.tag?.takeIf { it.isNotBlank() }
                val wordCount = chapter.wordCount?.takeIf { showWordCount && it.isNotBlank() && !chapter.isVolume }
                if (tag != null || wordCount != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (tag != null) Text(tag, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (wordCount != null) Text(wordCount, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (showVip || isCurrent || showUncached) Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showVip) {
                    Icon(painterResource(R.drawable.ic_lock_outline), stringResource(R.string.toc_vip_chapter),
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                if (isCurrent) {
                    Icon(painterResource(R.drawable.ic_check), stringResource(R.string.current_chapter),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                if (showUncached) {
                    Icon(painterResource(R.drawable.ic_outline_cloud_24), stringResource(R.string.toc_not_cached),
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.tocClickable(onClick: () -> Unit, onLongClick: () -> Unit,
    enabled: Boolean = true, onClickLabel: String? = null, onLongClickLabel: String? = null): Modifier {
    val motion = LocalLegadoMotion.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && motion.enabled) 0.985f else 1f,
        motion.pressSpec(), label = "chapter press")
    return graphicsLayer { scaleX = scale; scaleY = scale }.clip(MaterialTheme.shapes.extraSmall)
        .combinedClickable(interactionSource = interactions,
            indication = if (motion.enabled) LocalIndication.current else null,
            enabled = enabled, role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            onClickLabel = onClickLabel, onLongClickLabel = onLongClickLabel)
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
