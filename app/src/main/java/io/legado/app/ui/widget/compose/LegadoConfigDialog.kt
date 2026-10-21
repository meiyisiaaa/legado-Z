package io.legado.app.ui.widget.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import kotlin.math.roundToInt

@Composable
internal fun LegadoConfigDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val motion = LocalLegadoMotion.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .animateContentSize(animationSpec = tween(durationMillis = motion.state)),
        color = MaterialTheme.colorScheme.background
    ) {
        Column {
            LegadoDialogHeader(title, onDismiss)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun LegadoDetailSlider(
    title: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedValue = value.coerceIn(0, max)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.widthIn(min = 56.dp, max = 84.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = {
                if (clampedValue > 0) {
                    onValueChange(clampedValue - 1)
                    onValueChangeFinished()
                }
            },
            enabled = clampedValue > 0,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reduce),
                contentDescription = stringResource(R.string.reduce),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = clampedValue.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, max)) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "$title: $clampedValue" }
        )
        IconButton(
            onClick = {
                if (clampedValue < max) {
                    onValueChange(clampedValue + 1)
                    onValueChangeFinished()
                }
            },
            enabled = clampedValue < max,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.plus),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = clampedValue.toString(),
            modifier = Modifier.widthIn(min = 36.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
