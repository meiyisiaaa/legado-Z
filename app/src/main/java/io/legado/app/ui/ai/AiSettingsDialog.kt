package io.legado.app.ui.ai

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiConfigValidator
import io.legado.app.ai.AiError
import io.legado.app.ai.AiReaderOptions
import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiSettingsBinding
import io.legado.app.help.http.okHttpClient
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/**
 * AI 配置对话框：编辑 endpoint / apiKey / model 等参数并持久化。
 *
 * 直接复用 [AiConfig] 对象读写 SharedPreferences（它是 `io.legado.app.ai` 包中
 * 唯一允许触碰 Android 的文件），不再额外包一层 ViewModel——本对话框自洽，
 * 且与主助手对话框 [AiSourceAssistantDialog] 共享同一份配置快照。
 *
 * 测试连接使用由 [okHttpClient] 派生的 [OpenAiClient]（§7.2 禁止 new OkHttpClient）。
 */
class AiSettingsDialog : BaseDialogFragment(R.layout.dialog_ai_settings) {

    private val binding by viewBinding(DialogAiSettingsBinding::bind)
    private val chatClient by lazy { OpenAiClient(okHttpClient) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.ai_settings)
        loadConfig()

        binding.aiSave.setOnClickListener { saveConfig() }
        binding.aiTest.setOnClickListener { testConnection() }
    }

    private fun loadConfig() {
        hideMsg()
        val options = AiConfig.snapshot()
        binding.aiEndpoint.setText(options.endpoint)
        binding.aiApiKey.setText(options.apiKey)
        binding.aiModel.setText(options.model)
        binding.aiTemperature.setText(options.temperature.toString())
        binding.aiMaxTokens.setText(options.maxTokens.toString())
        binding.aiTimeout.setText(options.timeoutSec.toString())
        binding.aiHtmlMaxChars.setText(options.htmlMaxChars.toString())
        binding.aiAutoRepair.isChecked = options.autoRepair

        val reader = AiConfig.readerSnapshot()
        binding.aiReaderAssistantEnabled.isChecked = reader.readerAssistantEnabled
        binding.aiExplainMaxChars.setText(reader.explainMaxChars.toString())
    }

    /**
     * 读取读者助手参数。
     *
     * 空串/非法输入一律回落默认值而非报错：这些参数只影响成本与效果，
     * 越界值会在 [AiConfig.saveReader] 内被钳制，无需在 UI 侧再拦一道。
     */
    private fun readReaderOptions(): AiReaderOptions {
        return AiReaderOptions(
            explainMaxChars = binding.aiExplainMaxChars.text?.toString()?.toIntOrNull()
                ?: AiReaderOptions.DEFAULT_EXPLAIN_MAX_CHARS,
            readerAssistantEnabled = binding.aiReaderAssistantEnabled.isChecked
        )
    }

    private fun readOptions(): AiChatOptions {
        return AiChatOptions(
            endpoint = binding.aiEndpoint.text?.toString().orEmpty(),
            apiKey = binding.aiApiKey.text?.toString().orEmpty(),
            model = binding.aiModel.text?.toString().orEmpty(),
            temperature = binding.aiTemperature.text?.toString()?.toFloatOrNull()
                ?: AiChatOptions.DEFAULT_TEMPERATURE,
            maxTokens = binding.aiMaxTokens.text?.toString()?.toIntOrNull()
                ?: AiChatOptions.DEFAULT_MAX_TOKENS,
            timeoutSec = binding.aiTimeout.text?.toString()?.toIntOrNull()
                ?: AiChatOptions.DEFAULT_TIMEOUT_SEC,
            htmlMaxChars = binding.aiHtmlMaxChars.text?.toString()?.toIntOrNull()
                ?: AiChatOptions.DEFAULT_HTML_MAX_CHARS,
            autoRepair = binding.aiAutoRepair.isChecked
        )
    }

    private fun saveConfig() {
        val result = AiConfig.save(readOptions())
        if (result.hasError()) {
            val issue = result.errors().first()
            val base = getString(issue.messageRes)
            val detail = if (issue.detail.isBlank()) base else "$base：${issue.detail}"
            showMsg(detail)
        } else {
            // 基础配置校验通过后才落盘读者参数，避免「主配置没存、副配置却存了」的半截状态
            AiConfig.saveReader(readReaderOptions())
            hideMsg()
            requireContext().toastOnUi(getString(R.string.ai_settings_saved))
            dismissAllowingStateLoss()
        }
    }

    private fun testConnection() {
        val options = readOptions()
        val validation = AiConfigValidator.validate(options)
        if (validation.hasError()) {
            val issue = validation.errors().first()
            val base = getString(issue.messageRes)
            showMsg(if (issue.detail.isBlank()) base else "$base：${issue.detail}")
            return
        }
        binding.aiTest.isEnabled = false
        hideMsg()
        execute {
            chatClient.chat(options, listOf(ChatMessage.user("ping")))
        }.onSuccess {
            binding.aiTest.isEnabled = true
            hideMsg()
            requireContext().toastOnUi(getString(R.string.ai_ping_ok))
        }.onError { e ->
            binding.aiTest.isEnabled = true
            val msg = if (e is AiError) getString(e.messageRes) else (e.localizedMessage ?: "")
            showMsg(msg)
        }
    }

    private fun showMsg(text: String) {
        if (text.isBlank()) {
            hideMsg()
            return
        }
        binding.aiMsg.text = text
        binding.aiMsg.visible()
    }

    private fun hideMsg() {
        binding.aiMsg.gone()
    }
}
