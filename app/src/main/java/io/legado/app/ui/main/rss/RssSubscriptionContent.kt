package io.legado.app.ui.main.rss

import android.widget.ImageView
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.request.RequestOptions
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.ui.widget.compose.LocalLegadoMotion

@Composable
internal fun RssSubscriptionContent(
    fragment: Fragment,
    lifecycle: Lifecycle,
    sources: List<RssSource>,
    onRuleSubscription: () -> Unit,
    onOpen: (RssSource) -> Unit,
    onEdit: (RssSource) -> Unit,
    onTop: (RssSource) -> Unit,
    onLogin: (RssSource) -> Unit,
    onDelete: (RssSource) -> Unit,
    onDisable: (RssSource) -> Unit
) {
    RssSubscriptionGrid(sources, onRuleSubscription, onOpen, onEdit, onTop, onLogin,
        onDelete, onDisable) { source ->
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ImageView(it).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } },
            update = { image ->
                val options = RequestOptions().set(OkHttpModelLoader.sourceOriginOption, source.sourceUrl)
                ImageLoader.load(fragment, lifecycle, source.sourceIcon)
                    .apply(options).centerCrop().placeholder(R.drawable.image_rss)
                    .error(R.drawable.image_rss).into(image)
            }
        )
    }
}

@Composable
internal fun RssSubscriptionGrid(
    sources: List<RssSource>,
    onRuleSubscription: () -> Unit,
    onOpen: (RssSource) -> Unit,
    onEdit: (RssSource) -> Unit,
    onTop: (RssSource) -> Unit,
    onLogin: (RssSource) -> Unit,
    onDelete: (RssSource) -> Unit,
    onDisable: (RssSource) -> Unit,
    modifier: Modifier = Modifier,
    sourceIcon: @Composable (RssSource) -> Unit
) {
    var menuSourceUrl by remember { mutableStateOf<String?>(null) }
    val motion = LocalLegadoMotion.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "rule-subscription") {
            Column {
                Row(
                    Modifier.fillMaxWidth().rssEntryClick(onRuleSubscription)
                        .heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.image_legado), null, Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.rule_subscription), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Icon(painterResource(R.drawable.ic_arrow_right), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (sources.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(stringResource(R.string.rss_source_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        } else {
            items(sources, key = { it.sourceUrl }) { source ->
                Box(Modifier.animateItem(fadeInSpec = tween(motion.state),
                    fadeOutSpec = tween(motion.state), placementSpec = tween(motion.state))) {
                    RssSourceGridItem(
                        name = source.sourceName,
                        onClick = { onOpen(source) },
                        onLongClick = { menuSourceUrl = source.sourceUrl },
                        imageContent = {
                            sourceIcon(source)
                        }
                    )
                    DropdownMenu(
                        expanded = menuSourceUrl == source.sourceUrl,
                        onDismissRequest = { menuSourceUrl = null }
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menuSourceUrl = null; onEdit(source) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.to_top)) }, onClick = { menuSourceUrl = null; onTop(source) })
                        if (!source.loginUrl.isNullOrBlank()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.login)) }, onClick = { menuSourceUrl = null; onLogin(source) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.disable_source)) }, onClick = { menuSourceUrl = null; onDisable(source) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuSourceUrl = null; onDelete(source) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RssSourceGridItem(
    name: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    imageContent: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .rssEntryClick(onClick, onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(50.dp).clip(MaterialTheme.shapes.medium)) {
            imageContent()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            name,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.rssEntryClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val motion = LocalLegadoMotion.current
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && motion.enabled) 0.985f else 1f,
        motion.pressSpec(), label = "rss press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(MaterialTheme.shapes.extraSmall)
        .combinedClickable(interactionSource = interactions,
            indication = if (motion.enabled) LocalIndication.current else null,
            role = Role.Button, onClick = onClick, onLongClick = onLongClick,
            onLongClickLabel = if (onLongClick != null) stringResource(R.string.more_menu) else null)
}
