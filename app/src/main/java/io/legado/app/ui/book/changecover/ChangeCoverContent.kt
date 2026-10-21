package io.legado.app.ui.book.changecover

import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.widget.image.CoverImageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeCoverContent(
    covers: List<SearchBook>,
    searchState: Int,
    onBack: () -> Unit,
    onSearchAction: () -> Unit,
    onChoose: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.change_cover_source), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current)) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    val (label, iconState) = when (searchState) {
                        1 -> stringResource(R.string.stop) to true
                        2 -> stringResource(R.string.resume) to false
                        else -> stringResource(R.string.refresh) to false
                    }
                    TextButton(onClick = onSearchAction, colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current)) {
                        if (iconState) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current,
                                strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(label)
                    }
                }
            )
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            if (searchState == 1) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            } else {
                Spacer(Modifier.fillMaxWidth().height(2.dp))
            }
            if (covers.isEmpty() && searchState != 1) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到可用封面", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(covers, key = { it.bookUrl.ifBlank { it.originName + it.coverUrl } }) { book ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(book.coverUrl.orEmpty()) }
                                .focusable()
                                .padding(12.dp)
                        ) {
                            key(book.bookUrl, book.coverUrl, book.originName) {
                                AndroidView(
                                    factory = { viewContext ->
                                        CoverImageView(viewContext).apply {
                                            contentDescription = viewContext.getString(R.string.img_cover)
                                            transitionName = "img_cover"
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                            load(book, false)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                                    update = {
                                        it.scaleType = ImageView.ScaleType.CENTER_CROP
                                        it.load(book, false)
                                    }
                                )
                            }
                            Text(
                                text = book.originName,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
