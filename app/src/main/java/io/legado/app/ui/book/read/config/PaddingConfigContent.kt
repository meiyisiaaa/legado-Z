package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

internal enum class PaddingField {
    BODY_TOP, BODY_BOTTOM, BODY_LEFT, BODY_RIGHT,
    HEADER_TOP, HEADER_BOTTOM, HEADER_LEFT, HEADER_RIGHT,
    FOOTER_TOP, FOOTER_BOTTOM, FOOTER_LEFT, FOOTER_RIGHT
}

internal data class PaddingUiState(
    val values: Map<PaddingField, Int>,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean
)

@Composable
internal fun PaddingConfigContent(
    state: PaddingUiState,
    onValueChange: (PaddingField, Int) -> Unit,
    onHeaderLineChange: (Boolean) -> Unit,
    onFooterLineChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.padding),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.main_body)) {
                PaddingSlider(state, PaddingField.BODY_TOP, R.string.padding_top, 200, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.BODY_BOTTOM, R.string.padding_bottom, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.BODY_LEFT, R.string.padding_left, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.BODY_RIGHT, R.string.padding_right, 100, onValueChange)
            }
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.header)) {
                ReaderConfigSwitch(
                    title = stringResource(R.string.showLine),
                    checked = state.showHeaderLine,
                    onCheckedChange = onHeaderLineChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.HEADER_TOP, R.string.padding_top, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.HEADER_BOTTOM, R.string.padding_bottom, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.HEADER_LEFT, R.string.padding_left, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.HEADER_RIGHT, R.string.padding_right, 100, onValueChange)
            }
        }
        item {
            ReaderConfigSection(title = stringResource(R.string.footer)) {
                ReaderConfigSwitch(
                    title = stringResource(R.string.showLine),
                    checked = state.showFooterLine,
                    onCheckedChange = onFooterLineChange
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.FOOTER_TOP, R.string.padding_top, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.FOOTER_BOTTOM, R.string.padding_bottom, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.FOOTER_LEFT, R.string.padding_left, 100, onValueChange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PaddingSlider(state, PaddingField.FOOTER_RIGHT, R.string.padding_right, 100, onValueChange)
            }
        }
    }
}

@Composable
private fun PaddingSlider(
    state: PaddingUiState,
    field: PaddingField,
    titleRes: Int,
    max: Int,
    onValueChange: (PaddingField, Int) -> Unit
) {
    val value = state.values[field] ?: 0
    ReaderConfigSlider(
        title = stringResource(titleRes),
        value = value.toFloat(),
        valueRange = 0f..max.toFloat(),
        valueText = value.toString(),
        onValueChange = { onValueChange(field, it.toInt()) }
    )
}

@Preview(showBackground = true)
@Composable
private fun PaddingConfigPreview() {
    LegadoTheme {
        PaddingConfigContent(
            state = PaddingUiState(
                values = PaddingField.entries.associateWith { 8 },
                showHeaderLine = false,
                showFooterLine = true
            ),
            onValueChange = { _, _ -> },
            onHeaderLineChange = {},
            onFooterLineChange = {}
        )
    }
}
