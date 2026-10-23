package io.legado.app.ui.rss.article

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.legado.app.R
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssStar
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.ui.widget.compose.legadoPressFeedback
import com.bumptech.glide.request.RequestOptions

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
internal fun RssArticleListContent(
    articles: List<RssArticle>,
    articleStyle: Int,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onLoadMore: (Boolean) -> Unit,
    onRead: (RssArticle) -> Unit,
    preload: Boolean,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when {
            articles.isEmpty() && isRefreshing -> LoadingArticles()
            articles.isEmpty() && error != null -> ArticleLoadError(error, onRefresh)
            articles.isEmpty() -> EmptyArticles(onRefresh)
            articleStyle == 2 || articleStyle == 4 -> {
                val columns = if (articleStyle == 4) 3 else 2
                val state = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LaunchedEffect(state, articles.size, hasMore, isLoadingMore, preload) {
                    if (preload) androidx.compose.runtime.snapshotFlow {
                        val layout = state.layoutInfo
                        layout.visibleItemsInfo.maxOfOrNull { it.index }?.let { it >= layout.totalItemsCount - 5 } == true
                    }.collect { nearBottom -> if (nearBottom && hasMore && !isLoadingMore) onLoadMore(false) }
                    else androidx.compose.runtime.snapshotFlow {
                        val layout = state.layoutInfo
                        layout.visibleItemsInfo.maxOfOrNull { it.index } == layout.totalItemsCount - 1
                    }.collect { atBottom -> if (atBottom && hasMore && !isLoadingMore) onLoadMore(false) }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(articles, key = { articleKey(it) }) { article ->
                        RssArticleCard(article, articleStyle, onClick = { onRead(article) })
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        ArticleListFooter(isLoadingMore, hasMore, error, onLoadMore)
                    }
                }
            }
            articleStyle == 3 -> {
                val state = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
                LaunchedEffect(state, articles.size, hasMore, isLoadingMore, preload) {
                    androidx.compose.runtime.snapshotFlow {
                        val layout = state.layoutInfo
                        val lastIndex = layout.visibleItemsInfo.maxOfOrNull { it.index }
                        lastIndex == layout.totalItemsCount - 1 || (preload && lastIndex?.let { it >= layout.totalItemsCount - 5 } == true)
                    }.collect { nearBottom -> if (nearBottom && hasMore && !isLoadingMore) onLoadMore(false) }
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(168.dp),
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    items(articles, key = { articleKey(it) }) { article ->
                        RssArticleCard(article, articleStyle, onClick = { onRead(article) })
                    }
                    item { ArticleListFooter(isLoadingMore, hasMore, error, onLoadMore) }
                }
            }
            else -> {
                val state = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(state, articles.size, hasMore, isLoadingMore, preload) {
                    androidx.compose.runtime.snapshotFlow {
                        val layout = state.layoutInfo
                        layout.visibleItemsInfo.lastOrNull()?.index == layout.totalItemsCount - 1
                    }.collect { nearBottom ->
                        if (nearBottom && hasMore && !isLoadingMore) onLoadMore(false)
                    }
                }
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(articles, key = { articleKey(it) }) { article ->
                        RssArticleCard(article, articleStyle, onClick = { onRead(article) })
                    }
                    item { ArticleListFooter(isLoadingMore, hasMore, error, onLoadMore) }
                }
            }
        }
    }
}

