package io.legado.app.ui.book.read.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ReadAloudContent(
    playing: Boolean,
    timerValue: Int,
    speechRate: Int,
    followSystem: Boolean,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousParagraph: () -> Unit,
    onNextParagraph: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onTimerChange: (Int) -> Unit,
    onTimerChangeFinished: () -> Unit,
    onTimerPresetClick: () -> Unit,
    onSaveTimer: () -> Unit,
    onFollowSystemChange: (Boolean) -> Unit,
    onSpeechRateChange: (Int) -> Unit,
    onSpeechRateChangeFinished: () -> Unit,
    onPreviousSpeed: () -> Unit,
    onNextSpeed: () -> Unit,
    onCatalog: () -> Unit,
    onMainMenu: () -> Unit,
    onBackground: () -> Unit,
    onSettings: () -> Unit,
    onRoleTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roleDescription = stringResource(R.string.role_tts_title)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextAction(R.string.previous_chapter, onPreviousChapter)
            IconAction(R.drawable.ic_skip_previous, R.string.prev_sentence, onPreviousParagraph)
            IconAction(
                if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_24dp,
                if (playing) R.string.pause else R.string.audio_play,
                onPlayPause
            )
            IconAction(R.drawable.ic_stop_black_24dp, R.string.stop, onStop)
            IconAction(R.drawable.ic_skip_next, R.string.next_sentence, onNextParagraph)
            TextAction(R.string.next_chapter, onNextChapter)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconAction(R.drawable.ic_time_add_24dp, R.string.set_timer, onSaveTimer)
            Slider(
                value = timerValue.toFloat(),
                onValueChange = { onTimerChange(it.toInt()) },
                onValueChangeFinished = onTimerChangeFinished,
                valueRange = 0f..180f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            TextAction(R.string.timer_m, onTimerPresetClick, value = stringResource(R.string.timer_m, timerValue.coerceAtLeast(0)))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.read_aloud_speed), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = ((speechRate + 5) / 10f).toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.flow_sys), style = MaterialTheme.typography.labelMedium)
                Switch(checked = followSystem, onCheckedChange = onFollowSystemChange)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconAction(R.drawable.ic_reduce, R.string.tts_speech_reduce, onPreviousSpeed)
            Slider(
                value = speechRate.toFloat().coerceIn(0f, 45f),
                onValueChange = { onSpeechRateChange(it.toInt()) },
                onValueChangeFinished = onSpeechRateChangeFinished,
                valueRange = 0f..45f,
                enabled = !followSystem,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            IconAction(R.drawable.ic_add, R.string.tts_speech_add, onNextSpeed)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onRoleTts)
                .semantics { contentDescription = roleDescription }
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_groups),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Text(
                stringResource(R.string.role_tts_title),
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomAction(R.drawable.ic_toc, R.string.chapter_list, onCatalog)
            BottomAction(R.drawable.ic_menu, R.string.main_menu, onMainMenu)
            BottomAction(R.drawable.ic_visibility_off, R.string.to_backstage, onBackground)
            BottomAction(R.drawable.ic_settings, R.string.setting, onSettings)
        }
    }
}

@Composable
private fun TextAction(label: Int, onClick: () -> Unit, value: String? = null) {
    val text = value ?: stringResource(label)
    Text(
        text = text,
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = text }
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun IconAction(icon: Int, label: Int, onClick: () -> Unit) {
    val description = stringResource(label)
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun BottomAction(icon: Int, label: Int, onClick: () -> Unit) {
    val labelText = stringResource(label)
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = labelText }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(labelText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadAloudContentPreview() {
    LegadoTheme {
        ReadAloudContent(
            playing = false, timerValue = 10, speechRate = 5, followSystem = true,
            onPreviousChapter = {}, onNextChapter = {}, onPreviousParagraph = {}, onNextParagraph = {},
            onPlayPause = {}, onStop = {}, onTimerChange = {}, onTimerChangeFinished = {}, onTimerPresetClick = {},
            onSaveTimer = {}, onFollowSystemChange = {}, onSpeechRateChange = {}, onSpeechRateChangeFinished = {},
            onPreviousSpeed = {}, onNextSpeed = {}, onCatalog = {}, onMainMenu = {}, onBackground = {},
            onSettings = {}, onRoleTts = {}
        )
    }
}
