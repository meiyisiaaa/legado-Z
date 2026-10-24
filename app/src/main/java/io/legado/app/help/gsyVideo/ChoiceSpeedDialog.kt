package io.legado.app.help.gsyVideo

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import io.legado.app.ui.widget.compose.LegadoTheme

class ChoiceSpeedDialog(private val mContext: Context) : Dialog(
    mContext,
    io.legado.app.R.style.dialog_style
) {
    private var onItemClickListener: OnListItemClickListener? = null

    interface OnListItemClickListener {
        fun onItemClick(value: Float)
        fun finishDialog()
    }

    override fun onStop() {
        onItemClickListener?.finishDialog()
        super.onStop()
    }

    fun initList(
        data: List<Float>,
        onItemClickListener: OnListItemClickListener
    ) {
        this.onItemClickListener = onItemClickListener
        val host = ComposeView(mContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                LegadoTheme {
                    SpeedListContent(data) { value ->
                        dismiss()
                        onItemClickListener.onItemClick(value)
                    }
                }
            }
        }
        setContentView(host)
        window?.let { dialogWindow ->
            val metrics = mContext.resources.displayMetrics
            val params = dialogWindow.attributes
            params.width = (metrics.widthPixels * 0.3).toInt()
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.END
            dialogWindow.attributes = params
        }
    }
}

@Composable
private fun SpeedListContent(data: List<Float>, onSelect: (Float) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x80121212)).padding(vertical = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(data) { _, speed ->
                Surface(
                    onClick = { onSelect(speed) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    color = Color.Transparent,
                    contentColor = Color.White
                ) {
                    Text(
                        text = "${speed}X",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
