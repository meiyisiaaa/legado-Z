package io.legado.app.ui.rss.article

import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.RssReadRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssReadRecordContent(
    records: List<RssReadRecord>,
    onRead: (RssReadRecord) -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }
    val recordColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.read_record)) },
                actions = { TextButton(onClick = { confirmClear = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
                    Text(stringResource(R.string.clear))
                } }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("暂无阅读记录", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(records, key = { "${it.origin}\u0000${it.record}\u0000${it.readTime}" }) { record ->
                    Column(Modifier.fillMaxWidth().padding(6.dp)) {
                        Text(
                            text = record.title.orEmpty(),
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .clickable { onRead(record) }
                                .padding(bottom = 8.dp),
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    autoLinkMask = Linkify.WEB_URLS
                                    maxLines = 1
                                    isSingleLine = true
                                    setHorizontallyScrolling(true)
                                    textSize = 12f
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            update = {
                                it.text = record.record
                                it.setTextColor(recordColor)
                            }
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.draw)) },
            text = { Text("${stringResource(R.string.sure_del)}\n${records.size} ${stringResource(R.string.read_record)}") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text(stringResource(R.string.yes)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.no)) } }
        )
    }
}
