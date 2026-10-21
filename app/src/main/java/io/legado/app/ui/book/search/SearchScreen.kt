package io.legado.app.ui.book.search

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.colorResource
import io.legado.app.R
import io.legado.app.ui.book.info.bookCoverTransitionName
import io.legado.app.ui.book.info.isBookCoverTransitionEnabled
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.image.CoverImageView
import androidx.core.view.ViewCompat
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class SearchUiState(
    val query: String = "",
    val queryFocused: Boolean = false,
    val showInputHelp: Boolean = true,
    val focusRequestId: Int = 0,
    val isSearching: Boolean = false,
    val manuallyStopped: Boolean = false,
    val hasMore: Boolean = false,
    val history: List<String> = emptyList(),
    val localBooks: List<SearchLocalBookUi> = emptyList(),
    val results: List<SearchBookUi> = emptyList()
)

internal data class SearchLocalBookUi(
    val name: String,
    val author: String,
    val bookUrl: String
)

internal data class SearchBookUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val coverUrl: String?,
    val origin: String?,
    val originCount: Int,
    val latestChapter: String?,
    val intro: String,
    val kinds: List<String>,
    val inBookshelf: Boolean
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLocalBookClick: (SearchLocalBookUi) -> Unit,
    onBookClick: (SearchBookUi, View?) -> Unit,
    onStopSearch: () -> Unit,
    onContinueSearch: () -> Unit,
    onListEndReached: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val motionEnabled = LocalLegadoMotion.current.enabled

    LaunchedEffect(
        listState,
        state.results.size,
        state.isSearching,
        state.hasMore,
        state.showInputHelp,
        state.query
    ) {
        if (!state.showInputHelp && state.results.isNotEmpty()) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                    ?.let { it >= state.results.size }
                    ?: false
            }
                .distinctUntilChanged()
                .collect { reachedEnd ->
                    if (reachedEnd && !state.isSearching && state.hasMore) {
                        onListEndReached()
                    }
                }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            AnimatedVisibility(
                visible = state.isSearching,
            enter = if (motionEnabled) androidx.compose.animation.expandVertically() + fadeIn() else EnterTransition.None,
            exit = if (motionEnabled) androidx.compose.animation.shrinkVertically() + fadeOut() else ExitTransition.None
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            AnimatedContent(
                targetState = state.showInputHelp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    if (motionEnabled) {
                        (fadeIn() + slideInVertically { it / 18 }) togetherWith
                            (fadeOut() + slideOutVertically { -it / 18 })
                    } else {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "search content"
            ) { showHelp ->
                if (showHelp) {
                    SearchSuggestions(
                        state = state,
                        onHistoryClick = onHistoryClick,
                        onHistoryDelete = onHistoryDelete,
                        onClearHistory = onClearHistory,
                        onLocalBookClick = onLocalBookClick
                    )
                } else {
                    SearchResults(
                        state = state,
                        listState = listState,
                        onBookClick = onBookClick,
                        onListEndReached = onListEndReached
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.query.isNotBlank() &&
                (state.isSearching || (state.hasMore && !state.manuallyStopped)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .imePadding(),
            enter = if (motionEnabled) fadeIn() + scaleIn() else EnterTransition.None,
            exit = if (motionEnabled) fadeOut() + scaleOut() else ExitTransition.None
        ) {
            androidx.compose.material3.FloatingActionButton(
                onClick = if (state.isSearching) onStopSearch else onContinueSearch,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                if (state.isSearching) {
                    Icon(painterResource(R.drawable.ic_stop_black_24dp), contentDescription = "停止搜索")
                } else {
                    Icon(painterResource(R.drawable.ic_play_24dp), contentDescription = "继续搜索")
                }
            }
        }
    }
}

@Composable
internal fun SearchToolbar(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onQueryFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val toolbarForeground = when {
        LocalInspectionMode.current -> MaterialTheme.colorScheme.onPrimary
        AppConfig.isEInkMode -> Color.Black
        else -> Color(context.getPrimaryTextColor(ColorUtils.isColorLight(ThemeStore.primaryColor(context))))
    }

    LaunchedEffect(state.queryFocused, state.focusRequestId) {
        if (state.queryFocused) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    BackHandler(enabled = state.queryFocused) {
        focusManager.clearFocus()
        keyboard?.hide()
        onQueryFocusChange(false)
    }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { onQueryFocusChange(it.isFocused) },
            placeholder = {
                Text(
                    stringResource(R.string.search_book_key),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_close),
                            contentDescription = stringResource(R.string.clear_search_query)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = toolbarForeground,
                unfocusedTextColor = toolbarForeground,
                cursorColor = toolbarForeground,
                focusedBorderColor = toolbarForeground,
                unfocusedBorderColor = toolbarForeground.copy(alpha = 0.6f),
                focusedPlaceholderColor = toolbarForeground,
                unfocusedPlaceholderColor = toolbarForeground,
                focusedTrailingIconColor = toolbarForeground,
                unfocusedTrailingIconColor = toolbarForeground
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onSubmit()
                }
            )
        )
        IconButton(
            onClick = {
                keyboard?.hide()
                focusManager.clearFocus()
                onSubmit()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search),
                tint = toolbarForeground)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSuggestions(
    state: SearchUiState,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearHistory: () -> Unit,
    onLocalBookClick: (SearchLocalBookUi) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.localBooks.isNotEmpty()) {
            item(key = "local-heading") {
                SectionHeading(title = stringResource(R.string.bookshelf))
            }
            item(key = "local-books") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.localBooks.forEach { book ->
                        LocalBookChip(book = book, onClick = { onLocalBookClick(book) })
                    }
                }
            }
        }
        item(key = "history-heading") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading(
                    title = stringResource(R.string.searchHistory),
                    modifier = Modifier.weight(1f)
                )
                if (state.history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text(stringResource(R.string.clear))
                    }
                }
            }
        }

        if (state.history.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    text = "暂无搜索记录",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            item(key = "history-keys") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    state.history.forEach { keyword ->
                        HistoryChip(
                            word = keyword,
                            onClick = { onHistoryClick(keyword) },
                            onDelete = { onHistoryDelete(keyword) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBookClick: (SearchBookUi, View?) -> Unit,
    onListEndReached: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 102.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(state.results, key = { "result:${it.bookUrl}" }) { book ->
            SearchBookCard(book = book, onClick = { cover -> onBookClick(book, cover) })
        }

        if (state.isSearching) {
            item(key = "loading-more") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在扫描书源…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (state.hasMore && state.results.isNotEmpty()) {
            item(key = "load-more") {
                TextButton(
                    onClick = onListEndReached,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("继续加载更多结果")
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun LocalBookChip(book: SearchLocalBookUi, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = book.name,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HistoryChip(word: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = word,
                modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onDelete)
                    .padding(vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_outline_delete),
                    contentDescription = "删除搜索记录",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchBookCard(book: SearchBookUi, onClick: (View?) -> Unit) {
    val coverRef = remember(book.bookUrl) { arrayOfNulls<View>(1) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(coverRef[0]) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BookCover(
                book = book,
                modifier = Modifier.width(80.dp).height(110.dp),
                onViewCreated = { coverRef[0] = it }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (book.inBookshelf) {
                        Box(
                            Modifier.padding(end = 5.dp).size(8.dp).clip(CircleShape)
                                .background(colorResource(R.color.md_green_600))
                        )
                    }
                    Text(
                        text = book.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${book.originCount}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.kinds.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        book.kinds.forEach { kind ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = kind,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                book.latestChapter?.takeIf(String::isNotBlank)?.let { latest ->
                    Text(
                        text = latest,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (book.intro.isNotBlank()) {
                    Text(
                        text = book.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun BookCover(
    book: SearchBookUi,
    modifier: Modifier = Modifier,
    onViewCreated: (CoverImageView) -> Unit = {}
) {
    key(book.coverUrl, book.name, book.author, book.origin) {
        AndroidView(
            factory = { context ->
                CoverImageView(context).apply {
                    contentDescription = context.getString(R.string.img_cover)
                    if (isBookCoverTransitionEnabled()) {
                        ViewCompat.setTransitionName(this, bookCoverTransitionName(book.bookUrl))
                    }
                    load(
                        path = book.coverUrl,
                        name = book.name,
                        author = book.author,
                        loadOnlyWifi = AppConfig.loadCoverOnlyWifi,
                        sourceOrigin = book.origin
                    )
                    onViewCreated(this)
                }
            },
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    LegadoTheme {
        SearchScreen(
            state = SearchUiState(
                query = "星际",
                queryFocused = false,
                showInputHelp = false,
                hasMore = true,
                results = listOf(
                    SearchBookUi(
                        bookUrl = "preview://book",
                        name = "星际远航",
                        author = "示例作者",
                        coverUrl = null,
                        origin = null,
                        originCount = 4,
                        latestChapter = "最新章节：第四十章",
                        intro = "跨越星海的旅程，从一座边境空间站开始。",
                        kinds = listOf("科幻", "冒险"),
                        inBookshelf = false
                    )
                )
            ),
            onHistoryClick = {},
            onHistoryDelete = {},
            onClearHistory = {},
            onLocalBookClick = {},
            onBookClick = { _, _ -> },
            onStopSearch = {},
            onContinueSearch = {},
            onListEndReached = {}
        )
    }
}
