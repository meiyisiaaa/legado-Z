package io.legado.app.ui.ai

import android.widget.TextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.width
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.utils.setMarkdown
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AiChatContent(
    mode: Int,
    bookmark: Bookmark,
    items: List<AiChatItem>,
    loading: Boolean,
    saving: Boolean,
    lastSavedItemId: Long?,
    onSend: (String) -> Boolean,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveNote: () -> Unit,
    onCopy: (String) -> Unit,
    onImageLongClick: (String) -> Unit
) {
    var question by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val latestAnswer = items.lastOrNull { it.role == AiChatRole.ASSISTANT }
    val lastQuestion = items.lastOrNull { it.role == AiChatRole.USER }
    val canRetry = !loading && (lastQuestion?.status == AiChatStatus.FAILED || lastQuestion?.status == AiChatStatus.CANCELLED)
    val canSave = !saving && latestAnswer != null && latestAnswer.id != lastSavedItemId
    val context = LocalContext.current
    val assistantColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markdown = remember(context) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (mode == AiChatBottomSheet.MODE_EXPLAIN) R.string.ai_explain_title else R.string.ai_qa_title)) },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_save_note)) },
                            enabled = canSave,
                            onClick = { menuExpanded = false; onSaveNote() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_qa_retry)) },
                            enabled = canRetry,
                            onClick = { menuExpanded = false; onRetry() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_qa_clear)) },
                            onClick = { menuExpanded = false; onClear() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_settings)) },
                            onClick = { menuExpanded = false; onOpenSettings() }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    stringResource(R.string.ai_disclaimer_reader),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.ai_qa_hint)) },
                        minLines = 1,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (loading) onStop() else if (onSend(question)) question = ""
                        })
                    )
                    Button(onClick = {
                        if (loading) onStop()
                        else if (onSend(question)) question = ""
                    }, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text(stringResource(if (loading) R.string.ai_qa_stop else R.string.ai_qa_send))
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Text(
                stringResource(R.string.ai_selection_scope, bookmark.bookText.length),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 0.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { if (item.role == AiChatRole.ASSISTANT) onCopy(item.text) }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (item.role == AiChatRole.USER) {
                            Text(
                                item.text,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.End
                            )
                        } else {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { TextView(it).apply { textSize = 16f } },
                                update = { textView ->
                                    textView.setTextColor(assistantColor)
                                    textView.setMarkdown(
                                        markdown,
                                        markdown.toMarkdown(item.text),
                                        imgOnLongClickListener = onImageLongClick
                                    )
                                }
                            )
                        }
                        if (item.footer.isNotBlank()) {
                            Text(
                                item.footer,
                                Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (loading) {
                    item(key = "waiting") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
