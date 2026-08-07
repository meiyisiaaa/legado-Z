package io.legado.app.ui.ai

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.ai.Citation
import io.legado.app.databinding.DialogAiChatBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.read.ReadMenu
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.gone
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/**
 * 阅读器 AI 助手面板（问答 + 赏析共用）。
 *
 * 用 BottomSheet 而不是全屏对话框：读者是「读到一半顺手问一句」，
 * 面板盖住下半屏、上半屏仍能看见原文，比全屏切走上下文更贴合场景。
 *
 * 两种入口模式：
 * - [MODE_QA]：空白面板，读者自己输入问题；
 * - [MODE_EXPLAIN]：带着选中文本进来，**打开即自动发起一次赏析**。
 *
 * 隐私与成本的判断全在 [AiChatViewModel]：这里只负责渲染与转发用户动作，
 * 不自己拼 prompt、不自己发请求。
 */
class AiChatBottomSheet() : BottomSheetDialogFragment(R.layout.dialog_ai_chat),
    Toolbar.OnMenuItemClickListener {

    constructor(mode: Int, selection: String = "") : this() {
        arguments = Bundle().apply {
            putInt(ARG_MODE, mode)
            putString(ARG_SELECTION, selection)
        }
    }

    private val binding by viewBinding(DialogAiChatBinding::bind)
    private val viewModel by viewModels<AiChatViewModel>()

    private val adapter by lazy {
        AiChatAdapter(
            context = requireContext(),
            onCitationClick = ::jumpToCitation,
            onCopy = ::copyAnswer,
            onImageLongClick = { source -> showDialogFragment(PhotoDialog(source)) }
        )
    }

    private var mode = MODE_QA
    private var loading = false

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

        initToolBar()
        initRecycler()
        initInput()
        observeViewModel()

        // 只在首次创建时自动发起赏析：横竖屏重建时 ViewModel 里的结果还在，
        // 再发一次等于让用户白花一次 token
        if (savedInstanceState == null && mode == MODE_EXPLAIN) {
            val selection = arguments?.getString(ARG_SELECTION).orEmpty()
            viewModel.explain(selection)
        }
    }

    private fun initToolBar() {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(
            if (mode == MODE_EXPLAIN) R.string.ai_explain_title else R.string.ai_qa_title
        )
        binding.toolBar.inflateMenu(R.menu.menu_ai_chat)
        binding.toolBar.setOnMenuItemClickListener(this)
    }

    private fun initRecycler() {
        binding.recyclerChat.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerChat.adapter = adapter
    }

    private fun initInput() {
        binding.btnSend.setOnClickListener { onSendOrStop() }
        binding.btnExpand.setOnClickListener {
            binding.btnExpand.gone()
            viewModel.expandToFullBook()
        }
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
        binding.etQuestion.setText("")
        viewModel.ask(question)
    }

    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            adapter.setItems(items)
            if (items.isNotEmpty()) {
                binding.recyclerChat.scrollToPosition(items.lastIndex)
            }
        }
        viewModel.loading.observe(viewLifecycleOwner) { running ->
            loading = running
            if (running) binding.progress.visible() else binding.progress.gone()
            binding.btnSend.setText(if (running) R.string.ai_qa_stop else R.string.ai_qa_send)
        }
        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (!err.isNullOrBlank()) {
                requireContext().toastOnUi(err)
                viewModel.clearError()
            }
        }
        viewModel.canExpand.observe(viewLifecycleOwner) { canExpand ->
            if (canExpand) binding.btnExpand.visible() else binding.btnExpand.gone()
        }
        viewModel.scopeText.observe(viewLifecycleOwner) { scope ->
            binding.tvScope.text = scope
        }
    }

    /**
     * 点出处 → 跳章 → 关面板。
     *
     * 依赖 [ReadMenu.CallBack] 而不是直接转型成 ReadBookActivity：
     * 「跳到第 N 章」在阅读器里本来就有这条既定契约，蹭它比新开一个回调接口干净。
     */
    private fun jumpToCitation(citation: Citation) {
        val callBack = activity as? ReadMenu.CallBack ?: return
        callBack.skipToChapter(citation.chapterIndex)
        dismiss()
    }

    private fun copyAnswer(text: String) {
        requireContext().sendToClip(text)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_ai_chat_clear -> {
                viewModel.clear()
                requireContext().toastOnUi(R.string.ai_qa_cleared)
            }

            R.id.menu_ai_settings -> showDialogFragment(AiSettingsDialog())
        }
        return true
    }

    companion object {
        /** 问答模式：空白面板，等用户提问 */
        const val MODE_QA = 0

        /** 赏析模式：带选中文本进来，打开即请求 */
        const val MODE_EXPLAIN = 1

        private const val ARG_MODE = "mode"
        private const val ARG_SELECTION = "selection"

        /** 面板占屏高比例：留出上方三成让读者还能看见原文 */
        private const val SHEET_HEIGHT_RATIO = 0.7f
    }
}
