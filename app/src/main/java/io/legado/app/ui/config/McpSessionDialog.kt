package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.mcp.McpConfig
import io.legado.app.service.McpService
import io.legado.app.ui.widget.compose.*
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class McpSessionDialog : BaseDialogFragment(0) {
    private var tokenBusy by mutableStateOf(false)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    val status by McpService.status.collectAsStateWithLifecycle()
                    val statusText = status.error ?: when {
                        status.starting -> stringResource(R.string.mcp_starting)
                        status.running -> status.addresses.joinToString("\n")
                        else -> stringResource(R.string.mcp_stopped)
                    }
                    McpSessionContent(McpConfig.port.toString(), statusText, status.error != null,
                        status.running || status.starting, tokenBusy, ::startSession,
                        { McpService.stop(requireContext()) }, { tokenAction(false) },
                        { McpService.stop(requireContext()); tokenAction(true) }, ::dismiss)
                }
            }
        }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    private fun startSession(port: Int) {
        McpConfig.port = port
        PermissionsCompat.Builder().addPermissions(Permissions.POST_NOTIFICATIONS)
            .rationale(R.string.notification_permission_rationale)
            .onGranted { if (isAdded) McpService.start(requireContext()) }.request()
    }

    private fun tokenAction(regenerate: Boolean) {
        if (tokenBusy) return
        tokenBusy = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    if (regenerate) McpConfig.regenerateToken() else McpConfig.token()
                }
                if (!regenerate) requireContext().sendToClip(token)
                toastOnUi(if (regenerate) R.string.mcp_token_regenerated else R.string.mcp_token_copied)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                toastOnUi(R.string.mcp_token_failed)
            } finally {
                tokenBusy = false
            }
        }
    }
}

@Composable
internal fun McpSessionContent(initialPort: String, status: String, failed: Boolean,
    active: Boolean, tokenBusy: Boolean, onStart: (Int) -> Unit, onStop: () -> Unit,
    onCopyToken: () -> Unit, onRegenerate: () -> Unit, onClose: () -> Unit) {
    var port by rememberSaveable { mutableStateOf(initialPort) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val motion = LocalLegadoMotion.current
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.imePadding().navigationBarsPadding()) {
            ConfigDialogToolbar(stringResource(R.string.mcp_title), onClose)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    stringResource(R.string.mcp_scope),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(port, { value ->
                    if (value.length <= 5 && value.all { it.isDigit() }) {
                        port = value
                        invalid = false
                    }
                }, enabled = !active,
                    label = { Text(stringResource(R.string.mcp_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, isError = invalid,
                    supportingText = { if (invalid) Text(stringResource(R.string.mcp_port_invalid)) },
                    modifier = Modifier.fillMaxWidth().testTag("mcp_port"))
                AnimatedContent(
                    targetState = status,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        .testTag("mcp_status").semantics { liveRegion = LiveRegionMode.Polite },
                    transitionSpec = {
                        fadeIn(tween(motion.state)) togetherWith fadeOut(tween(motion.state))
                    },
                    label = "MCP session status"
                ) { currentStatus ->
                    SelectionContainer {
                        Text(
                            text = currentStatus,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Button(onClick = {
                    val number = port.toIntOrNull()
                    invalid = number == null || number !in 1024..65535
                    if (!invalid) onStart(number!!)
                }, enabled = !active,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)
                        .testTag("mcp_start")) { Text(stringResource(R.string.mcp_start)) }
                Button(onClick = onStop, enabled = active,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mcp_stop")) {
                    Text(stringResource(R.string.mcp_stop))
                }
                Button(onCopyToken, enabled = !tokenBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mcp_copy_token")) {
                    Text(stringResource(R.string.mcp_copy_token))
                }
                Button(onRegenerate, enabled = !tokenBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mcp_regenerate")) {
                    Text(stringResource(R.string.mcp_regenerate_token))
                }
                if (tokenBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview
@Composable
private fun McpSessionPreview() {
    LegadoTheme { McpSessionContent("1236", "服务已停止", false, false, false, {}, {}, {}, {}, {}) }
}
