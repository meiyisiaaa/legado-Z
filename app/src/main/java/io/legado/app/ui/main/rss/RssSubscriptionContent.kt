package io.legado.app.ui.main.rss

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

@OptIn(ExperimentalFoundationApi::class)
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
    var itemMenu by remember { mutableStateOf<RssSource?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "rule-subscription") {
            RssSourceGridItem(
                name = stringResource(R.string.rule_subscription),
                image = R.drawable.image_legado,
                onClick = onRuleSubscription
            )
        }
        if (sources.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Box(
                    Modifier.fillMaxWidth().padding(top = 36.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(stringResource(R.string.rss_source_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(sources, key = { it.sourceUrl }) { source ->
                Box {
                    RssSourceGridItem(
                        name = source.sourceName,
                        onClick = { onOpen(source) },
                        onLongClick = { itemMenu = source },
                        imageContent = {
                            AndroidView(
                                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)),
                                factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                                update = { image ->
                                    val options = RequestOptions().set(OkHttpModelLoader.sourceOriginOption, source.sourceUrl)
                                    ImageLoader.load(fragment, lifecycle, source.sourceIcon)
                                        .apply(options).centerCrop().placeholder(R.drawable.image_rss)
                                        .error(R.drawable.image_rss).into(image)
                                }
                            )
                        }
                    )
                    DropdownMenu(
                        expanded = itemMenu?.sourceUrl == source.sourceUrl,
                        onDismissRequest = { itemMenu = null }
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { itemMenu = null; onEdit(source) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.to_top)) }, onClick = { itemMenu = null; onTop(source) })
                        if (!source.loginUrl.isNullOrBlank()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.login)) }, onClick = { itemMenu = null; onLogin(source) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.disable_source)) }, onClick = { itemMenu = null; onDisable(source) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { itemMenu = null; onDelete(source) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssSourceGridItem(
    name: String,
    image: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    imageContent: (@Composable () -> Unit)? = null
) {
    val longClickLabel = if (onLongClick != null) stringResource(R.string.more_menu) else null
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = longClickLabel
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (imageContent != null) {
            imageContent()
        } else if (image != null) {
            androidx.compose.foundation.Image(
                painter = painterResource(image),
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
