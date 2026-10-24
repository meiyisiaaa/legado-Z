package io.legado.app.ui.book.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

@Composable
internal fun BookmarkEditScreen(
    chapterName: String,
    initialBookText: String,
    initialNote: String,
    canDelete: Boolean,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bookText by rememberSaveable(chapterName, initialBookText) {
        mutableStateOf(initialBookText)
    }
    var note by rememberSaveable(chapterName, initialNote) { mutableStateOf(initialNote) }

    Box(modifier = modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.bookmark),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = chapterName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = bookText,
                        onValueChange = { bookText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.content)) },
                        minLines = 4
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.note_content)) },
                        minLines = 3
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canDelete) {
                        TextButton(onClick = onDelete) {
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = { onSave(bookText, note) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookmarkEditScreenPreview() {
    LegadoTheme {
        BookmarkEditScreen(
            chapterName = "第 42 章 · 望向星河",
            initialBookText = "他们第一次意识到，星海并没有边界。",
            initialNote = "星河、文明与漫长的等待",
            canDelete = true,
            onSave = { _, _ -> },
            onDelete = {},
            onCancel = {}
        )
    }
}
