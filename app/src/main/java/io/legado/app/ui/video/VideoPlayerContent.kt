package io.legado.app.ui.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.image.CoverImageView

@Composable
internal fun VideoPlayerContent(
    titleBar: TitleBar?,
    playerContainer: FrameLayout?,
    playerHeight: Dp,
    bookName: String?,
    bookAuthor: String?,
    coverView: CoverImageView?,
    introScrollView: NestedScrollView?,
    volumes: List<BookChapter>,
    episodes: List<BookChapter>,
    selectedVolume: Int,
    selectedEpisode: Int,
    showBookSections: Boolean,
    onVolumeSelected: (Int) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onChapterListClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (titleBar != null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { titleBar }
            )
        }
        if (playerContainer != null) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(playerHeight),
                factory = { playerContainer }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(playerHeight).background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.video_play), color = androidx.compose.ui.graphics.Color.White)
            }
        }

        if (showBookSections && bookName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (coverView != null) {
                    AndroidView(
                        modifier = Modifier.width(110.dp).height(160.dp),
                        factory = { coverView }
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.image_cover_default),
                        contentDescription = stringResource(R.string.img_cover),
                        modifier = Modifier.width(110.dp).height(160.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bookName,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (!bookAuthor.isNullOrBlank()) {
                        Text(
                            text = bookAuthor,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (introScrollView != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = { introScrollView }
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.book_intro),
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(start = 8.dp, top = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                if (volumes.isNotEmpty()) {
                    ChapterStrip(
                        chapters = volumes,
                        selectedIndex = selectedVolume,
                        isVolume = true,
                        onClick = onVolumeSelected
                    )
                }
                if (episodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChapterStrip(
                            modifier = Modifier.weight(1f),
                            chapters = episodes,
                            selectedIndex = selectedEpisode,
                            isVolume = false,
                            onClick = onEpisodeSelected
                        )
                        IconButton(onClick = onChapterListClick, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_chapter_list),
                                contentDescription = stringResource(R.string.chapter_list),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChapterStrip(
    modifier: Modifier = Modifier,
    chapters: List<BookChapter>,
    selectedIndex: Int,
    isVolume: Boolean,
    onClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, chapters.size) {
        if (selectedIndex in chapters.indices) listState.scrollToItem(selectedIndex)
    }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        itemsIndexed(chapters, key = { index, chapter -> "${chapter.url}-$index" }) { index, chapter ->
            val selected = index == selectedIndex
            Surface(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .width(if (isVolume) 80.dp else 100.dp)
                    .heightIn(min = 48.dp),
                onClick = { onClick(index) },
                shape = RoundedCornerShape(4.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoPlayerContentPreview() {
    LegadoTheme {
        VideoPlayerContent(
            titleBar = null,
            playerContainer = null,
            playerHeight = 180.dp,
            bookName = "示例视频标题",
            bookAuthor = "作者",
            coverView = null,
            introScrollView = null,
            volumes = emptyList(),
            episodes = emptyList(),
            selectedVolume = -1,
            selectedEpisode = -1,
            showBookSections = true,
            onVolumeSelected = {},
            onEpisodeSelected = {},
            onChapterListClick = {}
        )
    }
}
