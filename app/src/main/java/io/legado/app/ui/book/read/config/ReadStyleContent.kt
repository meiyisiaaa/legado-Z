package io.legado.app.ui.book.read.config

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.compose.LegadoTheme

internal data class ReadStyleUiState(
    val textSize: Int,
    val letterSpacing: Float,
    val lineSpacingExtra: Int,
    val paragraphSpacing: Int,
    val pageAnim: Int,
    val shareLayout: Boolean,
    val styleSelect: Int,
    val textBold: Int,
    val chineseConverterType: Int,
    val textFont: String,
    val paragraphIndent: Int,
    val configs: List<ReadBookConfig.Config>
)

@Composable
internal fun ReadStyleContent(
    state: ReadStyleUiState,
    onTextSizeChange: (Int) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onLineSpacingChange: (Int) -> Unit,
    onParagraphSpacingChange: (Int) -> Unit,
    onPageAnimChange: (Int) -> Unit,
    onShareLayoutChange: (Boolean) -> Unit,
    onTextBoldClick: () -> Unit,
    onFontClick: () -> Unit,
    onIndentClick: () -> Unit,
    onPaddingClick: () -> Unit,
    onTipClick: () -> Unit,
    onChineseClick: () -> Unit,
    onStyleSelect: (Int) -> Unit,
    onStyleEdit: (Int) -> Unit,
    onAddStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalLegadoMotion.current
    val pageAnimationNames = listOf(
        stringResource(R.string.page_anim_cover),
        stringResource(R.string.page_anim_slide),
        stringResource(R.string.page_anim_simulation),
        stringResource(R.string.page_anim_scroll),
        stringResource(R.string.page_anim_none)
    )
    val addStyleDescription = "${stringResource(R.string.add)} ${stringResource(R.string.text_bg_style)}"
    val selected = state.styleSelect

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReadStyleWeightTool(state.textBold, onTextBoldClick)
                ReadStyleTool(stringResource(R.string.text_font), onFontClick)
                ReadStyleTool(stringResource(R.string.text_indent), onIndentClick)
                ReadStyleChineseTool(state.chineseConverterType, onChineseClick)
                ReadStyleTool(stringResource(R.string.padding), onPaddingClick)
                ReadStyleTool(stringResource(R.string.information), onTipClick)
            }
        }
        item {
            ReaderConfigSlider(
                title = stringResource(R.string.text_size),
                value = (state.textSize - 5).toFloat(),
                valueRange = 0f..45f,
                valueText = state.textSize.toString(),
                onValueChange = { onTextSizeChange(it.toInt() + 5) }
            )
            ReaderConfigSlider(
                title = stringResource(R.string.text_letter_spacing),
                value = (state.letterSpacing * 100 + 50).coerceIn(0f, 100f),
                valueRange = 0f..100f,
                valueText = state.letterSpacing.toString(),
                onValueChange = { onLetterSpacingChange((it.toInt() - 50) / 100f) }
            )
            ReaderConfigSlider(
                title = stringResource(R.string.line_size),
                value = state.lineSpacingExtra.toFloat(),
                valueRange = 0f..20f,
                valueText = ((state.lineSpacingExtra - 10) / 10f).toString(),
                onValueChange = { onLineSpacingChange(it.toInt()) }
            )
            ReaderConfigSlider(
                title = stringResource(R.string.paragraph_size),
                value = state.paragraphSpacing.toFloat(),
                valueRange = 0f..20f,
                valueText = (state.paragraphSpacing / 10f).toString(),
                onValueChange = { onParagraphSpacingChange(it.toInt()) }
            )
        }
        item {
            Column {
                Text(
                    text = stringResource(R.string.page_anim),
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pageAnimationNames.forEachIndexed { index, label ->
                        val selected = state.pageAnim == index
                        Surface(
                            modifier = Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onPageAnimChange(index) }
                            ),
                            shape = RoundedCornerShape(4.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.text_bg_style),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.share_layout),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Checkbox(
                        checked = state.shareLayout,
                        onCheckedChange = onShareLayoutChange
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.configs.forEachIndexed { index, config ->
                        ReadStyleCard(
                            config = config,
                            selected = index == selected,
                            onClick = { onStyleSelect(index) },
                            onLongClick = { onStyleEdit(index) },
                            motionDuration = motion.press
                        )
                    }
                    Surface(
                        onClick = onAddStyle,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = addStyleDescription },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadStyleTool(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReadStyleWeightTool(selected: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.font_weight_text)
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = buildAnnotatedString {
                label.forEachIndexed { index, character ->
                    val optionIndex = when (index) {
                        0 -> 0
                        2 -> 1
                        4 -> 2
                        else -> -1
                    }
                    pushStyle(
                        SpanStyle(
                            color = if (optionIndex == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (optionIndex == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    append(character)
                    pop()
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun ReadStyleChineseTool(selected: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = buildAnnotatedString {
                append("简/繁")
                if (selected in 1..2) {
                    addStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        start = if (selected == 1) 0 else 2,
                        end = if (selected == 1) 1 else 3
                    )
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadStyleCard(
    config: ReadBookConfig.Config,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    motionDuration: Int
) {
    val context = LocalContext.current
    val painter = remember(config) {
        runCatching {
            BitmapPainter(config.curBgDrawable(100, 150).toBitmap(100, 150).asImageBitmap())
        }.getOrNull()
    }
    val borderColor = animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color(config.curTextColor()),
        animationSpec = tween(durationMillis = motionDuration),
        label = "reader style selection"
    ).value
    Surface(
        modifier = Modifier
            .size(48.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = config.name.ifBlank { context.getString(R.string.text) }
            },
        color = if (config.curBgType() == 0) Color(config.curBgStr().toColorInt())
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Box {
            painter?.let {
                Image(
                    painter = it,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().clip(CircleShape)
                )
            }
            Text(
                text = config.name.ifBlank { "文字" },
                color = Color(config.curTextColor()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(5.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadStyleContentPreview() {
    LegadoTheme {
        ReadStyleContent(
            state = ReadStyleUiState(
                textSize = 20,
                letterSpacing = 0.1f,
                lineSpacingExtra = 12,
                paragraphSpacing = 2,
                pageAnim = 0,
                shareLayout = false,
                styleSelect = 0,
                textBold = 0,
                chineseConverterType = 0,
                textFont = "",
                paragraphIndent = 2,
                configs = listOf(ReadBookConfig.Config())
            ),
            onTextSizeChange = {}, onLetterSpacingChange = {}, onLineSpacingChange = {},
            onParagraphSpacingChange = {}, onPageAnimChange = {}, onShareLayoutChange = {},
            onTextBoldClick = {}, onFontClick = {}, onIndentClick = {}, onPaddingClick = {},
            onTipClick = {}, onChineseClick = {}, onStyleSelect = {}, onStyleEdit = {}, onAddStyle = {}
        )
    }
}
