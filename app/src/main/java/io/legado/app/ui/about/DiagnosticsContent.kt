package io.legado.app.ui.about

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoDialogHeader

internal data class DiagnosticItem(val title: String, val subtitle: String = "", val hasDetails: Boolean = true)

@Composable
internal fun DiagnosticsContent(title: String, items: List<DiagnosticItem>, loading: Boolean = false,
    error: String? = null, onClear: () -> Unit, onOpen: (Int) -> Unit,
    onRetry: () -> Unit = {}, onClose: () -> Unit) {
    val loadingDescription = stringResource(R.string.loading)
    Surface(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
        Column(Modifier.navigationBarsPadding()) {
            LegadoDialogHeader(title, onClose) {
                TextButton(onClear, enabled = !loading && items.isNotEmpty()) { Text(stringResource(R.string.clear)) }
            }
            when {
                loading -> LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(20.dp).semantics { contentDescription = loadingDescription }
                )
                error != null -> Column(Modifier.padding(24.dp)) {
                    Text(error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onRetry) { Text(stringResource(R.string.retry)) }
                }
                items.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.diagnostics_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(items) { index, item ->
                        Surface(
                            onClick = { onOpen(index) },
                            enabled = item.hasDetails,
                            modifier = Modifier.semantics { role = Role.Button },
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (item.subtitle.isNotBlank()) Text(item.subtitle,
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