@Composable
internal fun RssFavoriteListContent(
    favorites: List<RssStar>,
    onRead: (RssStar) -> Unit,
    onDelete: (RssStar) -> Unit,
    modifier: Modifier = Modifier
) {
    if (favorites.isEmpty()) {
        EmptyArticles(
            onRefresh = null,
            modifier = modifier,
            titleRes = R.string.rss_favorites_empty_title,
            descriptionRes = R.string.rss_favorites_empty_description,
            announceEmpty = false
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(favorites, key = { "${it.origin}\u0000${it.link}" }) { favorite ->
                RssArticleCard(
                    article = favorite.toRssArticle(),
                    articleStyle = 0,
                    onClick = { onRead(favorite) },
                    onLongClick = { onDelete(favorite) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RssArticleCard(
    article: RssArticle,
    articleStyle: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val grid = articleStyle == 2 || articleStyle == 3 || articleStyle == 4
    val titleColor = if (article.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val titleWeight = if (article.read) FontWeight.Normal else FontWeight.SemiBold
    val interactions = remember { MutableInteractionSource() }
    val clickModifier = Modifier
        .legadoPressFeedback(interactions)
        .clip(if (articleStyle == 3) MaterialTheme.shapes.medium else MaterialTheme.shapes.small)
        .then(if (onLongClick == null) Modifier.clickable(
            interactionSource = interactions, indication = androidx.compose.foundation.LocalIndication.current,
            role = Role.Button, onClick = onClick
        ) else Modifier.combinedClickable(
            interactionSource = interactions, indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick,
            onLongClick = onLongClick,
            onLongClickLabel = stringResource(R.string.delete),
            role = Role.Button
        ))

    if (articleStyle == 1) {
        Column(clickModifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp)) {
            if (!article.image.isNullOrBlank()) {
                RssArticleImage(
                    article.image,
                    article.origin,
                    Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium)
                )
            }
            Column(Modifier.padding(top = if (article.image.isNullOrBlank()) 0.dp else 12.dp, bottom = 24.dp)) {
                Text(article.title, color = titleColor, style = MaterialTheme.typography.titleMedium, fontWeight = titleWeight,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                ArticleDate(article.pubDate)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    } else if (grid) {
        val imageModifier = when (articleStyle) {
            2, 4 -> Modifier.fillMaxWidth().aspectRatio(0.75f).clip(MaterialTheme.shapes.small)
            else -> Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 340.dp)
        }
        val articleGridBody: @Composable () -> Unit = {
            Column {
                if (!article.image.isNullOrBlank()) {
                    RssArticleImage(article.image, article.origin, imageModifier,
                        naturalHeight = articleStyle == 3)
                } else if (articleStyle == 2) {
                    Box(imageModifier, contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.image_rss_article),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (articleStyle == 3) {
                    Column(Modifier.padding(12.dp)) {
                        Text(article.title, color = titleColor, style = MaterialTheme.typography.bodyMedium, fontWeight = titleWeight,
                            maxLines = 9, overflow = TextOverflow.Ellipsis)
                        ArticleDate(article.pubDate, maxLines = 39)
                    }
                } else {
                    Column(Modifier.padding(top = if (articleStyle == 2) 12.dp else 8.dp)) {
                        Text(article.title, color = titleColor,
                            style = if (articleStyle == 2) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                            fontWeight = titleWeight,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        ArticleDate(article.pubDate)
                    }
                }
            }
        }
        if (articleStyle == 3) {
            Card(
                modifier = clickModifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                articleGridBody()
            }
        } else {
            Column(
                modifier = clickModifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                articleGridBody()
            }
        }
    } else {
        val imageWidth = if (LocalDensity.current.fontScale >= 1.5f) 64.dp else 96.dp
        Column(clickModifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(vertical = 16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(article.title, color = titleColor, style = MaterialTheme.typography.titleMedium,
                        fontWeight = titleWeight, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ArticleDate(article.pubDate)
                }
                if (!article.image.isNullOrBlank()) {
                    RssArticleImage(article.image, article.origin,
                        Modifier.size(width = imageWidth, height = 76.dp).clip(MaterialTheme.shapes.small))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ArticleDate(date: String?, maxLines: Int = 1) {
    if (!date.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RssArticleImage(
    url: String?,
    origin: String,
    modifier: Modifier,
    naturalHeight: Boolean = false
) {
    val context = LocalContext.current
    if (url.isNullOrBlank()) return
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                contentDescription = null
                clipToOutline = true
            }
        },
        update = { image ->
            image.adjustViewBounds = naturalHeight
            image.scaleType = if (naturalHeight) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            loadArticleImage(context, image, url, origin, naturalHeight)
        }
    )
}

private fun loadArticleImage(context: Context, image: ImageView, url: String, origin: String, naturalHeight: Boolean) {
    val loadKey = "$origin\u0000$url\u0000$naturalHeight"
    if (image.getTag(R.id.tag1) == loadKey) return
    image.setTag(R.id.tag1, loadKey)
    ImageLoader.load(context, url)
        .apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, origin).apply {
            if (naturalHeight) fitCenter() else centerCrop()
        })
        .placeholder(R.drawable.image_rss_article)
        .error(R.drawable.image_rss_article)
        .into(image)
}

@Composable
private fun ArticleListFooter(
    loading: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: (Boolean) -> Unit
) {
    val loadingLabel = stringResource(R.string.loading)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.size(22.dp).semantics { contentDescription = loadingLabel },
                strokeWidth = 2.dp
            )
            error != null -> {
                Text(error.lineSequence().take(3).joinToString("\n"),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onLoadMore(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
            hasMore -> TextButton(onClick = { onLoadMore(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.rss_load_more)) }
            else -> Text(
                stringResource(R.string.rss_no_more_articles),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadingArticles() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.loading),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyArticles(
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleRes: Int = R.string.rss_articles_empty_title,
    descriptionRes: Int = R.string.rss_articles_empty_description,
    announceEmpty: Boolean = true
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(
            stringResource(titleRes),
            modifier = if (announceEmpty) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(descriptionRes), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRefresh != null) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.refresh)) }
        }
    }
}

@Composable
private fun ArticleLoadError(error: String, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(
            stringResource(R.string.rss_articles_load_failed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(error.lineSequence().take(5).joinToString("\n"), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, maxLines = 7, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
    }
}

private fun articleKey(article: RssArticle): String = "${article.origin}\u0000${article.sort}\u0000${article.link}"
