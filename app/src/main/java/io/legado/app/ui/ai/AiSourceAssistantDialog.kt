package io.legado.app.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.base.BaseDialogFragment
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.setLayout
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi

/** AI 书源助手：生成、调试与批量处理仍由既有 ViewModel 驱动。 */
class AiSourceAssistantDialog : BaseDialogFragment(R.layout.dialog_ai_source_assistant), CodeDialog.Callback {

    private val viewModel by viewModels<AiSourceAssistantViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        id = R.id.legado_ai_compose_root
        setLegadoContent {
            val outcome = viewModel.outcome.asComposeState().value
            val loading = viewModel.loading.asComposeState().value ?: false
            val error = viewModel.error.asComposeState().value
            val message = viewModel.message.asComposeState().value
            val batchRunning = viewModel.batchRunning.asComposeState().value ?: false
            val batchProgress = viewModel.batchProgress.asComposeState().value ?: io.legado.app.ai.batch.BatchProgress.EMPTY
            val debugTrialStatus = viewModel.debugTrialStatus.asComposeState().value
            AiSourceAssistantContent(
                outcome = outcome,
                loading = loading,
                error = error,
                message = message,
                batchRunning = batchRunning,
                batchProgress = batchProgress,
                debugTrialStatus = debugTrialStatus,
                onRun = ::runCurrent,
                onDebugTrial = viewModel::runDebugTrial,
                onPreviewEvidence = ::previewEvidence,
                onCancelBatch = viewModel::cancelBatch,
                onImportAll = ::importAllBatch,
                onShowBatchTask = viewModel::showBatchTask,
                onImport = ::importOutcome,
                onCopy = { requireContext().sendToClip(it) },
                onEdit = { showDialogFragment(CodeDialog(it, disableEdit = false)) },
                onSettings = { showDialogFragment(AiSettingsDialog()) },
                onHelp = { showHelp("AiSourceAssistantHelp") },
                onMessageConsumed = viewModel::clearMessage
            )
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.loadConfig()
    }

    private fun runCurrent(mode: Int, draft: AiSourceAssistantDraft) {
        when (mode) {
            MODE_DEBUG -> viewModel.debug(
                draft.sourceJson,
                draft.symptom,
                draft.sampleHtml,
                includeLogs = draft.includeLogs,
                includeHtml = draft.includeHtml
            )
            MODE_BATCH -> viewModel.runBatch(draft.batchUrls, draft.intent)
            else -> viewModel.generate(draft.intent, draft.pageUrl, draft.html)
        }
    }

    private fun previewEvidence(includeLogs: Boolean, includeHtml: Boolean, sampleHtml: String) {
        val preview = viewModel.previewDebugEvidence(includeLogs, includeHtml, sampleHtml)
        if (preview.isBlank()) {
            requireContext().toastOnUi(R.string.ai_debug_no_evidence)
        } else {
            showDialogFragment(CodeDialog(preview, disableEdit = true))
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

    private fun importOutcome(outcome: AiSourceOutcome) {
        val json = outcome.sourceJson
        if (outcome.canImport() && json.isNotBlank()) {
            showDialogFragment(ImportBookSourceDialog(json))
        } else {
            requireContext().toastOnUi(R.string.ai_no_json_to_import)
        }
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
