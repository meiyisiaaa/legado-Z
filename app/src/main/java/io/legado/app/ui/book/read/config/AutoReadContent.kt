package io.legado.app.ui.book.read.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

@Composable
internal fun AutoReadContent(
    speed: Int,
    onSpeedChange: (Int) -> Unit,
    onSpeedChangeFinished: () -> Unit,
    onCatalog: () -> Unit,
    onMainMenu: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val speedLabel = stringResource(R.string.auto_page_speed)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.auto_page_speed),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${speed.coerceAtLeast(1)}s",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = speed.coerceIn(1, 120).toFloat(),
            onValueChange = { onSpeedChange(it.toInt().coerceAtLeast(1)) },
            onValueChangeFinished = onSpeedChangeFinished,
            valueRange = 1f..120f,
            modifier = Modifier.semantics { contentDescription = "$speedLabel: $speed" }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoReadAction(R.drawable.ic_toc, R.string.chapter_list, onCatalog)
            AutoReadAction(R.drawable.ic_menu, R.string.main_menu, onMainMenu)
            AutoReadAction(R.drawable.ic_auto_page_stop, R.string.stop, onStop)
            AutoReadAction(R.drawable.ic_settings, R.string.setting, onSettings)
        }
    }
}

@Composable
private fun AutoReadAction(icon: Int, label: Int, onClick: () -> Unit) {
    val labelText = stringResource(label)
    Surface(
        modifier = Modifier
            .size(width = 64.dp, height = 64.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = labelText },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AutoReadPreview() {
    LegadoTheme {
        AutoReadContent(8, {}, {}, {}, {}, {}, {})
    }
}
