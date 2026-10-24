package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

internal enum class TipPosition { HEADER_LEFT, HEADER_MIDDLE, HEADER_RIGHT, FOOTER_LEFT, FOOTER_MIDDLE, FOOTER_RIGHT }

internal data class TipConfigUiState(
    val titleMode: Int,
    val titleSize: Int,
    val titleTopSpacing: Int,
    val titleBottomSpacing: Int,
    val headerMode: Int,
    val footerMode: Int,
    val values: Map<TipPosition, String>,
    val tipColor: String,
    val dividerColor: String
)

@Composable
internal fun TipConfigContent(
    state: TipConfigUiState,
    onTitleModeChange: (Int) -> Unit,
    onTitleSizeChange: (Int) -> Unit,
    onTitleTopChange: (Int) -> Unit,
    onTitleBottomChange: (Int) -> Unit,
    onHeaderModeClick: () -> Unit,
    onFooterModeClick: () -> Unit,
    onPositionClick: (TipPosition) -> Unit,
    onTipColorClick: () -> Unit,
    onDividerColorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleModes = listOf(
        stringResource(R.string.title_left),
        stringResource(R.string.title_center),
        stringResource(R.string.title_hide)
    )
    val names = stringArrayResource(R.array.read_tip).toList()

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.information),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.body_title)) {
                titleModes.forEachIndexed { index, label ->
                    ReaderConfigRow(
                        title = label,
                        trailing = { RadioButton(selected = state.titleMode == index, onClick = null) },
                        onClick = { onTitleModeChange(index) }
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReaderConfigSlider(
                    title = stringResource(R.string.text_size),
                    value = state.titleSize.toFloat(),
                    valueRange = 0f..20f,
                    valueText = state.titleSize.toString(),
                    onValueChange = { onTitleSizeChange(it.toInt()) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReaderConfigSlider(
                    title = stringResource(R.string.padding_top),
                    value = state.titleTopSpacing.toFloat(),
                    valueRange = 0f..100f,
                    valueText = state.titleTopSpacing.toString(),
                    onValueChange = { onTitleTopChange(it.toInt()) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReaderConfigSlider(
                    title = stringResource(R.string.padding_bottom),
                    value = state.titleBottomSpacing.toFloat(),
                    valueRange = 0f..100f,
                    valueText = state.titleBottomSpacing.toString(),
                    onValueChange = { onTitleBottomChange(it.toInt()) }
                )
            }
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.header)) {
                ReaderConfigRow(
                    title = stringResource(R.string.show_hide),
                    summary = namesForHeaderMode(state.headerMode),
                    onClick = onHeaderModeClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TipPosition.entries.take(3).forEach { position ->
                    ReaderConfigRow(
                        title = positionTitle(position),
                        summary = state.values[position].orEmpty(),
                        onClick = { onPositionClick(position) }
                    )
                }
            }
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.footer)) {
                ReaderConfigRow(
                    title = stringResource(R.string.show_hide),
                    summary = namesForFooterMode(state.footerMode),
                    onClick = onFooterModeClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TipPosition.entries.drop(3).forEach { position ->
                    ReaderConfigRow(
                        title = positionTitle(position),
                        summary = state.values[position].orEmpty(),
                        onClick = { onPositionClick(position) }
                    )
                }
            }
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.header_footer)) {
                ReaderConfigRow(
                    title = stringResource(R.string.text_color),
                    summary = state.tipColor,
                    onClick = onTipColorClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReaderConfigRow(
                    title = stringResource(R.string.tip_divider_color),
                    summary = state.dividerColor,
                    onClick = onDividerColorClick
                )
            }
        }
    }
}

@Composable
private fun positionTitle(position: TipPosition): String = when (position) {
    TipPosition.HEADER_LEFT, TipPosition.FOOTER_LEFT -> stringResource(R.string.left)
    TipPosition.HEADER_MIDDLE, TipPosition.FOOTER_MIDDLE -> stringResource(R.string.middle)
    TipPosition.HEADER_RIGHT, TipPosition.FOOTER_RIGHT -> stringResource(R.string.right)
}

@Composable
private fun namesForHeaderMode(mode: Int): String {
    val options = listOf(
        stringResource(R.string.hide_when_status_bar_show),
        stringResource(R.string.show),
        stringResource(R.string.hide)
    )
    return options.getOrElse(mode) { options.first() }
}

@Composable
private fun namesForFooterMode(mode: Int): String {
    val options = listOf(stringResource(R.string.show), stringResource(R.string.hide))
    return options.getOrElse(mode) { options.first() }
}

@Preview(showBackground = true)
@Composable
private fun TipConfigPreview() {
    LegadoTheme {
        TipConfigContent(
            state = TipConfigUiState(
                titleMode = 0,
                titleSize = 0,
                titleTopSpacing = 0,
                titleBottomSpacing = 0,
                headerMode = 0,
                footerMode = 0,
                values = TipPosition.entries.associateWith { "—" },
                tipColor = "Default",
                dividerColor = "None"
            ),
            onTitleModeChange = {}, onTitleSizeChange = {}, onTitleTopChange = {},
            onTitleBottomChange = {}, onHeaderModeClick = {}, onFooterModeClick = {},
            onPositionClick = {}, onTipColorClick = {}, onDividerColorClick = {}
        )
    }
}
