package io.legado.app.ui.code

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion

@Composable
internal fun CodeSearchPanel(
    visible: Boolean,
    replaceVisible: Boolean,
    query: String,
    replacement: String,
    isRegex: Boolean,
    resultSummary: String,
    replaceAllEnabled: Boolean,
    onQueryChange: (String) -> Unit,
    onReplacementChange: (String) -> Unit,
    onRegexChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleReplace: () -> Unit,
    onCloseReplace: () -> Unit,
    onReplaceAll: () -> Unit
) {
    val motion = LocalLegadoMotion.current
    val queryFocus = remember { FocusRequester() }
    val replacementFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) queryFocus.requestFocus()
    }
    LaunchedEffect(replaceVisible) {
        if (replaceVisible) {
            replacementFocus.requestFocus()
        } else if (visible) {
            queryFocus.requestFocus()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motion.container)) + expandVertically(tween(motion.container)),
        exit = fadeOut(tween(motion.state)) + shrinkVertically(tween(motion.state))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_result),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = resultSummary,
                        modifier = Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(R.string.regex), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isRegex, onCheckedChange = onRegexChange)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.find),
                        modifier = Modifier.padding(start = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f).focusRequester(queryFocus),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_close),
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                AnimatedVisibility(
                    visible = replaceVisible,
                    enter = fadeIn(tween(motion.state)) + expandVertically(tween(motion.state)),
                    exit = fadeOut(tween(motion.state)) + shrinkVertically(tween(motion.state))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.replace),
                            modifier = Modifier.padding(start = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = replacement,
                            onValueChange = onReplacementChange,
                            modifier = Modifier.weight(1f).focusRequester(replacementFocus),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = onCloseReplace, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_close),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onPrevious,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.btn_previous), maxLines = 1) }
                    TextButton(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.btn_next), maxLines = 1) }
                    TextButton(
                        onClick = onToggleReplace,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.replace), maxLines = 1) }
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = replaceAllEnabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.replace_all), maxLines = 1) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CodeSearchPanelPreview() {
    LegadoTheme {
        CodeSearchPanel(
            visible = true,
            replaceVisible = true,
            query = "videoUrl",
            replacement = "streamUrl",
            isRegex = false,
            resultSummary = "2/8",
            replaceAllEnabled = true,
            onQueryChange = {},
            onReplacementChange = {},
            onRegexChange = {},
            onClose = {},
            onPrevious = {},
            onNext = {},
            onToggleReplace = {},
            onCloseReplace = {},
            onReplaceAll = {}
        )
    }
}
