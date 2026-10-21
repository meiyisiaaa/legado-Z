package io.legado.app.ui.book.import

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R

internal data class ImportBrowserItem(
    val key: String,
    val title: String,
    val badge: String,
    val summary: String,
    val isDirectory: Boolean,
    val inBookshelf: Boolean,
    val selected: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImportBrowserContent(
    items: List<ImportBrowserItem>,
    emptyMessage: String,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit
) {
    if (items.isEmpty()) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(items) { index, item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    .combinedClickable(
                        onClick = { onItemClick(index) },
                        onLongClick = { onItemLongClick(index) }
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        item.isDirectory -> Icon(
                            painter = painterResource(R.drawable.ic_folder),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        item.inBookshelf -> Icon(
                            painter = painterResource(R.drawable.ic_book_has),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        else -> Checkbox(checked = item.selected, onCheckedChange = { onItemClick(index) })
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.isDirectory) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.badge,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    item.summary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (item.isDirectory) {
                        Icon(painterResource(R.drawable.ic_arrow_right), null,
                            Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
