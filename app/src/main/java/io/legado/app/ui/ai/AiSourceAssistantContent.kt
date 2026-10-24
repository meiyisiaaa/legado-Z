package io.legado.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import io.legado.app.R
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.ai.batch.BatchProgress
import io.legado.app.ai.batch.BatchTask
import io.legado.app.ai.batch.BatchTaskState
import io.legado.app.ai.prompt.IntentPromptTemplates
import io.legado.app.ai.prompt.SourceIntent
import io.legado.app.utils.toastOnUi

internal data class AiSourceAssistantDraft(
    val intent: String,
    val pageUrl: String,
    val html: String,
    val sourceJson: String,
    val symptom: String,
    val trialKey: String,
    val sampleHtml: String,
    val includeLogs: Boolean,
    val includeHtml: Boolean,
    val batchUrls: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSourceAssistantContent(
    outcome: AiSourceOutcome?,
    loading: Boolean,
    error: String?,
    message: String?,
    batchRunning: Boolean,
    batchProgress: BatchProgress,
    debugTrialStatus: String?,
    onRun: (Int, AiSourceAssistantDraft) -> Unit,
    onDebugTrial: (String, String) -> Unit,
    onPreviewEvidence: (Boolean, Boolean, String) -> Unit,
    onCancelBatch: () -> Unit,
    onImportAll: () -> Unit,
    onShowBatchTask: (BatchTask) -> Unit,
    onImport: (AiSourceOutcome) -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (String) -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(AiSourceAssistantDialog.MODE_GENERATE) }
    var intent by rememberSaveable { mutableStateOf("") }
    var pageUrl by rememberSaveable { mutableStateOf("") }
    var html by rememberSaveable { mutableStateOf("") }
    var sourceJson by rememberSaveable { mutableStateOf("") }
    var symptom by rememberSaveable { mutableStateOf("") }
    val defaultTrialKey = stringResource(R.string.ai_debug_trial_key_default)
    var trialKey by rememberSaveable { mutableStateOf(defaultTrialKey) }
    var sampleHtml by rememberSaveable { mutableStateOf("") }
    var includeLogs by rememberSaveable { mutableStateOf(true) }
    var includeHtml by rememberSaveable { mutableStateOf(true) }
    var batchUrls by rememberSaveable { mutableStateOf("") }
    val selectedIntents = remember { mutableStateListOf<SourceIntent>() }
    val context = LocalContext.current

    LaunchedEffect(error) { error?.takeIf(String::isNotBlank)?.let { context.toastOnUi(it) } }
    LaunchedEffect(message) {
        message?.takeIf(String::isNotBlank)?.let {
            context.toastOnUi(it)
            onMessageConsumed()
        }
    }
    val currentDraft = AiSourceAssistantDraft(
        intent = IntentPromptTemplates.compose(selectedIntents, intent),
        pageUrl = pageUrl,
        html = html,
        sourceJson = sourceJson,
        symptom = symptom,
        trialKey = trialKey,
        sampleHtml = sampleHtml,
        includeLogs = includeLogs,
        includeHtml = includeHtml,
        batchUrls = batchUrls
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_source_assistant)) },
                actions = {
                    val colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
                    TextButton(onClick = onSettings, colors = colors) { Text(stringResource(R.string.ai_settings)) }
                    TextButton(onClick = onHelp, colors = colors) { Text(stringResource(R.string.help)) }
                }
            )
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    AiSourceAssistantDialog.MODE_GENERATE to R.string.ai_tab_generate,
                    AiSourceAssistantDialog.MODE_DEBUG to R.string.ai_tab_debug,
                    AiSourceAssistantDialog.MODE_BATCH to R.string.ai_tab_batch
                ).forEach { (tab, label) ->
                    FilterChip(
                        selected = mode == tab,
                        onClick = { mode = tab },
                        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (mode) {
                AiSourceAssistantDialog.MODE_GENERATE -> {
                    Text(stringResource(R.string.ai_intent_section), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        SourceIntent.ALL.forEach { option ->
                            FilterChip(
                                selected = option in selectedIntents,
                                onClick = {
                                    if (!selectedIntents.remove(option)) selectedIntents.add(option)
                                },
                                label = { Text(stringResource(option.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    AssistantField(stringResource(R.string.ai_hint_intent), intent, { intent = it }, minLines = 2)
                    AssistantField(stringResource(R.string.ai_hint_page_url), pageUrl, { pageUrl = it }, KeyboardType.Uri)
                    AssistantField(stringResource(R.string.ai_hint_html), html, { html = it }, minLines = 5)
                }
                AiSourceAssistantDialog.MODE_DEBUG -> {
                    AssistantField(stringResource(R.string.ai_debug_source_input_hint), sourceJson, { sourceJson = it }, minLines = 6)
                    AssistantField(stringResource(R.string.ai_hint_symptom), symptom, { symptom = it }, minLines = 3)
                    AssistantField(stringResource(R.string.ai_debug_trial_key_hint), trialKey, { trialKey = it }, KeyboardType.Uri)
                    Button(onClick = { onDebugTrial(sourceJson, trialKey) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_debug_trial_action))
                    }
                    debugTrialStatus?.takeIf(String::isNotBlank)?.let {
                        Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AssistantCheck(stringResource(R.string.ai_debug_include_logs), includeLogs) { includeLogs = it }
                    AssistantCheck(stringResource(R.string.ai_debug_include_html), includeHtml) { includeHtml = it }
                    AssistantField(stringResource(R.string.ai_hint_sample_html), sampleHtml, { sampleHtml = it }, minLines = 5)
                    TextButton(onClick = { onPreviewEvidence(includeLogs, includeHtml, sampleHtml) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_debug_preview_evidence))
                    }
                }
                else -> {
                    Text(stringResource(R.string.ai_batch_serial_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AssistantField(
                        stringResource(R.string.ai_hint_batch_urls, io.legado.app.ai.batch.BatchInputParser.DEFAULT_MAX_TASKS),
                        batchUrls,
                        { batchUrls = it },
                        minLines = 6
                    )
                    AssistantField(stringResource(R.string.ai_hint_intent), intent, { intent = it }, minLines = 2)
                    if (batchProgress.total > 0) {
                        Text(
                            stringResource(R.string.ai_batch_progress, batchProgress.completedCount, batchProgress.total, batchProgress.successCount, batchProgress.failedCount),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.labelLarge
                        )
                        batchProgress.tasks.forEach { task ->
                            BatchTaskCard(task, onClick = { if (task.state == BatchTaskState.SUCCESS) onShowBatchTask(task) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onCancelBatch, enabled = batchRunning, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.ai_action_batch_cancel))
                            }
                            TextButton(onClick = onImportAll, enabled = batchProgress.importableJsons().isNotEmpty(), modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.ai_action_batch_import_all))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onRun(mode, currentDraft) },
                enabled = !loading && !batchRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading || batchRunning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.loading))
                    }
                } else Text(
                    stringResource(
                        when (mode) {
                            AiSourceAssistantDialog.MODE_DEBUG -> R.string.ai_action_diagnose
                            AiSourceAssistantDialog.MODE_BATCH -> R.string.ai_action_batch_run
                            else -> R.string.ai_action_generate
                        }
                    )
                )
            }

            outcome?.let { AssistantOutcome(it, onImport, onCopy, onEdit) { onRun(mode, currentDraft) } }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AssistantField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        maxLines = if (minLines > 1) minLines + 8 else 1
    )
}

@Composable
private fun AssistantCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        )
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

@Composable
private fun BatchTaskCard(task: BatchTask, onClick: () -> Unit) {
    val stateText = when (task.state) {
        BatchTaskState.PENDING -> stringResource(R.string.ai_batch_state_pending)
        BatchTaskState.RUNNING -> stringResource(R.string.ai_batch_state_running)
        BatchTaskState.SUCCESS -> stringResource(R.string.ai_batch_state_success)
        BatchTaskState.FAILED -> stringResource(R.string.ai_batch_state_failed)
        BatchTaskState.CANCELLED -> stringResource(R.string.ai_batch_state_cancelled)
    }
    val errorLabel = if (task.errorMessageRes != 0) stringResource(task.errorMessageRes) else ""
    Card(
        onClick = onClick,
        enabled = task.state == BatchTaskState.SUCCESS,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${task.index + 1}. ${task.url}", style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append(stateText)
                    if (errorLabel.isNotEmpty()) append("：$errorLabel")
                    if (task.errorDetail.isNotBlank()) append("（${task.errorDetail}）")
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (task.state) {
                    BatchTaskState.FAILED -> MaterialTheme.colorScheme.error
                    BatchTaskState.SUCCESS -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun AssistantOutcome(
    outcome: AiSourceOutcome,
    onImport: (AiSourceOutcome) -> Unit,
    onCopy: (String) -> Unit,
    onEdit: (String) -> Unit,
    onRetry: () -> Unit
) {
    val tokenEstimate = stringResource(R.string.ai_token_estimate, outcome.promptTokenEstimate)
    val usageLabel = outcome.usage?.let { stringResource(R.string.ai_usage, it.totalTokens) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (outcome.advice.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(outcome.advice, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (outcome.validation.issues.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ai_debug_trial_section), style = MaterialTheme.typography.titleSmall)
                    outcome.validation.issues.forEach { issue ->
                        Text(
                            "${issue.field}：${stringResource(issue.messageRes)}${issue.detail.takeIf(String::isNotBlank)?.let { "（$it）" }.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (issue.level == io.legado.app.ai.IssueLevel.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        outcome.preview.groups.forEach { group ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(group.titleRes), style = MaterialTheme.typography.titleSmall)
                    group.entries.forEach { entry ->
                        Text("${entry.key} = ${entry.value}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        if (outcome.promptTokenEstimate > 0 || outcome.usage != null) {
            Text(
                if (usageLabel.isNullOrBlank()) tokenEstimate else "$tokenEstimate · $usageLabel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (outcome.rawReply.isNotBlank()) {
            Text(stringResource(R.string.ai_result_raw_reply), style = MaterialTheme.typography.titleSmall)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                SelectionContainer {
                    Text(
                        outcome.rawReply,
                        Modifier.fillMaxWidth().padding(12.dp),
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        if (outcome.sourceJson.isNotBlank()) {
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { onImport(outcome) }, enabled = outcome.canImport(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_action_import))
                }
                TextButton(onClick = { onCopy(outcome.sourceJson) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_action_copy_json))
                }
                TextButton(onClick = { onEdit(outcome.sourceJson) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_action_edit_json))
                }
                TextButton(onClick = onRetry, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_action_retry))
                }
            }
        }
    }
}
