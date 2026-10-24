package io.legado.app.ui.book.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.utils.toDurationTime

internal data class AudioPlayerUiState(
    val subtitle: String = "",
    val currentProgress: Int = 0,
    val duration: Int = 0,
    val buffered: Int = 0,
    val isPlaying: Boolean = false,
    val loading: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val modeIcon: Int = R.drawable.ic_play_mode_list_end_stop,
    val speed: String? = null,
    val timer: String? = null,
    val showSpeed: Boolean = true
)

internal enum class AudioPlayerAction {
    Timer, Speed, Previous, Play, Stop, Next, Mode, Chapters
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AudioPlayerContent(
    state: AudioPlayerUiState,
    onAction: (AudioPlayerAction) -> Unit,
    onSeekChange: (Int) -> Unit,
    onSeekFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.subtitle,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.currentProgress.toDurationTime(), color = Color.White)
            Slider(
                value = state.currentProgress.toFloat().coerceIn(0f, state.duration.coerceAtLeast(1).toFloat()),
                onValueChange = { onSeekChange(it.toInt()) },
                onValueChangeFinished = onSeekFinished,
                valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                enabled = state.duration > 0
            )
            Text(state.duration.toDurationTime(), color = Color.White)
        }
        LinearProgressIndicator(
            progress = {
                if (state.duration <= 0) 0f else (state.buffered.toFloat() / state.duration).coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp).height(2.dp),
            color = Color.White.copy(alpha = 0.55f),
            trackColor = Color.White.copy(alpha = 0.12f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerIcon(R.drawable.ic_timer_black_24dp, stringResource(R.string.set_timer), Color.White) {
                onAction(AudioPlayerAction.Timer)
            }
            if (state.showSpeed) {
                PlayerIcon(R.drawable.ic_speed_control, stringResource(R.string.speed_control), Color.White) {
                    onAction(AudioPlayerAction.Speed)
                }
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.size(42.dp))
            }
            PlayerIcon(R.drawable.ic_skip_previous, stringResource(R.string.skip_previous), Color.White, enabled = state.canPrevious) {
                onAction(AudioPlayerAction.Previous)
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(58.dp).combinedClickable(
                    onClick = { onAction(AudioPlayerAction.Play) },
                    onLongClick = { onAction(AudioPlayerAction.Stop) }
                ),
                contentAlignment = Alignment.Center
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(34.dp), color = Color.White, strokeWidth = 3.dp)
                } else {
                    Icon(
                        painter = painterResource(if (state.isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp),
                        contentDescription = stringResource(if (state.isPlaying) R.string.audio_pause else R.string.audio_play),
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            PlayerIcon(R.drawable.ic_skip_next, stringResource(R.string.skip_next), Color.White, enabled = state.canNext) {
                onAction(AudioPlayerAction.Next)
            }
            PlayerIcon(state.modeIcon, stringResource(R.string.play_mode), Color.White) {
                onAction(AudioPlayerAction.Mode)
            }
            PlayerIcon(R.drawable.ic_chapter_list, stringResource(R.string.chapter_list), Color.White) {
                onAction(AudioPlayerAction.Chapters)
            }
        }
    }
}

@Composable
private fun PlayerIcon(
    imageRes: Int,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(42.dp)) {
            Icon(
                painter = painterResource(imageRes),
                contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.35f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
