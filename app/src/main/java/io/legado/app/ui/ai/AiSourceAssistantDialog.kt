package io.legado.app.ui.ai

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.ai.batch.BatchInputParser
import io.legado.app.ai.batch.BatchProgress
import io.legado.app.ai.prompt.IntentPromptTemplates
import io.legado.app.ai.prompt.SourceIntent
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiSourceAssistantBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/**
 * AI 书源助手主对话框：生成 / 调试 / 批量三种模式 + 结果预览 + 一键导入。
 *
 * - 生成模式：快捷意图（多选） + 意图 + 页面地址(可选) + 网页 HTML(可选)；
 * - 调试模式：现有书源 JSON + 故障症状 + 样例 HTML；
 * - 批量模式：多行页面地址，串行逐条生成，成功项可合并导入；
 * - 结果区：诊断建议、规则预览（[RulePreviewAdapter]）、原始回复、token 估算，
 *   以及导入 / 复制 / 编辑 / 重试四个动作。
 *
 * 隐私红线（Q5）由内核层保证：调试引擎构造时压根没有 PageFetcher，这里无法越权联网。
 */
class AiSourceAssistantDialog : BaseDialogFragment(R.layout.dialog_ai_source_assistant),
    Toolbar.OnMenuItemClickListener,
    CodeDialog.Callback {

    private val binding by viewBinding(DialogAiSourceAssistantBinding::bind)
    private val viewModel by viewModels<AiSourceAssistantViewModel>()
    private val previewAdapter by lazy { RulePreviewAdapter(requireContext()) }
    private val batchAdapter by lazy {
        BatchTaskAdapter(requireContext()) { task -> viewModel.showBatchTask(task) }
    }

    /** 已勾选的快捷意图；用 LinkedHashSet 只是为了调试时输出顺序稳定，合成顺序由内核固定。 */
    private val selectedIntents = linkedSetOf<SourceIntent>()

    private var mode = MODE_GENERATE
    private var singleRunning = false
    private var batchRunning = false

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.ai_source_assistant)
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.menu_ai_assistant)

        binding.recyclerPreview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPreview.adapter = previewAdapter

        binding.recyclerBatch.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBatch.adapter = batchAdapter
        binding.tilBatchUrls.hint =
            getString(R.string.ai_hint_batch_urls, BatchInputParser.DEFAULT_MAX_TASKS)

        binding.tabGenerate.setOnClickListener { switchMode(MODE_GENERATE) }
        binding.tabDebug.setOnClickListener { switchMode(MODE_DEBUG) }
        binding.tabBatch.setOnClickListener { switchMode(MODE_BATCH) }

        initIntentButtons()

        binding.btnRun.setOnClickListener { runCurrent() }

        binding.btnImport.setOnClickListener {
            val outcome = viewModel.outcome.value
            val json = outcome?.sourceJson
            if (outcome?.canImport() == true && !json.isNullOrBlank()) {
                showDialogFragment(ImportBookSourceDialog(json))
            } else {
                requireContext().toastOnUi(R.string.ai_no_json_to_import)
            }
        }
        binding.btnCopy.setOnClickListener {
            viewModel.outcome.value?.sourceJson?.let { requireContext().sendToClip(it) }
        }
        binding.btnEdit.setOnClickListener {
            viewModel.outcome.value?.sourceJson?.let {
                showDialogFragment(CodeDialog(it, disableEdit = false))
            }
        }
        binding.btnRetry.setOnClickListener { runCurrent() }

        binding.btnBatchCancel.setOnClickListener { viewModel.cancelBatch() }
        binding.btnBatchImportAll.setOnClickListener { importAllBatch() }

        observeViewModel()

        switchMode(MODE_GENERATE)
    }

    /* ==================== 快捷意图 ==================== */

    private fun initIntentButtons() {
        intentButtons().forEach { (intent, button) ->
            button.setOnClickListener {
                if (selectedIntents.contains(intent)) {
                    selectedIntents.remove(intent)
                } else {
                    selectedIntents.add(intent)
                }
                button.isSelected = selectedIntents.contains(intent)
            }
        }
    }

    /** 意图与按钮的固定映射，避免在多个地方各写一遍 when。 */
    private fun intentButtons(): List<Pair<SourceIntent, Button>> = listOf(
        SourceIntent.SEARCH to binding.btnIntentSearch,
        SourceIntent.TOC to binding.btnIntentToc,
        SourceIntent.CONTENT to binding.btnIntentContent,
        SourceIntent.DETAIL to binding.btnIntentDetail
    )

    /** 勾选模板 + 手写意图合成最终意图文本。 */
    private fun composedIntent(): String = IntentPromptTemplates.compose(
        selectedIntents,
        binding.etIntent.text?.toString().orEmpty()
    )

    /* ==================== 模式切换与执行 ==================== */

    private fun switchMode(target: Int) {
        mode = target
        if (target == MODE_GENERATE) binding.groupGenerate.visible() else binding.groupGenerate.gone()
        if (target == MODE_DEBUG) binding.groupDebug.visible() else binding.groupDebug.gone()
        if (target == MODE_BATCH) binding.groupBatch.visible() else binding.groupBatch.gone()
        binding.tabGenerate.isSelected = target == MODE_GENERATE
        binding.tabDebug.isSelected = target == MODE_DEBUG
        binding.tabBatch.isSelected = target == MODE_BATCH
        binding.btnRun.setText(
            when (target) {
                MODE_DEBUG -> R.string.ai_action_diagnose
                MODE_BATCH -> R.string.ai_action_batch_run
                else -> R.string.ai_action_generate
            }
        )
    }

    private fun runCurrent() {
        when (mode) {
            MODE_GENERATE -> viewModel.generate(
                composedIntent(),
                binding.etPageUrl.text?.toString().orEmpty(),
                binding.etHtml.text?.toString().orEmpty()
            )

            MODE_DEBUG -> viewModel.debug(
                binding.etSourceJson.text?.toString().orEmpty(),
                binding.etSymptom.text?.toString().orEmpty(),
                binding.etSampleHtml.text?.toString().orEmpty()
            )

            MODE_BATCH -> viewModel.runBatch(
                binding.etBatchUrls.text?.toString().orEmpty(),
                composedIntent()
            )
        }
    }

    private fun importAllBatch() {
        val json = viewModel.mergedBatchJson()
        if (json == null) {
            requireContext().toastOnUi(R.string.ai_batch_no_success)
            return
        }
        val count = viewModel.batchProgress.value?.importableJsons()?.size ?: 0
        requireContext().toastOnUi(getString(R.string.ai_batch_imported, count))
        showDialogFragment(ImportBookSourceDialog(json))
    }

    /* ==================== 观察 ==================== */

    private fun observeViewModel() {
        viewModel.outcome.observe(viewLifecycleOwner) { showOutcome(it) }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            singleRunning = loading
            upRunningState()
        }
        viewModel.batchRunning.observe(viewLifecycleOwner) { running ->
            batchRunning = running
            binding.btnBatchCancel.isEnabled = running
            upRunningState()
        }
        viewModel.batchProgress.observe(viewLifecycleOwner) { showBatchProgress(it) }
        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) requireContext().toastOnUi(err)
        }
        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                requireContext().toastOnUi(msg)
                viewModel.clearMessage()
            }
        }
    }

    /** 单条与批量共用一个进度条和一个执行按钮，任一在跑就锁住。 */
    private fun upRunningState() {
        val running = singleRunning || batchRunning
        if (running) binding.progress.visible() else binding.progress.gone()
        binding.btnRun.isEnabled = !running
    }

    private fun showBatchProgress(progress: BatchProgress) {
        if (progress.total == 0) {
            binding.tvBatchProgress.gone()
            batchAdapter.setItems(emptyList())
            return
        }
        binding.tvBatchProgress.visible()
        binding.tvBatchProgress.text = getString(
            R.string.ai_batch_progress,
            progress.completedCount,
            progress.total,
            progress.successCount,
            progress.failedCount
        )
        batchAdapter.setItems(progress.tasks)
    }

    private fun showOutcome(outcome: AiSourceOutcome) {
        if (outcome.sourceJson.isNotBlank()) binding.resultActions.visible()
        else binding.resultActions.gone()
        binding.btnImport.isEnabled = outcome.canImport()

        if (outcome.advice.isNotBlank()) {
            binding.tvAdvice.visible()
            binding.tvAdvice.text = outcome.advice
        } else {
            binding.tvAdvice.gone()
        }

        if (outcome.rawReply.isNotBlank()) {
            binding.tvRaw.visible()
            binding.tvRaw.text = outcome.rawReply
        } else {
            binding.tvRaw.gone()
        }

        if (outcome.promptTokenEstimate > 0 || outcome.usage != null) {
            binding.tvToken.visible()
            binding.tvToken.text = buildString {
                append(getString(R.string.ai_token_estimate, outcome.promptTokenEstimate))
                outcome.usage?.let {
                    append("  ")
                    append(getString(R.string.ai_usage, it.totalTokens))
                }
            }
        } else {
            binding.tvToken.gone()
        }

        if (outcome.preview.groups.isNotEmpty()) {
            binding.recyclerPreview.visible()
            previewAdapter.setItems(outcome.preview.groups)
        } else {
            binding.recyclerPreview.gone()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ai_settings -> showDialogFragment(AiSettingsDialog())
            R.id.menu_ai_help -> showHelp("AiSourceAssistantHelp")
        }
        return true
    }

    override fun onCodeSave(code: String, requestId: String?) {
        viewModel.updateEditedSourceJson(code)
    }

    companion object {
        const val MODE_GENERATE = 0
        const val MODE_DEBUG = 1
        const val MODE_BATCH = 2
    }
}
