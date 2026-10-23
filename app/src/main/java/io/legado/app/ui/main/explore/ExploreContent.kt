package io.legado.app.ui.main.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.compose.LegadoPageState
import kotlinx.coroutines.launch

internal data class ExploreUiState(
    val sources: List<BookSourcePart> = emptyList(),
    val groups: List<String> = emptyList(),
    val isLoadingSources: Boolean = false,
    val sourcesError: String? = null,
    val isLoadingGroups: Boolean = false,
    val groupsError: String? = null,
    val query: String = "",
    val selectedGroup: String? = null,
    val expandedSourceUrl: String? = null,
    val kindsBySource: Map<String, List<ExploreKind>> = emptyMap(),
    val renderRevisionBySource: Map<String, Int> = emptyMap(),
    val loadingSourceUrls: Set<String> = emptySet(),
    val kindErrorsBySource: Map<String, String> = emptyMap(),
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
    onRetrySources: () -> Unit,
    onRetryGroups: () -> Unit,
    kindContent: @Composable (BookSourcePart, List<ExploreKind>, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val motion = LocalLegadoMotion.current
    val motionDuration = motion.state

    LaunchedEffect(state.scrollToTopRequest) {
        if (state.scrollToTopRequest > 0 && state.sources.isNotEmpty()) {
            if (motion.enabled) listState.animateScrollToItem(0) else listState.scrollToItem(0)
        }
    }

    val firstSourceUrl = state.sources.firstOrNull()?.bookSourceUrl
    var previousFirstSourceUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(firstSourceUrl) {
        if (firstSourceUrl != null) {
            if (previousFirstSourceUrl != null && firstSourceUrl != previousFirstSourceUrl) {
                listState.scrollToItem(0)
            }
            previousFirstSourceUrl = firstSourceUrl
        }
    }

    val loadingDescription = stringResource(R.string.data_loading)
    val retryLabel = stringResource(R.string.retry)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            state.isLoadingGroups -> if (motion.enabled) LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = loadingDescription
                    liveRegion = LiveRegionMode.Polite
                }
            ) else Icon(painterResource(R.drawable.ic_refresh_black_24dp),
                contentDescription = loadingDescription,
                modifier = Modifier.padding(16.dp).size(20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite })
            state.groupsError != null -> Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.groupsError.orEmpty(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRetryGroups) { Text(retryLabel) }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoadingSources -> if (motion.enabled) CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(30.dp).semantics {
                        contentDescription = loadingDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                    strokeWidth = 2.dp
                ) else Icon(painterResource(R.drawable.ic_refresh_black_24dp),
                    contentDescription = loadingDescription,
                    modifier = Modifier.align(Alignment.Center).size(30.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
                state.sourcesError != null -> LegadoPageState(
                    icon = R.drawable.ic_bottom_explore_e,
                    title = stringResource(R.string.error_get_data),
                    message = state.sourcesError,
                    iconTint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    TextButton(onClick = onRetrySources) { Text(retryLabel) }
                }
                state.sources.isEmpty() -> LegadoPageState(
                    icon = R.drawable.ic_bottom_explore_e,
                    title = stringResource(if (state.query.isBlank() && state.selectedGroup == null)
                        R.string.discovery else R.string.search_no_results),
                    message = stringResource(
                        if (state.query.isBlank() && state.selectedGroup == null) {
                            R.string.explore_empty
                        } else {
                            R.string.empty
                        }
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
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
                            kindsLoaded = source.bookSourceUrl in state.kindsBySource,
                            kindError = state.kindErrorsBySource[source.bookSourceUrl],
                            renderRevision = state.renderRevisionBySource[source.bookSourceUrl] ?: 0,
                            isResumed = state.isResumed,
                            motionDuration = motionDuration,
                            onToggle = {
                                onToggleSource(source)
                                if (!expanded) {
                                    scope.launch {
                                        if (motion.enabled) listState.animateScrollToItem(state.sources.indexOf(source))
                                        else listState.scrollToItem(state.sources.indexOf(source))
                                    }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceCard(
    source: BookSourcePart,
    expanded: Boolean,
    isLoading: Boolean,
    kinds: List<ExploreKind>,
    kindsLoaded: Boolean,
    kindError: String?,
    renderRevision: Int,
    isResumed: Boolean,
    motionDuration: Int,
    onToggle: () -> Unit,
    onSourceAction: (ExploreSourceAction) -> Unit,
    onOpenExplore: (String, String, String?) -> Unit,
    kindContent: @Composable (BookSourcePart, List<ExploreKind>, Int, Boolean) -> Unit
) {
    var menuExpanded by remember(source.bookSourceUrl) { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(if (expanded) 90f else 0f,
        tween(motionDuration), label = "source expansion")
    val loadingDescription = stringResource(R.string.data_loading)
    val expansionDescription = stringResource(
        if (expanded) R.string.accessibility_expanded else R.string.accessibility_collapsed
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onToggle,
                        onLongClick = { menuExpanded = true },
                        onLongClickLabel = stringResource(R.string.more_menu)
                    )
                    .semantics {
                        stateDescription = expansionDescription
                        if (expanded) collapse { onToggle(); true }
                        else expand { onToggle(); true }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Text(
                    text = source.bookSourceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                  source.bookSourceGroup?.takeIf(String::isNotBlank)?.let { group ->
                    Text(group, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
                if (isLoading) {
                    if (LocalLegadoMotion.current.enabled) CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp).size(18.dp).semantics {
                            contentDescription = loadingDescription
                            liveRegion = LiveRegionMode.Polite
                        },
                        strokeWidth = 2.dp
                    ) else Icon(painterResource(R.drawable.ic_refresh_black_24dp),
                        contentDescription = loadingDescription,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite })
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(arrowRotation)
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AnimatedVisibility(
            visible = expanded && isResumed,
            enter = expandVertically(animationSpec = tween(motionDuration)) + fadeIn(animationSpec = tween(motionDuration)),
            exit = shrinkVertically(animationSpec = tween(motionDuration)) + fadeOut(animationSpec = tween(motionDuration)),
            label = "explore-source-controls"
        ) {
            val ruleColor = MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)
                .drawBehind { drawLine(ruleColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) }
                .padding(start = 12.dp)) {
            when {
                isLoading -> Unit
                kindError != null -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        kindError,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { onSourceAction(ExploreSourceAction.Refresh) }) {
                        Text(stringResource(R.string.retry))
                    }
                    if (kinds.isNotEmpty()) {
                        Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                            kindContent(source, kinds, renderRevision, isResumed)
                        }
                    }
                }
                kindsLoaded && kinds.isEmpty() -> Text(
                    stringResource(R.string.empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                kinds.isNotEmpty() -> Column {
                    Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
                        kindContent(source, kinds, renderRevision, isResumed)
                    }
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
            onRetrySources = {},
            onRetryGroups = {},
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
