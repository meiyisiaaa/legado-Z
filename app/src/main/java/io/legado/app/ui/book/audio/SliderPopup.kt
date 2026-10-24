package io.legado.app.ui.book.audio

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.ComposePopupView
import kotlin.math.roundToInt

class SliderPopup(private val context: Context, private val name: Int) :
    PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {
    companion object {
        const val TIMER = 1
        const val SPEED = 2
    }

    private val isTimer = name == TIMER
    private val minimum = if (isTimer) 0 else 50
    private val maximum = if (isTimer) 180 else 300
    private val steps = if (isTimer) 179 else 24
    private var progress by mutableIntStateOf(readServiceProgress())
    private val composeView = ComposePopupView(context).apply {
        setContent {
            LegadoTheme {
                SliderPopupContent(
                    isTimer = isTimer,
                    progress = progress,
                    minimum = minimum,
                    maximum = maximum,
                    steps = steps,
                    onProgressChange = ::updateProgress
                )
            }
        }
    }

    init {
        contentView = composeView
        isTouchable = true
        isOutsideTouchable = false
        isFocusable = true
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int, gravity: Int) {
        syncProgressFromService()
        super.showAsDropDown(anchor, xoff, yoff, gravity)
    }

    override fun showAtLocation(parent: View?, gravity: Int, x: Int, y: Int) {
        syncProgressFromService()
        super.showAtLocation(parent, gravity, x, y)
    }

    private fun readServiceProgress(): Int = if (isTimer) {
        AudioPlayService.timeMinute.coerceIn(minimum, maximum)
    } else {
        (AudioPlayService.playSpeed * 100).toInt().coerceIn(minimum, maximum)
    }

    private fun syncProgressFromService() {
        progress = readServiceProgress()
    }

    private fun updateProgress(value: Float) {
        progress = if (isTimer) {
            value.roundToInt().coerceIn(minimum, maximum)
        } else {
            ((value / 10f).roundToInt() * 10).coerceIn(minimum, maximum)
        }
        if (isTimer) {
            AudioPlay.setTimer(progress)
        } else {
            AudioPlay.setSpeed(progress / 100f)
        }
    }
}

@Composable
private fun SliderPopupContent(
    isTimer: Boolean,
    progress: Int,
    minimum: Int,
    maximum: Int,
    steps: Int,
    onProgressChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isTimer) {
                    stringResource(R.string.timer_m, progress)
                } else {
                    "%.1fX".format(progress / 100f)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = progress.toFloat(),
                onValueChange = onProgressChange,
                valueRange = minimum.toFloat()..maximum.toFloat(),
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
