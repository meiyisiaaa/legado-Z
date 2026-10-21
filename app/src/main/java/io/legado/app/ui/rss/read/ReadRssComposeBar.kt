package io.legado.app.ui.rss.read

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadRssComposeBar(
    title: String,
    hasArticle: Boolean,
    isFavorite: Boolean,
    isSpeaking: Boolean,
    hasLogin: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onSpeak: () -> Unit,
    onAction: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        },
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(painterResource(R.drawable.ic_refresh_black_24dp), contentDescription = stringResource(R.string.refresh))
            }
            if (hasArticle) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                        contentDescription = stringResource(if (isFavorite) R.string.in_favorites else R.string.out_favorites)
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = stringResource(R.string.share))
            }
            IconButton(onClick = onSpeak) {
                Icon(
                    painterResource(if (isSpeaking) R.drawable.ic_stop_black_24dp else R.drawable.ic_volume_up),
                    contentDescription = stringResource(if (isSpeaking) R.string.aloud_stop else R.string.read_aloud)
                )
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.menu))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (hasLogin) ReadRssMenuItem(R.id.menu_login, R.string.login, onAction) { menuExpanded = false }
                ReadRssMenuItem(R.id.menu_browser_open, R.string.open_in_browser, onAction) { menuExpanded = false }
                ReadRssMenuItem(R.id.menu_read_record, R.string.read_record, onAction) { menuExpanded = false }
                ReadRssMenuItem(R.id.menu_edit_source, R.string.edit_source, onAction) { menuExpanded = false }
                ReadRssMenuItem(R.id.menu_log, R.string.log, onAction) { menuExpanded = false }
            }
        }
    )
}

@Composable
private fun ReadRssMenuItem(id: Int, label: Int, onAction: (Int) -> Unit, dismiss: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { dismiss(); onAction(id) })
}
