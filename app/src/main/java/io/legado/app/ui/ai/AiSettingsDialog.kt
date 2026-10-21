package io.legado.app.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.legado.app.ui.widget.compose.LegadoTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiConfigValidator
import io.legado.app.ai.AiError
import io.legado.app.ai.AiReaderOptions
import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.http.okHttpClient
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi

private data class AiSettingsDraft(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val temperature: String,
    val maxTokens: String,
    val timeout: String,
    val htmlMaxChars: String,
    val autoRepair: Boolean,
    val readerAssistantEnabled: Boolean,
    val explainMaxChars: String
)

/** AI 配置界面只负责收集表单值；校验、持久化和连接测试仍走既有配置契约。 */
class AiSettingsDialog : BaseDialogFragment(R.layout.dialog_ai_settings) {

    private val chatClient by lazy { OpenAiClient(okHttpClient) }
    private val initialDraft by lazy {
        val options = AiConfig.snapshot()
        val reader = AiConfig.readerSnapshot()
        AiSettingsDraft(
            endpoint = options.endpoint,
            apiKey = options.apiKey,
            model = options.model,
            temperature = options.temperature.toString(),
            maxTokens = options.maxTokens.toString(),
            timeout = options.timeoutSec.toString(),
            htmlMaxChars = options.htmlMaxChars.toString(),
            autoRepair = options.autoRepair,
            readerAssistantEnabled = reader.readerAssistantEnabled,
            explainMaxChars = reader.explainMaxChars.toString()
        )
    }
    private var message by mutableStateOf<String?>(null)
    private var testing by mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        id = R.id.legado_ai_compose_root
        setLegadoContent {
            AiSettingsContent(
                initial = initialDraft,
                message = message,
                testing = testing,
                onTest = ::testConnection,
                onSave = ::saveConfig
            )
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    private fun options(draft: AiSettingsDraft) = AiChatOptions(
        endpoint = draft.endpoint,
        apiKey = draft.apiKey,
        model = draft.model,
        temperature = draft.temperature.toFloatOrNull() ?: AiChatOptions.DEFAULT_TEMPERATURE,
        maxTokens = draft.maxTokens.toIntOrNull() ?: AiChatOptions.DEFAULT_MAX_TOKENS,
        timeoutSec = draft.timeout.toIntOrNull() ?: AiChatOptions.DEFAULT_TIMEOUT_SEC,
        htmlMaxChars = draft.htmlMaxChars.toIntOrNull() ?: AiChatOptions.DEFAULT_HTML_MAX_CHARS,
        autoRepair = draft.autoRepair
    )

    private fun saveConfig(draft: AiSettingsDraft) {
        val result = AiConfig.save(options(draft))
        if (result.hasError()) {
            val issue = result.errors().first()
            val base = getString(issue.messageRes)
            message = if (issue.detail.isBlank()) base else "$base：${issue.detail}"
            return
        }
        AiConfig.saveReader(
            AiReaderOptions(
                explainMaxChars = draft.explainMaxChars.toIntOrNull()
                    ?: AiReaderOptions.DEFAULT_EXPLAIN_MAX_CHARS,
                readerAssistantEnabled = draft.readerAssistantEnabled
            )
        )
        message = null
        requireContext().toastOnUi(getString(R.string.ai_settings_saved))
        dismissAllowingStateLoss()
    }

    private fun testConnection(draft: AiSettingsDraft) {
        val options = options(draft)
        val validation = AiConfigValidator.validate(options)
        if (validation.hasError()) {
            val issue = validation.errors().first()
            val base = getString(issue.messageRes)
            message = if (issue.detail.isBlank()) base else "$base：${issue.detail}"
            return
        }
        testing = true
        message = null
        execute {
            chatClient.chat(options, listOf(ChatMessage.user("ping")))
        }.onSuccess {
            testing = false
            message = null
            requireContext().toastOnUi(getString(R.string.ai_ping_ok))
        }.onError { error ->
            testing = false
            message = if (error is AiError) getString(error.messageRes)
            else error.localizedMessage.orEmpty()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsContent(
    initial: AiSettingsDraft,
    message: String?,
    testing: Boolean,
    onTest: (AiSettingsDraft) -> Unit,
    onSave: (AiSettingsDraft) -> Unit
) {
    var endpoint by rememberSaveable { mutableStateOf(initial.endpoint) }
    var apiKey by rememberSaveable { mutableStateOf(initial.apiKey) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var temperature by rememberSaveable { mutableStateOf(initial.temperature) }
    var maxTokens by rememberSaveable { mutableStateOf(initial.maxTokens) }
    var timeout by rememberSaveable { mutableStateOf(initial.timeout) }
    var htmlMaxChars by rememberSaveable { mutableStateOf(initial.htmlMaxChars) }
    var autoRepair by rememberSaveable { mutableStateOf(initial.autoRepair) }
    var readerEnabled by rememberSaveable { mutableStateOf(initial.readerAssistantEnabled) }
    var explainMaxChars by rememberSaveable { mutableStateOf(initial.explainMaxChars) }
    val draft = AiSettingsDraft(
        endpoint, apiKey, model, temperature, maxTokens, timeout,
        htmlMaxChars, autoRepair, readerEnabled, explainMaxChars
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.ai_settings)) }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = { onTest(draft) },
                    enabled = !testing,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.ai_action_test_connection)) }
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(android.R.string.ok)) }
            }
        }
    ) { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AiField(stringResource(R.string.ai_hint_endpoint), endpoint, { endpoint = it }, KeyboardType.Uri)
            AiField(stringResource(R.string.ai_hint_api_key), apiKey, { apiKey = it }, KeyboardType.Password, password = true)
            AiField(stringResource(R.string.ai_hint_model), model, { model = it })
            AiField(stringResource(R.string.ai_hint_temperature), temperature, { temperature = it }, KeyboardType.Decimal)
            AiField(stringResource(R.string.ai_hint_max_tokens), maxTokens, { maxTokens = it }, KeyboardType.Number)
            AiField(stringResource(R.string.ai_hint_timeout_sec), timeout, { timeout = it }, KeyboardType.Number)
            AiField(stringResource(R.string.ai_hint_html_max_chars), htmlMaxChars, { htmlMaxChars = it }, KeyboardType.Number)
            AiCheckRow(stringResource(R.string.ai_auto_repair), autoRepair) { autoRepair = it }
            HorizontalDivider()
            Text(stringResource(R.string.ai_reader_section), style = MaterialTheme.typography.titleMedium)
            AiCheckRow(stringResource(R.string.ai_reader_assistant_enabled), readerEnabled) { readerEnabled = it }
            AiField(stringResource(R.string.ai_hint_explain_max_chars), explainMaxChars, { explainMaxChars = it }, KeyboardType.Number)
            Text(stringResource(R.string.ai_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.takeIf(String::isNotBlank)?.let {
                Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun AiCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
