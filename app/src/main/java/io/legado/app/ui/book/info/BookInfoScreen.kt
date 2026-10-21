package io.legado.app.ui.book.info

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal data class BookInfoUiState(
    val name: String = "",
    val author: String = "",
    val origin: String = "",
    val latestChapter: String = "",
    val currentChapter: String = "",
    val group: String = "",
    val kinds: List<String> = emptyList(),
    val inBookshelf: Boolean = false,
    val isWebFile: Boolean = false,
    val hasIntro: Boolean = false,
    val loading: Boolean = true
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookInfoScreen(
    state: BookInfoUiState,
    coverContent: @Composable (Modifier) -> Unit,
    introContent: @Composable (Modifier) -> Unit,
    onCoverClick: () -> Unit,
    onCoverLongClick: () -> Unit,
    onNameClick: () -> Unit,
    onNameLongClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onAuthorLongClick: () -> Unit,
    onOriginClick: () -> Unit,
    onChangeSource: () -> Unit,
    onChangeGroup: () -> Unit,
    onOpenToc: () -> Unit,
    onKindClick: (String) -> Unit,
    onKindLongClick: (String) -> Unit,
    onShelfClick: () -> Unit,
    onReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arcColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "hero") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(168.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawOval(
                            color = arcColor,
                            topLeft = Offset(-size.width * 0.16f, size.height * 0.53f),
                            size = Size(size.width * 1.32f, size.height * 0.64f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 3.dp)
                            .size(width = 110.dp, height = 160.dp)
                            .shadow(8.dp, RoundedCornerShape(5.dp))
                            .clip(RoundedCornerShape(5.dp))
                            .combinedClickable(onClick = onCoverClick, onLongClick = onCoverLongClick)
                    ) {
                        coverContent(Modifier.fillMaxSize())
                    }
                }
            }

            item(key = "name-and-kinds") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.name.ifBlank { stringResource(R.string.book_name) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.combinedClickable(
                                onClick = onNameClick,
                                onLongClick = onNameLongClick
                            )
                        )
                    }
                    if (state.kinds.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.kinds.forEach { kind ->
                                    Surface(
                                        modifier = Modifier.combinedClickable(
                                            onClick = { onKindClick(kind) },
                                            onLongClick = { onKindLongClick(kind) }
                                        ),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = kind,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "metadata") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    InfoRow(
                        R.drawable.ic_author,
                        state.author,
                        onClick = onAuthorClick,
                        onLongClick = onAuthorLongClick
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    InfoRow(
                        R.drawable.ic_web_outline,
                        state.origin,
                        actionText = stringResource(R.string.change_origin),
                        onClick = onOriginClick,
                        onAction = onChangeSource
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    InfoRow(R.drawable.ic_book_last, state.latestChapter)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    InfoRow(
                        R.drawable.ic_groups,
                        state.group,
                        actionText = stringResource(R.string.change_group),
                        onAction = onChangeGroup
                    )
                    if (!state.isWebFile) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        InfoRow(
                            R.drawable.ic_folder_open,
                            state.currentChapter,
                            actionText = stringResource(R.string.view_toc),
                            onClick = onOpenToc,
                            onAction = onOpenToc
                        )
                    }
                }
            }

            item(key = "intro") {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    if (state.hasIntro) {
                        introContent(Modifier.fillMaxWidth())
                    } else {
                        Text(
                            text = stringResource(R.string.book_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                        )
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onShelfClick,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(if (state.inBookshelf) stringResource(R.string.remove_from_bookshelf) else stringResource(R.string.add_to_bookshelf))
                }
                Button(
                    onClick = onReadClick,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.reading))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: Int,
    text: String,
    actionText: String? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    onAction: () -> Unit = onClick
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
        if (actionText != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(actionText)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10171B)
@Composable
private fun BookInfoScreenPreview() {
    io.legado.app.ui.widget.compose.LegadoTheme {
        BookInfoScreen(
            state = BookInfoUiState(
                name = "星海纪元：群星长夜",
                author = "巡山小妖",
                origin = "书源：星河书库",
                latestChapter = "第 248 章 · 长夜之后",
                currentChapter = "第 212 章",
                group = "科幻 / 连载",
                kinds = listOf("科幻", "星际", "群像"),
                inBookshelf = true,
                hasIntro = true,
                loading = false
            ),
            coverContent = { modifier ->
                Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text("BOOK", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            },
            introContent = { modifier -> Text("文明航船驶出母星，群星之间的历史从此改写。", modifier = modifier) },
            onCoverClick = {},
            onCoverLongClick = {},
            onNameClick = {},
            onNameLongClick = {},
            onAuthorClick = {},
            onAuthorLongClick = {},
            onOriginClick = {},
            onChangeSource = {},
            onChangeGroup = {},
            onOpenToc = {},
            onKindClick = {},
            onKindLongClick = {},
            onShelfClick = {},
            onReadClick = {}
        )
    }
}
