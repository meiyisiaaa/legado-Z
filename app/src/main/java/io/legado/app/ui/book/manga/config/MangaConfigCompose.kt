package io.legado.app.ui.book.manga.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.ReaderInfoBarView

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MangaFooterContent(
    config: MangaFooterConfig,
    onChange: (MangaFooterConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MangaFooterSection(stringResource(R.string.manga_header_chapter)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FooterCheck(stringResource(R.string.manga_check_chapter_label), config.hideChapterLabel) {
                    onChange(config.copy(hideChapterLabel = it))
                }
                FooterCheck(stringResource(R.string.manga_check_chapter), config.hideChapter) {
                    onChange(config.copy(hideChapter = it))
                }
                FooterCheck(stringResource(R.string.manga_check_chapter_name), config.hideChapterName) {
                    onChange(config.copy(hideChapterName = it))
                }
            }
        }
        MangaFooterSection(stringResource(R.string.manga_header_page)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FooterCheck(stringResource(R.string.manga_check_page_label), config.hidePageNumberLabel) {
                    onChange(config.copy(hidePageNumberLabel = it))
                }
                FooterCheck(stringResource(R.string.manga_check_page_number), config.hidePageNumber) {
                    onChange(config.copy(hidePageNumber = it))
                }
            }
        }
        MangaFooterSection(stringResource(R.string.manga_header_progress)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FooterCheck(stringResource(R.string.manga_check_progress_label), config.hideProgressRatioLabel) {
                    onChange(config.copy(hideProgressRatioLabel = it))
                }
                FooterCheck(stringResource(R.string.manga_check_progress), config.hideProgressRatio) {
                    onChange(config.copy(hideProgressRatio = it))
                }
            }
        }
        MangaFooterSection(stringResource(R.string.manga_header_footer)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FooterRadio(
                    title = stringResource(R.string.show),
                    selected = !config.hideFooter,
                    modifier = Modifier.weight(1f)
                ) { onChange(config.copy(hideFooter = false)) }
                FooterRadio(
                    title = stringResource(R.string.hide),
                    selected = config.hideFooter,
                    modifier = Modifier.weight(1f)
                ) { onChange(config.copy(hideFooter = true)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FooterRadio(
                    title = stringResource(R.string.manga_radio_left),
                    selected = config.footerOrientation != ReaderInfoBarView.ALIGN_CENTER,
                    modifier = Modifier.weight(1f)
                ) { onChange(config.copy(footerOrientation = ReaderInfoBarView.ALIGN_LEFT)) }
                FooterRadio(
                    title = stringResource(R.string.manga_radio_center),
                    selected = config.footerOrientation == ReaderInfoBarView.ALIGN_CENTER,
                    modifier = Modifier.weight(1f)
                ) { onChange(config.copy(footerOrientation = ReaderInfoBarView.ALIGN_CENTER)) }
            }
        }
    }
}

@Composable
private fun MangaFooterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        content()
    }
}

@Composable
private fun FooterCheck(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(start = 8.dp, top = 2.dp, end = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FooterRadio(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    Row(
        modifier = modifier
            .toggleable(selected, role = Role.RadioButton, onValueChange = { checked ->
                if (checked) onSelect()
            })
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
