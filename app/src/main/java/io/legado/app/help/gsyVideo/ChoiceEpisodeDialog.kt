package io.legado.app.help.gsyVideo

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.widget.compose.LegadoTheme

class ChoiceEpisodeDialog(private val mContext: Context) : Dialog(
    mContext,
    io.legado.app.R.style.dialog_style
) {
    private var onItemClickListener: OnListItemClickListener? = null

    interface OnListItemClickListener {
        fun onItemClick(position: Int)
        fun finishDialog()
    }

    override fun onStop() {
        onItemClickListener?.finishDialog()
        super.onStop()
    }

    fun initList(
        data: List<BookChapter>,
        onItemClickListener: OnListItemClickListener,
        initialSelection: Int = -1
    ) {
        this.onItemClickListener = onItemClickListener
        val host = ComposeView(mContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                LegadoTheme {
                    EpisodeListContent(
                        episodes = data,
                        initialSelection = initialSelection,
                        onSelect = { position ->
                            dismiss()
                            onItemClickListener.onItemClick(position)
                        }
                    )
                }
            }
        }
        setContentView(host)
        window?.let { dialogWindow ->
            val metrics = mContext.resources.displayMetrics
            val params = dialogWindow.attributes
            params.width = (metrics.widthPixels * 0.4).toInt()
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.END
            dialogWindow.attributes = params
        }
    }
}

@Composable
private fun EpisodeListContent(
    episodes: List<BookChapter>,
    initialSelection: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(initialSelection, episodes.size) {
        if (initialSelection in episodes.indices) listState.scrollToItem(initialSelection)
    }
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0x80121212)).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "选集（${episodes.size}）",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelLarge
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(episodes, key = { index, chapter -> "${chapter.url}-$index" }) { index, chapter ->
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    color = if (index == initialSelection) MaterialTheme.colorScheme.primary.copy(alpha = 0.26f) else Color.Transparent,
                    contentColor = Color.White
                ) {
                    Text(
                        text = chapter.title,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        fontWeight = if (index == initialSelection) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
