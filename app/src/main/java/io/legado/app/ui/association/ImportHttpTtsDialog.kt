package io.legado.app.ui.association

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateListOf
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.HttpTTS
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

class ImportHttpTtsDialog() : BaseDialogFragment(R.layout.dialog_recycler_view), CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by viewModels<ImportHttpTtsViewModel>()
    private val rows = mutableStateListOf<AssociationImportRow>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean("finishOnDismiss") == true) activity?.finish()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.import_tts)
        binding.rotateLoading.visible()
        installAssociationImportContent(
            requireContext(), binding.recyclerView, rows,
            onSelectionChange = { index, selected ->
                if (index in viewModel.selectStatus.indices) {
                    viewModel.selectStatus[index] = selected
                    rows[index] = rows[index].copy(selected = selected)
                    upSelectText()
                }
            },
            onOpen = ::openItem
        )
        binding.tvCancel.visible()
        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            val waitDialog = WaitDialog(requireContext())
            waitDialog.show()
            viewModel.importSelect {
                waitDialog.dismiss()
                dismissAllowingStateLoss()
            }
        }
        binding.tvFooterLeft.visible()
        binding.tvFooterLeft.setOnClickListener {
            val selectAll = viewModel.isSelectAll
            viewModel.selectStatus.indices.forEach { index ->
                viewModel.selectStatus[index] = !selectAll
                rows.getOrNull(index)?.let { rows[index] = it.copy(selected = !selectAll) }
            }
            upSelectText()
        }
        viewModel.errorLiveData.observe(this) {
            binding.rotateLoading.gone()
            binding.tvMsg.apply { text = it; visible() }
        }
        viewModel.successLiveData.observe(this) { count ->
            binding.rotateLoading.gone()
            if (count > 0) {
                refreshRows()
                upSelectText()
            } else {
                binding.tvMsg.apply { setText(R.string.wrong_format); visible() }
            }
        }
        val source = arguments?.getString("source")
        if (source.isNullOrEmpty()) {
            dismiss()
            return
        }
        viewModel.importSource(source)
    }

    private fun refreshRows() {
        rows.clear()
        viewModel.allSources.forEachIndexed { index, item ->
            val local = viewModel.checkSources.getOrNull(index)
            rows.add(
                AssociationImportRow(
                    name = item.name,
                    status = when {
                        local == null -> "新增"
                        item.lastUpdateTime > local.lastUpdateTime -> "更新"
                        else -> "已有"
                    },
                    selected = viewModel.selectStatus.getOrNull(index) == true,
                    detail = item.url
                )
            )
        }
    }

    private fun openItem(index: Int) {
        viewModel.allSources.getOrNull(index)?.let { item ->
            showDialogFragment(CodeDialog(GSON.toJson(item), disableEdit = false, requestId = index.toString()))
        }
    }

    private fun upSelectText() {
        binding.tvFooterLeft.text = getString(
            if (viewModel.isSelectAll) R.string.select_cancel_count else R.string.select_all_count,
            viewModel.selectCount,
            viewModel.allSources.size
        )
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toIntOrNull()?.let { index ->
            HttpTTS.fromJson(code).getOrNull()?.let { item ->
                if (index in viewModel.allSources.indices) {
                    viewModel.allSources[index] = item
                    refreshRows()
                }
            }
        }
    }
}
