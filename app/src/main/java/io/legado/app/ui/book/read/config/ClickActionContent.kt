package io.legado.app.ui.book.read.config

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion

internal enum class ClickArea {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, MIDDLE_CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

@Composable
internal fun ClickActionContent(
    actions: List<Pair<Int, String>>,
    selected: Map<ClickArea, Int>,
    onSelect: (ClickArea) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalLegadoMotion.current
    val areaLabels = mapOf(
        ClickArea.TOP_LEFT to "${stringResource(R.string.top)} ${stringResource(R.string.left)}",
        ClickArea.TOP_CENTER to "${stringResource(R.string.top)} ${stringResource(R.string.middle)}",
        ClickArea.TOP_RIGHT to "${stringResource(R.string.top)} ${stringResource(R.string.right)}",
        ClickArea.MIDDLE_LEFT to "${stringResource(R.string.middle)} ${stringResource(R.string.left)}",
        ClickArea.MIDDLE_CENTER to stringResource(R.string.middle),
        ClickArea.MIDDLE_RIGHT to "${stringResource(R.string.middle)} ${stringResource(R.string.right)}",
        ClickArea.BOTTOM_LEFT to "${stringResource(R.string.bottom)} ${stringResource(R.string.left)}",
        ClickArea.BOTTOM_CENTER to "${stringResource(R.string.bottom)} ${stringResource(R.string.middle)}",
        ClickArea.BOTTOM_RIGHT to "${stringResource(R.string.bottom)} ${stringResource(R.string.right)}"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.click_regional_config),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.close),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            val rows = listOf(
                listOf(ClickArea.TOP_LEFT, ClickArea.TOP_CENTER, ClickArea.TOP_RIGHT),
                listOf(ClickArea.MIDDLE_LEFT, ClickArea.MIDDLE_CENTER, ClickArea.MIDDLE_RIGHT),
                listOf(ClickArea.BOTTOM_LEFT, ClickArea.BOTTOM_CENTER, ClickArea.BOTTOM_RIGHT)
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { area ->
                        val areaLabel = areaLabels.getValue(area)
                        val actionLabel = actions.firstOrNull { it.first == selected[area] }?.second.orEmpty()
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(onClick = { onSelect(area) })
                                .semantics { contentDescription = "$areaLabel: $actionLabel" }
                                .animateContentSize(animationSpec = tween(durationMillis = motion.press)),
                            color = Color.White.copy(alpha = 0.13f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(PaddingValues(8.dp)),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = areaLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.72f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = actionLabel,
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ClickActionContentPreview() {
    LegadoTheme {
        ClickActionContent(
            actions = listOf(-1 to "无操作", 0 to "菜单", 1 to "下一页"),
            selected = ClickArea.entries.associateWith { 1 },
            onSelect = {},
            onDismiss = {}
        )
    }
}
