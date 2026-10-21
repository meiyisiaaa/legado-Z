package io.legado.app.ui.book.info.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

internal data class BookInfoEditUiState(
    val name: String = "",
    val author: String = "",
    val type: Int = 0,
    val coverUrl: String = "",
    val intro: String = ""
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookInfoEditScreen(
    state: BookInfoEditUiState,
    onStateChange: (BookInfoEditUiState) -> Unit,
    coverContent: @Composable (Modifier) -> Unit,
    onSelectCover: () -> Unit,
    onChangeCover: () -> Unit,
    onRefreshCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val types = stringArrayResource(R.array.book_type)
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.size(width = 96.dp, height = 138.dp)) {
                        coverContent(Modifier.fillMaxSize())
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "EDIT / BOOK PROFILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("封面预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("编辑书名、作者与书籍类型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onStateChange(state.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.book_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = state.author,
                    onValueChange = { onStateChange(state.copy(author = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.author)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.book_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(14.dp)) {
                Text(types.getOrNull(state.type) ?: types.firstOrNull().orEmpty())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                types.forEachIndexed { index, type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            onStateChange(state.copy(type = index))
                            expanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.coverUrl,
            onValueChange = { onStateChange(state.copy(coverUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.cover_path)) },
            singleLine = false,
            shape = RoundedCornerShape(16.dp)
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSelectCover, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.select_local_image))
            }
            OutlinedButton(onClick = onChangeCover, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.change_cover_source))
            }
            Button(onClick = {
                focusManager.clearFocus()
                onRefreshCover()
            }, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.refresh_cover))
            }
        }

        OutlinedTextField(
            value = state.intro,
            onValueChange = { onStateChange(state.copy(intro = it)) },
            modifier = Modifier.fillMaxWidth().height(220.dp),
            label = { Text(stringResource(R.string.book_intro)) },
            minLines = 6,
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun BookInfoEditScreenPreview() {
    LegadoTheme {
        BookInfoEditScreen(
            state = BookInfoEditUiState(
                name = "星海纪元",
                author = "巡山小妖",
                type = 0,
                coverUrl = "https://example.org/cover.jpg",
                intro = "文明航船驶出母星，群星之间的历史从此改写。"
            ),
            onStateChange = {},
            coverContent = { modifier -> Text("BOOK COVER", modifier) },
            onSelectCover = {},
            onChangeCover = {},
            onRefreshCover = {}
        )
    }
}
