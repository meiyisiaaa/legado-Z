package io.legado.app.ui.main.bookshelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.compose.LocalLegadoMotion

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfGroups(groups: List<BookGroup>, selectedIndex: Int,
    onSelect: (Int) -> Unit, onEdit: (BookGroup) -> Unit) {
    val motion = LocalLegadoMotion.current
    val toolbarColor = if (LocalInspectionMode.current) MaterialTheme.colorScheme.surface
        else Color(ThemeStore.primaryColor(LocalContext.current))
    val ink = if (toolbarColor.luminance() > 0.179f) Color.Black else Color.White
    Surface(color = Color.Transparent) {
        LazyRow(Modifier.selectableGroup().height(48.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
            itemsIndexed(groups, key = { _, group -> group.groupId }) { index, group ->
                val selected = index == selectedIndex
                val foreground by animateColorAsState(
                    if (selected) ink else ink.copy(alpha = 0.8f),
                    tween(motion.state), label = "group selection text")
                val indicator by animateFloatAsState(if (selected) 1f else 0f,
                    tween(motion.state), label = "group underline")
                Column(
                    modifier = Modifier.widthIn(min = 56.dp, max = 144.dp).width(IntrinsicSize.Max).heightIn(min = 48.dp)
                        .combinedClickable(role = Role.Tab, onClick = { onSelect(index) },
                            onLongClick = { onEdit(group) }, onLongClickLabel = stringResource(R.string.group_edit))
                        .semantics { this.selected = selected }
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(group.groupName, style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(2.dp)
                        .graphicsLayer { scaleX = indicator; alpha = indicator }, color = ink) {}
                }
            }
        }
    }
}
