package io.legado.app.ui.book.read.config

import android.widget.ImageView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Slider
import io.legado.app.R
import io.legado.app.help.glide.ImageLoader
import io.legado.app.ui.widget.compose.LegadoTheme
import java.io.File

internal data class BgTextConfigUiState(
    val name: String,
    val darkStatusIcon: Boolean,
    val isImageBook: Boolean,
    val underlineMode: Int,
    val underlineOptions: List<String>,
    val textColor: Int,
    val backgroundColor: Int,
    val accentColor: Int,
    val backgroundAlpha: Int,
    val imageNames: List<String>,
    val backgroundType: Int,
    val backgroundValue: String
)

@Composable
internal fun BgTextConfigContent(
    state: BgTextConfigUiState,
    onEditName: () -> Unit,
    onRestore: () -> Unit,
    onDarkStatusIconChange: (Boolean) -> Unit,
    onUnderlineClick: () -> Unit,
    onTextColorClick: () -> Unit,
    onBackgroundColorClick: () -> Unit,
    onAccentColorClick: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onBackgroundAlphaChange: (Int) -> Unit,
    onBackgroundAlphaFinished: () -> Unit,
    onSelectImage: () -> Unit,
    onAssetSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val alphaLabel = stringResource(R.string.bg_alpha)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.style_name), style = MaterialTheme.typography.titleSmall)
            Text(
                state.name.ifBlank { stringResource(R.string.text) },
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onEditName) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit))
            }
            TextButton(onClick = onRestore) { Text(stringResource(R.string.restore)) }
        }
        ReaderConfigSection(title = stringResource(R.string.setting)) {
            ReaderConfigSwitch(
                title = stringResource(R.string.dark_status_icon),
                checked = state.darkStatusIcon,
                onCheckedChange = onDarkStatusIconChange
            )
            if (!state.isImageBook) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReaderConfigRow(
                    title = stringResource(R.string.text_underline),
                    summary = state.underlineOptions.getOrElse(state.underlineMode) { state.underlineOptions.firstOrNull().orEmpty() },
                    onClick = onUnderlineClick
                )
            }
        }
        ReaderConfigSection(title = stringResource(R.string.read_style)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorConfigChip(R.string.text_color, state.textColor, onTextColorClick)
                ColorConfigChip(R.string.bg_color, state.backgroundColor, onBackgroundColorClick)
                ColorConfigChip(R.string.text_accent_color, state.accentColor, onAccentColorClick)
            }
        }
        ReaderConfigSection(title = stringResource(R.string.import_str) + " / " + stringResource(R.string.export_str)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconActionButton(R.drawable.ic_import, R.string.import_str, onImport)
                IconActionButton(R.drawable.ic_export, R.string.export_str, onExport)
                IconActionButton(R.drawable.ic_clear_all, R.string.delete, onDelete)
            }
        }
        ReaderConfigSection(title = stringResource(R.string.bg_alpha)) {
            Slider(
                value = state.backgroundAlpha.coerceIn(0, 100).toFloat(),
                onValueChange = { onBackgroundAlphaChange(it.toInt()) },
                onValueChangeFinished = onBackgroundAlphaFinished,
                valueRange = 0f..100f,
                modifier = Modifier.padding(horizontal = 12.dp).semantics { contentDescription = alphaLabel }
            )
        }
        ReaderConfigSection(title = stringResource(R.string.bg_image)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "choose-image") {
                    AssetBackgroundTile(
                        name = stringResource(R.string.select_image),
                        selected = state.backgroundType == 2,
                        onClick = onSelectImage
                    )
                }
                items(state.imageNames, key = { it }) { name ->
                    AssetBackgroundTile(
                        name = name.substringBeforeLast("."),
                        assetPath = "bg${File.separator}$name",
                        selected = state.backgroundType == 1 && state.backgroundValue == name,
                        onClick = { onAssetSelect(name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorConfigChip(label: Int, color: Int, onClick: () -> Unit) {
    val labelText = stringResource(label)
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .animateContentSize()
            .semantics { contentDescription = labelText },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                shape = RoundedCornerShape(50),
                color = Color(color)
            ) {}
            Text(labelText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun IconActionButton(icon: Int, label: Int, onClick: () -> Unit) {
    val description = stringResource(label)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = null)
            Text(description, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AssetBackgroundTile(
    name: String,
    assetPath: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 72.dp, height = 92.dp)
            .clickable(onClick = onClick)
            .animateContentSize()
            .semantics { contentDescription = name },
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (assetPath != null) {
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(3.dp),
                    factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                    update = { image ->
                        runCatching {
                            val bytes = image.context.assets.open(assetPath).use { it.readBytes() }
                            ImageLoader.load(image.context, bytes).centerCrop().into(image)
                        }
                    }
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).padding(8.dp)
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BgTextConfigPreview() {
    LegadoTheme {
        BgTextConfigContent(
            state = BgTextConfigUiState("Text", false, false, 0, listOf("Off", "Solid", "Dashed"),
                0xFF333333.toInt(), 0xFFFFFFFF.toInt(), 0xFF448AFF.toInt(), 100, emptyList(), 0, "#FFFFFF"),
            onEditName = {}, onRestore = {}, onDarkStatusIconChange = {}, onUnderlineClick = {},
            onTextColorClick = {}, onBackgroundColorClick = {}, onAccentColorClick = {}, onImport = {},
            onExport = {}, onDelete = {}, onBackgroundAlphaChange = {}, onBackgroundAlphaFinished = {},
            onSelectImage = {}, onAssetSelect = {}
        )
    }
}
