package io.legado.app.ui.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogMcpSessionBinding
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.mcp.McpConfig
import io.legado.app.service.McpService
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.applyTint
import io.legado.app.utils.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class McpSessionDialog : BaseDialogFragment(R.layout.dialog_mcp_session) {
    private val binding by viewBinding(DialogMcpSessionBinding::bind)
    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitleTextColor(requireContext().getPrimaryTextColor(ColorUtils.isColorLight(primaryColor)))
        val accent = ThemeStore.accentColor(requireContext())
        listOf(binding.start, binding.stop, binding.copyToken, binding.regenerate).forEach { it.applyTint(accent) }
        binding.port.applyTint(accent)
        if (savedInstanceState == null) binding.port.setText(McpConfig.port.toString())
        binding.start.setOnClickListener {
            val port = binding.port.text?.toString()?.toIntOrNull()
            if (port == null || port !in 1024..65535) {
                binding.port.error = getString(R.string.mcp_port_invalid)
                return@setOnClickListener
            }
            McpConfig.port = port
            PermissionsCompat.Builder().addPermissions(Permissions.POST_NOTIFICATIONS)
                .rationale(R.string.notification_permission_rationale)
                .onGranted { if (isAdded) McpService.start(requireContext()) }.request()
        }
        binding.stop.setOnClickListener { McpService.stop(requireContext()) }
        binding.copyToken.setOnClickListener { tokenAction(regenerate = false) }
        binding.regenerate.setOnClickListener {
            McpService.stop(requireContext())
            tokenAction(regenerate = true)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                McpService.status.collect { state ->
                    binding.status.text = state.error ?: when {
                        state.starting -> getString(R.string.mcp_starting)
                        state.running -> state.addresses.joinToString("\n")
                        else -> getString(R.string.mcp_stopped)
                    }
                    binding.start.isEnabled = !state.running && !state.starting
                    binding.port.isEnabled = !state.running && !state.starting
                    binding.stop.isEnabled = state.running || state.starting
                }
            }
        }
    }

    private fun tokenAction(regenerate: Boolean) {
        binding.copyToken.isEnabled = false
        binding.regenerate.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    if (regenerate) McpConfig.regenerateToken() else McpConfig.token()
                }
                if (!regenerate) requireContext().sendToClip(token)
                requireContext().toastOnUi(if (regenerate) R.string.mcp_token_regenerated else R.string.mcp_token_copied)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                requireContext().toastOnUi(R.string.mcp_token_failed)
            } finally {
                if (view != null) {
                    binding.copyToken.isEnabled = true
                    binding.regenerate.isEnabled = true
                }
            }
        }
    }
}
