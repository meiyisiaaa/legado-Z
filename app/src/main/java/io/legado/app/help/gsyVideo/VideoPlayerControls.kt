package io.legado.app.help.gsyVideo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion

@Composable
internal fun VideoPlayerActions(
    isFullscreen: Boolean,
    showEpisodes: Boolean,
    showDanmaku: Boolean,
    danmakuLabel: String,
    speedLabel: String,
    currentPositionMs: Long,
    durationMs: Long,
    progress: Float,
    isPlaying: Boolean,
    onProgressChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onEpisodeList: () -> Unit,
    onSpeed: () -> Unit,
    onDanmaku: () -> Unit
) {
    if (isFullscreen) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(formatVideoTime(currentPositionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Text(" / ", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                Text(formatVideoTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Slider(
                value = progress.coerceIn(0f, 100f),
                onValueChange = onProgressChange,
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..100f,
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPause, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp),
                        contentDescription = stringResource(if (isPlaying) R.string.audio_pause else R.string.audio_play),
                        tint = Color.White
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = stringResource(R.string.next_chapter),
                        tint = Color.White
                    )
                }
                Spacer(Modifier.weight(1f))
                if (showDanmaku) ControlAction(danmakuLabel, onDanmaku)
                if (showEpisodes) ControlAction(stringResource(R.string.chapter_list), onEpisodeList)
                ControlAction(speedLabel, onSpeed)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatVideoTime(currentPositionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            Slider(
                value = progress.coerceIn(0f, 100f),
                onValueChange = onProgressChange,
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..100f,
                enabled = durationMs > 0,
                modifier = Modifier.weight(1f).height(32.dp)
            )
            Text(
                text = formatVideoTime(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            if (showDanmaku) ControlAction(danmakuLabel, onDanmaku)
        }
    }
}

private fun formatVideoTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun ControlAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
    }
}

@Composable
internal fun VideoPlayerOverlay(
    errorVisible: Boolean,
    tip: String?,
    onRetry: () -> Unit
) {
    val motion = LocalLegadoMotion.current
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = errorVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(motion.state)) + expandVertically(tween(motion.state)),
            exit = fadeOut(tween(motion.state)) + shrinkVertically(tween(motion.state))
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.78f),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.error), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onRetry, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Text(stringResource(R.string.retry), color = Color.White)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = tip != null && !errorVisible,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp),
            enter = fadeIn(tween(motion.state)) + expandVertically(tween(motion.state)),
            exit = fadeOut(tween(motion.state)) + shrinkVertically(tween(motion.state))
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.72f),
                contentColor = Color.White
            ) {
                Text(
                    text = tip.orEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoPlayerControlsPreview() {
    LegadoTheme {
        Column(Modifier.fillMaxWidth().background(Color.Black)) {
            VideoPlayerActions(
                isFullscreen = true,
                showEpisodes = true,
                showDanmaku = true,
                danmakuLabel = "关弹幕",
                speedLabel = "1.5X",
                currentPositionMs = 36_000L,
                durationMs = 240_000L,
                progress = 15f,
                isPlaying = true,
                onProgressChange = {},
                onSeekFinished = {},
                onPlayPause = {},
                onNext = {},
                onEpisodeList = {},
                onSpeed = {},
                onDanmaku = {}
            )
        }
    }
}
