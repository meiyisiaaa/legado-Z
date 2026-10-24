package io.legado.app.ui.video.config

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.model.VideoPlay
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialog

class SettingsDialog(private val context: Context, private val callBack: CallBack? = null) :
    BaseDialogFragment(R.layout.dialog_video_settings) {

    private var autoPlay by mutableStateOf(VideoPlay.autoPlay)
    private var startFull by mutableStateOf(VideoPlay.startFull)
    private var fullBottomProgress by mutableStateOf(VideoPlay.fullBottomProgressBar)
    private var showStartFull by mutableStateOf(true)
    private var longPressSpeed by mutableStateOf(VideoPlay.longPressSpeed)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ComposeView>(R.id.composeView).setContent {
            LegadoTheme {
                VideoSettingsContent(
                    autoPlay = autoPlay,
                    startFull = startFull,
                    showStartFull = showStartFull,
                    fullBottomProgress = fullBottomProgress,
                    longPressSpeedText = context.getString(
                        R.string.press_speed_summary,
                        longPressSpeed / 10.0f
                    ),
                    onAutoPlayChange = {
                        autoPlay = it
                        VideoPlay.autoPlay = it
                        showStartFull = it
                    },
                    onStartFullChange = {
                        startFull = it
                        VideoPlay.startFull = it
                    },
                    onFullBottomProgressChange = {
                        fullBottomProgress = it
                        VideoPlay.fullBottomProgressBar = it
                    },
                    onPressSpeedClick = ::showPressSpeedPicker
                )
            }
        }
    }

    private fun showPressSpeedPicker() {
        NumberPickerDialog(requireContext(), true)
            .setTitle(getString(R.string.press_speed))
            .setMaxValue(60)
            .setMinValue(5)
            .setValue(VideoPlay.longPressSpeed)
            .setCustomButton(R.string.btn_default_s) {
                VideoPlay.longPressSpeed = 30
                longPressSpeed = 30
            }
            .show {
                VideoPlay.longPressSpeed = it
                longPressSpeed = it
            }
    }

    interface CallBack {
//        fun upUi()
    }
}

@Composable
private fun VideoSettingsContent(
    autoPlay: Boolean,
    startFull: Boolean,
    showStartFull: Boolean,
    fullBottomProgress: Boolean,
    longPressSpeedText: String,
    onAutoPlayChange: (Boolean) -> Unit,
    onStartFullChange: (Boolean) -> Unit,
    onFullBottomProgressChange: (Boolean) -> Unit,
    onPressSpeedClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.config_settings),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        VideoSettingCheckRow(R.string.auto_play, autoPlay, onAutoPlayChange)
        if (showStartFull) {
            VideoSettingCheckRow(R.string.start_full, startFull, onStartFullChange)
        }
        VideoSettingCheckRow(
            R.string.full_bottom_progress,
            fullBottomProgress,
            onFullBottomProgressChange
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onPressSpeedClick)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.press_speed),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                longPressSpeedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoSettingCheckRow(
    titleRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(titleRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
    }
}
