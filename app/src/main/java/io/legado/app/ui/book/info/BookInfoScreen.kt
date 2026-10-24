package io.legado.app.ui.book.info

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.legadoPressFeedback

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
    val userNote: String = "",
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
    val colors = MaterialTheme.colorScheme
    val ink = !LocalInspectionMode.current && AppConfig.isEInkMode
    Column(modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "hero") {
                // A / mineral cloth: one cover field, asymmetric identity, paper below.
                BoxWithConstraints(Modifier.fillMaxWidth().background(colors.tertiaryContainer)) {
                    if (!ink) Image(painterResource(R.drawable.mineral_cloth), null,
                        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                        colorFilter = ColorFilter.tint(colors.tertiaryContainer, BlendMode.Modulate), alpha = 0.35f)
                    val stacked = LocalDensity.current.fontScale >= 1.5f
                    val compact = maxWidth < 360.dp
                    val smallCover = compact || stacked
                    val ribbon: @Composable (Modifier) -> Unit = { modifier ->
                        val ribbonColor = colors.inversePrimary
                        Canvas(modifier.size(20.dp, 30.dp)) {
                            val path = Path().apply {
                                moveTo(0f, 0f); lineTo(size.width, 0f)
                                lineTo(size.width, size.height); lineTo(size.width / 2, size.height * 0.78f)
                                lineTo(0f, size.height); close()
                            }
                            drawPath(path, ribbonColor)
                        }
                    }
                    val cover: @Composable () -> Unit = {
                        Box(Modifier.padding(bottom = if (smallCover) 16.dp else 0.dp)) {
                            if (smallCover) ribbon(Modifier.align(Alignment.BottomStart).padding(start = 12.dp))
                            Box(Modifier.padding(bottom = if (smallCover) 14.dp else 0.dp).size(
                                if (smallCover) 104.dp else 136.dp,
                                if (smallCover) 150.dp else 196.dp)
                                .shadow(6.dp, RoundedCornerShape(4.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .combinedClickable(onClick = onCoverClick, onLongClick = onCoverLongClick)) {
                                coverContent(Modifier.fillMaxSize())
                            }
                        }
                    }
                    val identity: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.name.ifBlank { stringResource(R.string.book_name) },
                                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif,
                                    fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
                                color = colors.onTertiaryContainer,
                                modifier = Modifier.heightIn(min = 48.dp).semantics { heading() }
                                    .combinedClickable(onClick = onNameClick, onLongClick = onNameLongClick))
                            Text(state.author, style = MaterialTheme.typography.bodyLarge,
                                color = colors.onTertiaryContainer,
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .combinedClickable(onClick = onAuthorClick, onLongClick = onAuthorLongClick)
                                    .padding(top = 8.dp))
                        }
                    }
                    val metadata: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            InfoRow(R.drawable.ic_web_outline, state.origin,
                                actionText = stringResource(R.string.change_origin),
                                onClick = onOriginClick, onAction = onChangeSource,
                                onColorField = true)
                            InfoRow(R.drawable.ic_groups, state.group,
                                actionText = stringResource(R.string.change_group),
                                onClick = onChangeGroup, onAction = onChangeGroup,
                                onColorField = true)
                        }
                    }
                    val kinds: @Composable () -> Unit = {
                        if (state.kinds.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.kinds.forEach { kind ->
                                    Text(kind, style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                            .combinedClickable(onClick = { onKindClick(kind) },
                                                onLongClick = { onKindLongClick(kind) })
                                            .padding(vertical = 14.dp, horizontal = 4.dp))
                                }
                            }
                        }
                    }
                    CompositionLocalProvider(LocalContentColor provides colors.onTertiaryContainer) {
                        Column(Modifier.padding(start = 24.dp, end = 24.dp,
                            top = if (compact && !stacked) 16.dp else 24.dp,
                            bottom = if (compact && !stacked) 12.dp else if (smallCover) 20.dp else 32.dp)) {
                            if (stacked) {
                                cover()
                                identity()
                                metadata()
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    cover()
                                    Column(Modifier.weight(1f)) {
                                        identity()
                                        if (!compact) metadata()
                                        kinds()
                                    }
                                }
                                if (compact) metadata()
                            }
                            if (stacked) kinds()
                        }
                    }
                    if (!smallCover) {
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(16.dp).background(colors.background))
                        val ribbonColor = colors.inversePrimary
                        Canvas(Modifier.matchParentSize()) {
                            val left = 36.dp.toPx()
                            val right = left + 20.dp.toPx()
                            // Follow the centered cover as real metadata changes the field height.
                            val top = (size.height - 24.dp.toPx() - 32.dp.toPx() - 196.dp.toPx()) / 2 +
                                24.dp.toPx() + 196.dp.toPx() - 2.dp.toPx()
                            drawPath(Path().apply {
                                moveTo(left, top); lineTo(right, top)
                                lineTo(right, size.height); lineTo((left + right) / 2, size.height - 6.dp.toPx())
                                lineTo(left, size.height); close()
                            }, ribbonColor)
                        }
                    }
                }
            }
            item(key = "reading-position") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
                    .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small).padding(16.dp)) {
                    if (!state.isWebFile) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.current_chapter),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).semantics { heading() })
                            TextButton(onClick = onOpenToc) {
                                Icon(painterResource(R.drawable.ic_folder_open), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.view_toc))
                            }
                        }
                        Text(state.currentChapter, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .combinedClickable(onClick = onOpenToc)
                                .padding(vertical = 8.dp))
                        HorizontalDivider(color = colors.outlineVariant)
                    }
                    InfoRow(R.drawable.ic_book_last, stringResource(R.string.lasted_show, state.latestChapter))
                }
            }

            item(key = "intro") {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.book_intro), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        if (state.hasIntro) introContent(Modifier.fillMaxWidth())
                    }
                }
            }
            if (state.userNote.isNotBlank()) {
                item(key = "user-note") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(stringResource(R.string.book_note_label),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() })
                        SelectionContainer {
                            Text(state.userNote, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val shelfInteractions = remember { MutableInteractionSource() }
                val readInteractions = remember { MutableInteractionSource() }
                FilledTonalButton(
                    onClick = onShelfClick,
                    interactionSource = shelfInteractions,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)
                        .legadoPressFeedback(shelfInteractions),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(painterResource(if (state.inBookshelf) R.drawable.ic_outline_delete else R.drawable.ic_add),
                        null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.inBookshelf) stringResource(R.string.remove_from_bookshelf) else stringResource(R.string.add_to_bookshelf))
                }
                Button(
                    onClick = onReadClick,
                    interactionSource = readInteractions,
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp)
                        .legadoPressFeedback(readInteractions),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_book_has), null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
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
    onAction: () -> Unit = onClick,
    onColorField: Boolean = false
) {
    val foreground = if (onColorField) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = foreground,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 12.dp)
        )
        if (actionText != null) {
            TextButton(onClick = onAction, colors = ButtonDefaults.textButtonColors(contentColor = foreground),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
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
