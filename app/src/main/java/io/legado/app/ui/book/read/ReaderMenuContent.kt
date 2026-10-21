package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import kotlinx.coroutines.delay

internal data class ReadMenuUiState(
    val title: String = "",
    val chapterName: String = "",
    val chapterUrl: String = "",
    val sourceName: String = "",
    val showTitleBarAddition: Boolean = true,
    val showSource: Boolean = false,
    val canLogin: Boolean = false,
    val canPayChapter: Boolean = false,
    val hasCustomButton: Boolean = false,
    val isLocalBook: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val progress: Int = 0,
    val maxProgress: Int = 0,
    val chapterProgress: Boolean = false,
    val autoPage: Boolean = false,
    val brightness: Int = 128,
    val brightnessAuto: Boolean = true,
    val brightnessEnabled: Boolean = true,
    val showBrightness: Boolean = true,
    val brightnessOnStart: Boolean = true,
    val background: Color = Color(0xFF17232B),
    val foreground: Color = Color(0xFFF4F8FA),
    val accent: Color = Color(0xFF45D6C8),
    val animate: Boolean = true
)

internal enum class ReaderMenuAction {
    BookInfo, OpenChapter, CustomButton, CustomButtonLong, Search, AutoPage, Replace,
    NightTheme, PreviousChapter, NextChapter, ChapterList, ReadAloud, ReadAloudSettings,
    ReadStyle, MoreSettings, Login, PayChapter, EditSource, DisableSource,
    ToggleBrightnessAuto, ChangeBrightnessPosition, PreviewPage, SeekPage, SeekChapter, Dismiss
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReadMenuContent(
    state: ReadMenuUiState,
    visible: Boolean,
    onAction: (ReaderMenuAction, Int) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessFinished: (Int) -> Unit,
    onClosed: () -> Unit,
    onHelp: () -> Unit,
    onOpenUrlPreference: () -> Unit
) {
    val duration = if (state.animate) LocalLegadoMotion.current.container else 0
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(visible, duration) {
        if (visible) {
            hasOpened = true
            delay(duration.toLong())
            onHelp()
        } else if (hasOpened) {
            delay(duration.toLong())
            onClosed()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 8 },
        exit = fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { it / 8 }
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .combinedClickable(onClick = { onAction(ReaderMenuAction.Dismiss, 0) })
            )
            Column(
                modifier = Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(state.background.copy(alpha = 0.96f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f).clickable {
                            onAction(ReaderMenuAction.BookInfo, 0)
                        }
                    ) {
                        if (state.title.isNotBlank()) {
                            Text(
                                text = state.title,
                                color = state.foreground,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (state.showTitleBarAddition && state.hasCustomButton) {
                        IconAction(
                            painter = painterResource(R.drawable.ic_custom),
                            description = stringResource(R.string.custom_button),
                            tint = state.accent,
                            onClick = { onAction(ReaderMenuAction.CustomButton, 0) },
                            onLongClick = { onAction(ReaderMenuAction.CustomButtonLong, 0) }
                        )
                    }
                    if (state.showTitleBarAddition && state.showSource) {
                        var sourceExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                onClick = { sourceExpanded = true },
                                shape = RoundedCornerShape(50),
                                color = state.accent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = state.sourceName.ifBlank { stringResource(R.string.book_source) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    color = state.foreground,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                                if (state.canLogin) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.login)) },
                                        onClick = { sourceExpanded = false; onAction(ReaderMenuAction.Login, 0) }
                                    )
                                }
                                if (state.canPayChapter) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chapter_pay)) },
                                        onClick = { sourceExpanded = false; onAction(ReaderMenuAction.PayChapter, 0) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_book_source)) },
                                    onClick = { sourceExpanded = false; onAction(ReaderMenuAction.EditSource, 0) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.disable_book_source)) },
                                    onClick = { sourceExpanded = false; onAction(ReaderMenuAction.DisableSource, 0) }
                                )
                            }
                        }
                    }
                }
                if (state.showTitleBarAddition && state.chapterName.isNotBlank()) {
                    Text(
                        text = state.chapterName,
                        modifier = Modifier.combinedClickable(
                            onClick = { if (!state.isLocalBook) onAction(ReaderMenuAction.OpenChapter, 0) },
                            onLongClick = onOpenUrlPreference
                        ),
                        color = state.foreground.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.showTitleBarAddition && state.chapterUrl.isNotBlank()) {
                    Text(
                        text = state.chapterUrl,
                        modifier = Modifier.fillMaxWidth().combinedClickable(
                            onClick = { onAction(ReaderMenuAction.OpenChapter, 0) },
                            onLongClick = onOpenUrlPreference
                        ),
                        color = state.foreground.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state.showBrightness) {
                Column(
                    modifier = Modifier.align(if (state.brightnessOnStart) Alignment.CenterStart else Alignment.CenterEnd)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp)
                        .background(state.background.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconAction(
                        painter = painterResource(R.drawable.ic_brightness_auto),
                        description = stringResource(
                            R.string.brightness_auto,
                            stringResource(if (state.brightnessAuto) R.string.enabled else R.string.disabled)
                        ),
                        tint = if (state.brightnessAuto) state.accent else state.foreground.copy(alpha = 0.55f),
                        onClick = { onAction(ReaderMenuAction.ToggleBrightnessAuto, 0) }
                    )
                    VerticalBrightnessSlider(
                        value = state.brightness.toFloat(),
                        enabled = state.brightnessEnabled,
                        accent = state.accent,
                        foreground = state.foreground,
                        onValueChange = onBrightnessChange,
                        onValueChangeFinished = { onBrightnessFinished(state.brightness) }
                    )
                    IconAction(
                        painter = painterResource(R.drawable.ic_swap_horiz),
                        description = stringResource(R.string.adjust_pos),
                        tint = state.foreground,
                        onClick = { onAction(ReaderMenuAction.ChangeBrightnessPosition, 0) }
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReaderQuickAction(
                        R.drawable.ic_search,
                        stringResource(R.string.search_content),
                        state.foreground
                    ) { onAction(ReaderMenuAction.Search, 0) }
                    ReaderQuickAction(
                        if (state.autoPage) R.drawable.ic_auto_page_stop else R.drawable.ic_auto_page,
                        stringResource(if (state.autoPage) R.string.auto_next_page_stop else R.string.auto_next_page),
                        state.foreground
                    ) { onAction(ReaderMenuAction.AutoPage, 0) }
                    ReaderQuickAction(
                        R.drawable.ic_find_replace,
                        stringResource(R.string.replace_rule_title),
                        state.foreground
                    ) { onAction(ReaderMenuAction.Replace, 0) }
                    ReaderQuickAction(
                        R.drawable.ic_brightness,
                        stringResource(R.string.dark_theme),
                        state.foreground
                    ) { onAction(ReaderMenuAction.NightTheme, 0) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(state.background.copy(alpha = 0.97f))
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onAction(ReaderMenuAction.PreviousChapter, 0) },
                            enabled = state.canGoPrevious
                        ) { Text(stringResource(R.string.previous_chapter), color = state.foreground) }
                        Slider(
                            value = state.progress.toFloat().coerceIn(0f, state.maxProgress.coerceAtLeast(1).toFloat()),
                            onValueChange = { onAction(ReaderMenuAction.PreviewPage, it.toInt()) },
                            onValueChangeFinished = {
                                if (state.chapterProgress) onAction(ReaderMenuAction.SeekChapter, state.progress)
                                else onAction(ReaderMenuAction.SeekPage, state.progress)
                            },
                            valueRange = 0f..state.maxProgress.coerceAtLeast(1).toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = state.accent,
                                activeTrackColor = state.accent,
                                inactiveTrackColor = state.foreground.copy(alpha = 0.24f)
                            )
                        )
                        TextButton(
                            onClick = { onAction(ReaderMenuAction.NextChapter, 0) },
                            enabled = state.canGoNext
                        ) { Text(stringResource(R.string.next_chapter), color = state.foreground) }
                    }
                    HorizontalDivider(color = state.foreground.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderAction(
                            R.drawable.ic_chapter_list,
                            stringResource(R.string.chapter_list),
                            state.foreground
                        ) { onAction(ReaderMenuAction.ChapterList, 0) }
                        ReaderAction(
                            R.drawable.ic_read_aloud,
                            stringResource(R.string.read_aloud),
                            state.foreground,
                            onLongClick = { onAction(ReaderMenuAction.ReadAloudSettings, 0) }
                        ) { onAction(ReaderMenuAction.ReadAloud, 0) }
                        ReaderAction(
                            R.drawable.ic_interface_setting,
                            stringResource(R.string.interface_setting),
                            state.foreground
                        ) { onAction(ReaderMenuAction.ReadStyle, 0) }
                        ReaderAction(
                            R.drawable.ic_settings,
                            stringResource(R.string.setting),
                            state.foreground
                        ) { onAction(ReaderMenuAction.MoreSettings, 0) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun SearchMenuContent(
    visible: Boolean,
    chapterTitle: String,
    resultCount: Int,
    hasResults: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSearchResults: () -> Unit,
    onMainMenu: () -> Unit,
    onExit: () -> Unit,
    onClosed: () -> Unit
) {
    val duration = LocalLegadoMotion.current.container
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(visible, duration) {
        if (visible) hasOpened = true
        else if (hasOpened) {
            delay(duration.toLong())
            onClosed()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 8 },
        exit = fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { it / 8 }
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f))
                    .combinedClickable(onClick = onExit)
            )
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.search_content_size)}  $resultCount  ·  $chapterTitle",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onPrevious,
                        enabled = hasResults,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(painterResource(R.drawable.ic_expand_less), "上一条搜索结果",
                            Modifier.padding(12.dp).size(24.dp))
                    }
                    Surface(
                        onClick = onNext,
                        enabled = hasResults,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(painterResource(R.drawable.ic_expand_more), "下一条搜索结果",
                            Modifier.padding(12.dp).size(24.dp))
                    }
                    TextButton(onClick = onSearchResults) { Text("搜索结果") }
                    TextButton(onClick = onMainMenu) { Text("主菜单") }
                    TextButton(onClick = onExit) { Text("退出") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MangaMenuContent(
    visible: Boolean,
    animate: Boolean,
    title: String,
    chapterName: String,
    chapterUrl: String,
    sourceName: String,
    showTitleBarAddition: Boolean,
    progress: Int,
    pageCount: Int,
    onAction: (ReaderMenuAction, Int) -> Unit,
    onClosed: () -> Unit
) {
    val duration = if (animate) LocalLegadoMotion.current.container else 0
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(visible, duration) {
        if (visible) hasOpened = true
        else if (hasOpened) {
            delay(duration.toLong())
            onClosed()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { it / 8 },
        exit = fadeOut(tween(duration)) + slideOutVertically(tween(duration)) { it / 8 }
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
                    .combinedClickable(onClick = { onAction(ReaderMenuAction.Dismiss, 0) })
            )
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAction(ReaderMenuAction.BookInfo, 0) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showTitleBarAddition) {
                    if (chapterName.isNotBlank()) {
                        Text(
                            chapterName,
                            modifier = Modifier.combinedClickable(
                                onClick = { onAction(ReaderMenuAction.OpenChapter, 0) },
                                onLongClick = { onAction(ReaderMenuAction.OpenChapter, 1) }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (chapterUrl.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                chapterUrl,
                                modifier = Modifier.weight(1f).combinedClickable(
                                    onClick = { onAction(ReaderMenuAction.OpenChapter, 0) },
                                    onLongClick = { onAction(ReaderMenuAction.OpenChapter, 1) }
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sourceName.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        sourceName,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onAction(ReaderMenuAction.PreviousChapter, 0) }) {
                    Text(stringResource(R.string.previous_chapter))
                }
                Slider(
                    value = progress.toFloat().coerceIn(0f, pageCount.coerceAtLeast(1).toFloat()),
                    onValueChange = { onAction(ReaderMenuAction.SeekPage, it.toInt()) },
                    onValueChangeFinished = { onAction(ReaderMenuAction.SeekPage, progress) },
                    valueRange = 0f..pageCount.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onAction(ReaderMenuAction.NextChapter, 0) }) {
                    Text(stringResource(R.string.next_chapter))
                }
            }
        }
    }
}

internal data class TextActionMenuEntry(val index: Int, val title: String)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun TextActionMenuContent(
    items: List<TextActionMenuEntry>,
    expanded: Boolean,
    showMore: Boolean,
    moreLabel: String,
    backLabel: String,
    onToggleMore: () -> Unit,
    onSelect: (Int) -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(max = 380.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(
                        onClick = { onSelect(item.index) },
                        onLongClick = onLongClick
                    ),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        item.title,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
            if (showMore) {
                Surface(
                    onClick = onToggleMore,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        if (expanded) backLabel else moreLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderAction(
    icon: Int,
    title: String,
    tint: Color,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(min = 62.dp).clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Text(title, color = tint, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconAction(
    painter: Painter,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(painter, description, modifier = Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun ReaderQuickAction(
    icon: Int,
    description: String,
    foreground: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = foreground.copy(alpha = 0.12f),
        contentColor = foreground
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(22.dp),
                tint = foreground
            )
        }
    }
}

@Composable
private fun VerticalBrightnessSlider(
    value: Float,
    enabled: Boolean,
    accent: Color,
    foreground: Color,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Layout(
        content = {
            Slider(
                value = value,
                onValueChange = { onValueChange(it.toInt()) },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 0f..255f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = foreground.copy(alpha = 0.24f)
                )
            )
        },
        modifier = Modifier.size(width = 48.dp, height = 160.dp)
    ) { measurables, _ ->
        val width = 160.dp.roundToPx()
        val height = 48.dp.roundToPx()
        val slider = measurables.single().measure(Constraints.fixed(width, height))
        val containerWidth = 48.dp.roundToPx()
        val containerHeight = 160.dp.roundToPx()
        layout(containerWidth, containerHeight) {
            slider.placeRelativeWithLayer(
                x = (containerWidth - slider.width) / 2,
                y = (containerHeight - slider.height) / 2
            ) {
                rotationZ = 270f
            }
        }
    }
}
