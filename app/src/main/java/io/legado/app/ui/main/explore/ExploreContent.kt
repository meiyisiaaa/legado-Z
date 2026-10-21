package io.legado.app.ui.main.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import kotlinx.coroutines.launch

internal data class ExploreUiState(
    val sources: List<BookSourcePart> = emptyList(),
    val groups: List<String> = emptyList(),
    val query: String = "",
    val selectedGroup: String? = null,
    val expandedSourceUrl: String? = null,
    val kindsBySource: Map<String, List<ExploreKind>> = emptyMap(),
    val renderRevisionBySource: Map<String, Int> = emptyMap(),
    val loadingSourceUrls: Set<String> = emptySet(),
    val isResumed: Boolean = false,
    val scrollToTopRequest: Int = 0
)

internal enum class ExploreSourceAction {
    Edit,
    Top,
    Login,
    Search,
    Refresh,
    Delete
}

@Composable
internal fun ExploreKindControls(
    source: BookSourcePart,
    kinds: List<ExploreKind>,
    revision: Int,
    active: Boolean,
    onOpenExplore: (String, String, String?) -> Unit,
    onRefresh: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOpenExplore = rememberUpdatedState(onOpenExplore)
    val currentRefresh = rememberUpdatedState(onRefresh)
    val renderer = remember(context, source.bookSourceUrl, scope) {
        ExploreKindRenderer(
            context = context,
            scope = scope,
            onOpenExplore = { sourceUrl, title, url -> currentOpenExplore.value(sourceUrl, title, url) },
            onRefresh = { sourceUrl -> currentRefresh.value(sourceUrl) }
        )
    }
    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }
    AndroidView(
        factory = { renderer.createView() },
        modifier = modifier.fillMaxWidth(),
        update = { renderer.render(source, kinds, revision, active) }
    )
}

/** Compose owns the source list and states; the title bar and search field retain their native host views. */
@Composable
internal fun ExploreContent(
    state: ExploreUiState,
    onToggleSource: (BookSourcePart) -> Unit,
    onSourceAction: (BookSourcePart, ExploreSourceAction) -> Unit,
    onOpenExplore: (String, String, String?) -> Unit,
    kindContent: @Composable (BookSourcePart, List<ExploreKind>, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motionDuration = LocalLegadoMotion.current.state

    LaunchedEffect(state.scrollToTopRequest) {
        if (state.scrollToTopRequest > 0 && state.sources.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    val firstSourceUrl = state.sources.firstOrNull()?.bookSourceUrl
    var previousFirstSourceUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(firstSourceUrl) {
        if (firstSourceUrl != null && previousFirstSourceUrl != null && firstSourceUrl != previousFirstSourceUrl) {
            listState.scrollToItem(0)
        }
        previousFirstSourceUrl = firstSourceUrl
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (state.sources.isEmpty()) {
            if (state.query.isBlank() && state.selectedGroup == null) {
                Text(
                    text = stringResource(R.string.explore_empty),
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(
                    items = state.sources,
                    key = BookSourcePart::bookSourceUrl
                ) { source ->
                    val expanded = state.expandedSourceUrl == source.bookSourceUrl
                    ExploreSourceCard(
                        source = source,
                        expanded = expanded,
                        isLoading = source.bookSourceUrl in state.loadingSourceUrls,
                        kinds = state.kindsBySource[source.bookSourceUrl].orEmpty(),
                        renderRevision = state.renderRevisionBySource[source.bookSourceUrl] ?: 0,
                        isResumed = state.isResumed,
                        motionDuration = motionDuration,
                        onToggle = {
                            onToggleSource(source)
                            if (!expanded) {
                                scope.launch { listState.animateScrollToItem(state.sources.indexOf(source)) }
                            }
                        },
                        onSourceAction = { action -> onSourceAction(source, action) },
                        onOpenExplore = onOpenExplore,
                        kindContent = kindContent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceCard(
    source: BookSourcePart,
    expanded: Boolean,
    isLoading: Boolean,
    kinds: List<ExploreKind>,
    renderRevision: Int,
    isResumed: Boolean,
    motionDuration: Int,
    onToggle: () -> Unit,
    onSourceAction: (ExploreSourceAction) -> Unit,
    onOpenExplore: (String, String, String?) -> Unit,
    kindContent: @Composable (BookSourcePart, List<ExploreKind>, Int, Boolean) -> Unit
) {
    var menuExpanded by remember(source.bookSourceUrl) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(motionDuration))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = { menuExpanded = true },
                        onLongClickLabel = stringResource(R.string.more_menu)
                    )
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = source.bookSourceName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Icon(
                    painter = painterResource(if (expanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                SourceMenuAction(R.string.edit, R.drawable.ic_edit, ExploreSourceAction.Edit, { menuExpanded = false }, onSourceAction)
                SourceMenuAction(R.string.to_top, R.drawable.ic_arrow_drop_up, ExploreSourceAction.Top, { menuExpanded = false }, onSourceAction)
                if (source.hasLoginUrl) {
                    SourceMenuAction(R.string.login, R.drawable.ic_bottom_person_e, ExploreSourceAction.Login, { menuExpanded = false }, onSourceAction)
                }
                SourceMenuAction(R.string.search, R.drawable.ic_search, ExploreSourceAction.Search, { menuExpanded = false }, onSourceAction)
                SourceMenuAction(R.string.refresh, R.drawable.ic_refresh_black_24dp, ExploreSourceAction.Refresh, { menuExpanded = false }, onSourceAction)
                SourceMenuAction(R.string.delete, R.drawable.ic_outline_delete, ExploreSourceAction.Delete, { menuExpanded = false }, onSourceAction)
            }
        }

        AnimatedVisibility(
            visible = expanded && isResumed,
            enter = expandVertically(animationSpec = tween(motionDuration)) + fadeIn(animationSpec = tween(motionDuration)),
            exit = shrinkVertically(animationSpec = tween(motionDuration)) + fadeOut(animationSpec = tween(motionDuration)),
            label = "explore-source-controls"
        ) {
            if (kinds.isNotEmpty()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                        kindContent(source, kinds, renderRevision, isResumed)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceMenuAction(
    label: Int,
    icon: Int,
    action: ExploreSourceAction,
    onDismiss: () -> Unit,
    onAction: (ExploreSourceAction) -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = {
            onDismiss()
            onAction(action)
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ExploreContentPreview() {
    val source = BookSourcePart(
        bookSourceUrl = "https://example.invalid",
        bookSourceName = "示例书源",
        bookSourceGroup = "网络文学",
        hasLoginUrl = true
    )
    MaterialTheme {
        ExploreContent(
            state = ExploreUiState(
                sources = listOf(source),
                groups = listOf("网络文学", "漫画"),
                expandedSourceUrl = source.bookSourceUrl,
                kindsBySource = mapOf(
                    source.bookSourceUrl to listOf(ExploreKind(title = "热门", url = "https://example.invalid"))
                )
            ),
            onToggleSource = {},
            onSourceAction = { _, _ -> },
            onOpenExplore = { _, _, _ -> },
            kindContent = { _, _, _, _ ->
                Text(
                    text = "动态发现条件",
                    modifier = Modifier.padding(18.dp),
                    color = Color.Gray
                )
            }
        )
    }
}
