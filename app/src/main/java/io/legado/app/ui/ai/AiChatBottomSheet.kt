package io.legado.app.ui.ai

import android.os.Bundle
import android.content.DialogInterface
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.DialogAiChatBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.gone
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 阅读器选区 AI 助手面板（问答 + 赏析共用）。
 *
 * 用 BottomSheet 而不是全屏对话框：读者是「读到一半顺手问一句」，
 * 面板盖住下半屏、上半屏仍能看见原文，比全屏切走上下文更贴合场景。
 *
 * 两种入口模式：
 * - [MODE_QA]：针对选中文字输入问题；
 * - [MODE_EXPLAIN]：带着选中文本进来，**打开即自动发起一次赏析**。
 *
 * 隐私与成本的判断全在 [AiChatViewModel]：这里只负责渲染与转发用户动作，
 * 不自己拼 prompt、不自己发请求。
 */
class AiChatBottomSheet() : BottomSheetDialogFragment(R.layout.dialog_ai_chat),
    Toolbar.OnMenuItemClickListener {

    constructor(mode: Int, bookmark: Bookmark) : this() {
        arguments = Bundle().apply {
            putInt(ARG_MODE, mode)
            putParcelable(ARG_BOOKMARK, bookmark)
        }
    }

    private val binding by viewBinding(DialogAiChatBinding::bind)
    private val viewModel by viewModels<AiChatViewModel>()

    private val adapter by lazy {
        AiChatAdapter(
            context = requireContext(),
            onCopy = ::copyAnswer,
            onImageLongClick = { source -> showDialogFragment(PhotoDialog(source)) }
        )
    }

    private var mode = MODE_QA
    private var loading = false
    private var bookmark: Bookmark? = null
    private var latestItems: List<AiChatItem> = emptyList()
    private var lastSavedItemId: Long? = null
    private var saving = false

    override fun onStart() {
        super.onStart()
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        // 固定高度而不是自适应：对话流会越长越高，自适应会导致面板高度随每条回答跳动
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
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
            viewModel.bindSelection(listOf(mark.bookName, mark.bookAuthor,
                mark.chapterIndex, mark.chapterPos).joinToString("\u0000"), selectedText)
        }

        initToolBar()
        initRecycler()
        initInput()
        observeViewModel()
        binding.tvScope.text = getString(R.string.ai_selection_scope, selectedText.length)

        // 只在首次创建时自动发起赏析：横竖屏重建时 ViewModel 里的结果还在，
        // 再发一次等于让用户白花一次 token
        if (savedInstanceState == null && mode == MODE_EXPLAIN) {
            viewModel.explain(selectedText)
        }
    }

    private fun initToolBar() {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(
            if (mode == MODE_EXPLAIN) R.string.ai_explain_title else R.string.ai_qa_title
        )
        binding.toolBar.inflateMenu(R.menu.menu_ai_chat)
        binding.toolBar.menu.findItem(R.id.menu_ai_chat_save_note)?.isEnabled = false
        binding.toolBar.setOnMenuItemClickListener(this)
    }

    private fun initRecycler() {
        binding.recyclerChat.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerChat.adapter = adapter
    }

    private fun initInput() {
        binding.btnSend.setOnClickListener { onSendOrStop() }
        binding.etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                onSendOrStop()
                true
            } else {
                false
            }
        }
    }

    /** 发送按钮是双态的：空闲时发问，请求中变成「停止」。 */
    private fun onSendOrStop() {
        if (loading) {
            viewModel.stop()
            return
        }
        val question = binding.etQuestion.text?.toString().orEmpty()
        if (question.isBlank()) {
            requireContext().toastOnUi(R.string.ai_err_question_empty)
            return
        }
        if (viewModel.ask(bookmark?.bookText.orEmpty(), question)) binding.etQuestion.setText("")
    }

    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            latestItems = items
            adapter.setItems(items)
            updateSaveAction()
            if (items.isNotEmpty()) {
                binding.recyclerChat.scrollToPosition(items.lastIndex)
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { running ->
            loading = running
            if (running) binding.progress.visible() else binding.progress.gone()
            binding.btnSend.setText(if (running) R.string.ai_qa_stop else R.string.ai_qa_send)
            updateSaveAction()
        }
        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                requireContext().toastOnUi(err)
                viewModel.clearError()
            }
        }
    }

    private fun copyAnswer(text: String) {
        requireContext().sendToClip(text)
    }

    private fun updateSaveAction() {
        val latestAnswer = latestItems.lastOrNull { it.role == AiChatRole.ASSISTANT }
        binding.toolBar.menu.findItem(R.id.menu_ai_chat_save_note)?.isEnabled =
            !saving && latestAnswer != null && latestAnswer.id != lastSavedItemId
        val lastQuestion = latestItems.lastOrNull { it.role == AiChatRole.USER }
        binding.toolBar.menu.findItem(R.id.menu_ai_chat_retry)?.isEnabled = !loading &&
            (lastQuestion?.status == AiChatStatus.FAILED || lastQuestion?.status == AiChatStatus.CANCELLED)
    }

    private fun saveLatestAsNote() {
        if (saving) return
        val source = bookmark ?: return
        val answerIndex = latestItems.indexOfLast { it.role == AiChatRole.ASSISTANT }
        if (answerIndex < 0) return
        val answer = latestItems[answerIndex]
        if (answer.id == lastSavedItemId) return
        val question = latestItems.firstOrNull { it.id == answer.replyTo && it.role == AiChatRole.USER }
            ?.text
            .orEmpty()
        val isInitialExplain = mode == MODE_EXPLAIN && question.trim() == source.bookText.trim()
        val content = if (isInitialExplain) {
            getString(R.string.ai_note_explain_format, answer.text)
        } else {
            getString(R.string.ai_note_qa_format, question, answer.text)
        }
        val note = source.copy(time = System.currentTimeMillis(), content = content)

        saving = true
        updateSaveAction()
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = try {
                withContext(IO) {
                    runCatching { appDb.bookmarkDao.insert(note) }.isSuccess
                }
            } finally {
                saving = false
            }
            if (saved) {
                lastSavedItemId = answer.id
                requireContext().toastOnUi(R.string.ai_note_saved)
            } else {
                requireContext().toastOnUi(R.string.ai_note_save_failed)
            }
            updateSaveAction()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ai_chat_save_note -> saveLatestAsNote()
            R.id.menu_ai_chat_retry -> viewModel.retry()

            R.id.menu_ai_chat_clear -> {
                viewModel.clear()
                requireContext().toastOnUi(R.string.ai_qa_cleared)
            }

            R.id.menu_ai_settings -> showDialogFragment(AiSettingsDialog())
        }
        return true
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity?.isChangingConfigurations != true) viewModel.clear()
        super.onDismiss(dialog)
    }

    companion object {
        /** 选区问答模式：等用户针对选中文字提问 */
        const val MODE_QA = 0

        /** 赏析模式：带选中文本进来，打开即请求 */
        const val MODE_EXPLAIN = 1

        private const val ARG_MODE = "mode"
        private const val ARG_BOOKMARK = "bookmark"

        /** 面板占屏高比例：留出上方三成让读者还能看见原文 */
        private const val SHEET_HEIGHT_RATIO = 0.7f
    }
}
