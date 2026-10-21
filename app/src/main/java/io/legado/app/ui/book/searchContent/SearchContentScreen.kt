package io.legado.app.ui.book.searchContent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import kotlinx.coroutines.launch

internal data class SearchContentUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val resultCount: Int = 0,
    val searching: Boolean = false,
    val replaceEnabled: Boolean = false,
    val regexEnabled: Boolean = false,
    val searchRequested: Boolean = false,
    val requestFocus: Boolean = false,
    val error: String? = null,
    val initialResultIndex: Int? = null,
    val currentChapterIndex: Int = -1
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchContentScreen(
    state: SearchContentUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onToggleReplace: () -> Unit,
    onToggleRegex: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onOpenResult: (SearchResult, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.initialResultIndex, state.results.size) {
        val index = state.initialResultIndex
        if (state.results.isNotEmpty() && index != null && index in state.results.indices) {
            listState.scrollToItem(index)
        }
    }
    LaunchedEffect(state.requestFocus) {
        if (state.requestFocus) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    val queryColor = LocalContentColor.current
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = queryColor, unfocusedTextColor = queryColor,
                            cursorColor = queryColor,
                            focusedPlaceholderColor = queryColor.copy(alpha = 0.8f),
                            unfocusedPlaceholderColor = queryColor.copy(alpha = 0.8f),
                            focusedTrailingIconColor = queryColor, unfocusedTrailingIconColor = queryColor,
                            focusedBorderColor = queryColor, unfocusedBorderColor = queryColor.copy(alpha = 0.6f)
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                onSearch(state.query.trim())
                                keyboard?.hide()
                            }
                        ),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(painterResource(R.drawable.ic_clear), contentDescription = stringResource(R.string.clear))
                                }
                            }
                        }
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_menu))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.replace)) },
                                modifier = Modifier.semantics {
                                    role = Role.Checkbox
                                    toggleableState = ToggleableState(state.replaceEnabled)
                                },
                                trailingIcon = { Checkbox(state.replaceEnabled, onCheckedChange = null) },
                                onClick = { menuExpanded = false; onToggleReplace() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.regex)) },
                                modifier = Modifier.semantics {
                                    role = Role.Checkbox
                                    toggleableState = ToggleableState(state.regexEnabled)
                                },
                                trailingIcon = { Checkbox(state.regexEnabled, onCheckedChange = null) },
                                onClick = { menuExpanded = false; onToggleRegex() }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(Modifier.fillMaxWidth().navigationBarsPadding(), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${stringResource(R.string.search_content_size)}: ${state.resultCount}",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_drop_up), contentDescription = stringResource(R.string.go_to_top))
                    }
                    IconButton(
                        onClick = {
                            if (state.results.isNotEmpty()) scope.launch { listState.animateScrollToItem(state.results.lastIndex) }
                        },
                        modifier = Modifier.size(48.dp),
                        enabled = state.results.isNotEmpty()
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = stringResource(R.string.go_to_bottom))
                    }
                }
            }
        },
        floatingActionButton = {
            if (state.searching) {
                FloatingActionButton(onClick = onStop) {
                    Icon(painterResource(R.drawable.ic_stop_black_24dp), contentDescription = stringResource(R.string.stop))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            when {
                state.results.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        itemsIndexed(state.results, key = { index, result -> "${result.chapterIndex}:$index:${result.queryIndexInChapter}" }) { index, result ->
                            SearchResultRow(
                                result = result,
                                currentChapter = result.query.isNotBlank() && result.chapterIndex == state.currentChapterIndex,
                                onClick = { if (result.query.isNotBlank()) onOpenResult(result, index) }
                            )
                        }
                    }
                }
                state.searchRequested -> SearchEmptyState()
                else -> SearchIdleState()
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: SearchResult,
    currentChapter: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = result.query.isNotBlank(), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = result.highlightedText(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (currentChapter) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun SearchResult.highlightedText(): AnnotatedString {
    val accent = MaterialTheme.colorScheme.primary
    val body = MaterialTheme.colorScheme.onSurface
    val underlined = AppConfig.isEInkMode
    val chapter = chapterTitle
    val found = remember(resultText, query, isRegex) {
        if (query.isBlank()) null
        else if (isRegex) {
            runCatching {
                Regex(query).find(resultText, 20) ?: Regex(query).find(resultText)
            }.getOrNull()?.let { it.range.first to it.range.last + 1 }
        } else {
            val start = resultText.indexOf(query, 20).takeIf { it >= 0 } ?: resultText.indexOf(query)
            start.takeIf { it >= 0 }?.let { it to it + query.length }
        }
    }
    return buildAnnotatedString {
        if (chapter.isNotBlank() && found != null) {
            pushStyle(SpanStyle(color = if (underlined) body else accent, fontWeight = FontWeight.SemiBold,
                textDecoration = if (underlined) TextDecoration.Underline else TextDecoration.None))
            append(chapter)
            pop()
            append('\n')
        }
        val match = found?.takeIf { it.first in 0..resultText.length && it.second in it.first..resultText.length }
        if (match == null) {
            append(resultText)
        } else {
            append(resultText.substring(0, match.first))
            pushStyle(SpanStyle(color = if (underlined) body else accent, fontWeight = FontWeight.SemiBold,
                textDecoration = if (underlined) TextDecoration.Underline else TextDecoration.None))
            append(resultText.substring(match.first, match.second))
            pop()
            append(resultText.substring(match.second))
        }
    }
}

@Composable
private fun SearchEmptyState() {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.search_content_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchIdleState() {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.search),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchContentPreview() {
    LegadoTheme {
        SearchContentScreen(
            state = SearchContentUiState(
                query = "星海",
                results = listOf(SearchResult(resultText = "他们第一次意识到，星海并没有边界。", chapterTitle = "第四十二章 望向星河", query = "星海")),
                resultCount = 1,
                searchRequested = true
            ),
            onQueryChange = {}, onSearch = {}, onToggleReplace = {}, onToggleRegex = {},
            onStop = {}, onRetry = {}, onOpenResult = { _, _ -> }, onBack = {}
        )
    }
}
