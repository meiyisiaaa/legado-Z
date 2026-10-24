package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion

@Suppress("unused")
class WaitDialog(context: Context) : Dialog(context) {

    private var message by mutableStateOf(context.getString(R.string.loading))

    init {
        setCanceledOnTouchOutside(false)
        setContentView(
            ComposeDialogView(context).apply {
                setContent {
                    LegadoTheme {
                        WaitDialogContent(message, isEInk = AppConfig.isEInkMode)
                    }
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    }

    override fun onStart() {
        super.onStart()
        window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun setText(text: String): WaitDialog {
        message = text
        return this
    }

    fun setText(res: Int): WaitDialog {
        message = context.getString(res)
        return this
    }
}

@Composable
internal fun WaitDialogContent(message: String, isEInk: Boolean = AppConfig.isEInkMode) {
    val motionEnabled = LocalLegadoMotion.current.enabled && !isEInk
    val indicatorColor = MaterialTheme.colorScheme.primary
    val loadingDescription = stringResource(R.string.loading)
    Surface(
        modifier = Modifier.widthIn(min = 180.dp, max = 360.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isEInk) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (motionEnabled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Canvas(Modifier.size(28.dp).semantics {
                    contentDescription = loadingDescription
                }) {
                    val stroke = 3.dp.toPx()
                    drawArc(
                        color = indicatorColor,
                        startAngle = -90f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WaitDialogPreview() {
    LegadoTheme { WaitDialogContent("正在加载…", isEInk = false) }
}
