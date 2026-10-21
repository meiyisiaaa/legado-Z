package io.legado.app.ui.ai

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 阅读选区问答与赏析面板。 */
class AiChatBottomSheet() : BottomSheetDialogFragment() {

    constructor(mode: Int, bookmark: Bookmark) : this() {
        arguments = Bundle().apply {
            putInt(ARG_MODE, mode)
            putParcelable(ARG_BOOKMARK, bookmark)
        }
    }

    private val viewModel by viewModels<AiChatViewModel>()
    private var mode by mutableStateOf(MODE_QA)
    private var bookmark by mutableStateOf<Bookmark?>(null)
    private var latestItems: List<AiChatItem> = emptyList()
    private var lastSavedItemId: Long? = null
    private var saving by mutableStateOf(false)
    private var loading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        id = R.id.legado_ai_compose_root
        setContent {
            LegadoTheme {
                val items = viewModel.items.asComposeState().value.orEmpty()
                val running = viewModel.loading.asComposeState().value ?: false
                AiChatContent(
                    mode = mode,
                    bookmark = bookmark ?: return@LegadoTheme,
                    items = items,
                    loading = running,
                    saving = saving,
                    lastSavedItemId = lastSavedItemId,
                    onSend = ::sendQuestion,
                    onStop = { viewModel.stop() },
                    onRetry = { viewModel.retry() },
                    onClear = {
                        viewModel.clear()
                        requireContext().toastOnUi(R.string.ai_qa_cleared)
                    },
                    onOpenSettings = { showDialogFragment(AiSettingsDialog()) },
                    onSaveNote = ::saveLatestAsNote,
                    onCopy = { requireContext().sendToClip(it) },
                    onImageLongClick = { showDialogFragment(PhotoDialog(it)) }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mode = arguments?.getInt(ARG_MODE, MODE_QA) ?: MODE_QA
        @Suppress("DEPRECATION")
        bookmark = arguments?.getParcelable(ARG_BOOKMARK)
        val selectedText = bookmark?.bookText.orEmpty()
        if (selectedText.isBlank()) {
            dismissAllowingStateLoss()
            return
        }
        bookmark?.let { mark ->
            viewModel.bindSelection(
                listOf(mark.bookName, mark.bookAuthor, mark.chapterIndex, mark.chapterPos)
                    .joinToString("\u0000"),
                selectedText
            )
        }
        viewModel.items.observe(viewLifecycleOwner) { latestItems = it }
        viewModel.loading.observe(viewLifecycleOwner) { loading = it }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                requireContext().toastOnUi(error)
                viewModel.clearError()
            }
        }

        // Recreated sheets reuse the ViewModel result and must not send a second paid request.
        if (savedInstanceState == null && mode == MODE_EXPLAIN) viewModel.explain(selectedText)
    }

    override fun onStart() {
        super.onStart()
        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun sendQuestion(question: String): Boolean {
        if (loading) {
            viewModel.stop()
            return false
        }
        if (question.isBlank()) {
            requireContext().toastOnUi(R.string.ai_err_question_empty)
            return false
        }
        return viewModel.ask(bookmark?.bookText.orEmpty(), question)
    }

    private fun saveLatestAsNote() {
        if (saving) return
        val source = bookmark ?: return
        val answerIndex = latestItems.indexOfLast { it.role == AiChatRole.ASSISTANT }
        if (answerIndex < 0) return
        val answer = latestItems[answerIndex]
        if (answer.id == lastSavedItemId) return
        val question = latestItems.firstOrNull { it.id == answer.replyTo && it.role == AiChatRole.USER }
            ?.text.orEmpty()
        val isInitialExplain = mode == MODE_EXPLAIN && question.trim() == source.bookText.trim()
        val content = if (isInitialExplain) {
            getString(R.string.ai_note_explain_format, answer.text)
        } else {
            getString(R.string.ai_note_qa_format, question, answer.text)
        }
        val note = source.copy(time = System.currentTimeMillis(), content = content)
        saving = true
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = try {
                withContext(IO) { runCatching { appDb.bookmarkDao.insert(note) }.isSuccess }
            } finally {
                saving = false
            }
            if (saved) {
                lastSavedItemId = answer.id
                requireContext().toastOnUi(R.string.ai_note_saved)
            } else {
                requireContext().toastOnUi(R.string.ai_note_save_failed)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity?.isChangingConfigurations != true) viewModel.clear()
        super.onDismiss(dialog)
    }

    companion object {
        const val MODE_QA = 0
        const val MODE_EXPLAIN = 1
        private const val ARG_MODE = "mode"
        private const val ARG_BOOKMARK = "bookmark"
        private const val SHEET_HEIGHT_RATIO = 0.7f
    }
}
