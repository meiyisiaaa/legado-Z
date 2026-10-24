package io.legado.app.ui.association

import android.content.DialogInterface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.mutableStateListOf
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.DialogCustomGroupBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/** 导入书源。 */
class ImportBookSourceDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener,
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by viewModels<ImportBookSourceViewModel>()
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
        binding.toolBar.setTitle(R.string.import_book_source)
        binding.rotateLoading.visible()
        initMenu()
        installAssociationImportContent(
            requireContext(), binding.recyclerView, rows,
            onSelectionChange = { index, selected ->
                if (index in viewModel.selectStatus.indices) {
                    viewModel.selectStatus[index] = selected
                    rows[index] = rows[index].copy(selected = selected)
                    upSelectText()
                }
            },
            onOpen = ::openSource
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

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.import_source)
        binding.toolBar.menu.apply {
            findItem(R.id.menu_keep_original_name)?.isChecked = AppConfig.importKeepName
            findItem(R.id.menu_keep_group)?.isChecked = AppConfig.importKeepGroup
            findItem(R.id.menu_keep_enable)?.isChecked = AppConfig.importKeepEnable
            findItem(R.id.menu_show_comment)?.isChecked = AppConfig.importShowComment
        }
        binding.toolBar.menu.findItem(R.id.menu_new_group)?.let(::updateGroupMenuTitle)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_new_group -> alertCustomGroup(item)
            R.id.menu_select_new_source -> {
                val selected = !viewModel.isSelectAllNew
                viewModel.newSourceStatus.forEachIndexed { index, isNew ->
                    if (isNew) setSelected(index, selected)
                }
            }
            R.id.menu_select_update_source -> {
                val selected = !viewModel.isSelectAllUpdate
                viewModel.updateSourceStatus.forEachIndexed { index, isUpdate ->
                    if (isUpdate) setSelected(index, selected)
                }
            }
            R.id.menu_keep_original_name -> {
                item.isChecked = !item.isChecked
                putPrefBoolean(PreferKey.importKeepName, item.isChecked)
            }
            R.id.menu_keep_group -> {
                item.isChecked = !item.isChecked
                putPrefBoolean(PreferKey.importKeepGroup, item.isChecked)
            }
            R.id.menu_keep_enable -> {
                item.isChecked = !item.isChecked
                AppConfig.importKeepEnable = item.isChecked
            }
            R.id.menu_show_comment -> {
                item.isChecked = !item.isChecked
                AppConfig.importShowComment = item.isChecked
                refreshRows()
            }
        }
        return false
    }

    private fun setSelected(index: Int, selected: Boolean) {
        if (index !in viewModel.selectStatus.indices) return
        viewModel.selectStatus[index] = selected
        rows.getOrNull(index)?.let { rows[index] = it.copy(selected = selected) }
        upSelectText()
    }

    private fun alertCustomGroup(item: MenuItem) {
        alert(R.string.diy_edit_source_group) {
            val alertBinding = DialogCustomGroupBinding.inflate(layoutInflater).apply {
                val groups = appDb.bookSourceDao.allGroups()
                textInputLayout.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
                editView.setText(viewModel.groupName)
                swAddGroup.isChecked = viewModel.isAddGroup
            }
            customView { alertBinding.root }
            okButton {
                viewModel.isAddGroup = alertBinding.swAddGroup.isChecked
                viewModel.groupName = alertBinding.editView.text?.toString()
                updateGroupMenuTitle(item)
            }
            cancelButton()
        }
    }

    private fun updateGroupMenuTitle(item: MenuItem) {
        item.title = viewModel.groupName?.takeIf { it.isNotBlank() }?.let { group ->
            val title = getString(R.string.diy_edit_source_group_title, group)
            if (viewModel.isAddGroup) "+$title" else title
        } ?: getString(R.string.diy_source_group)
    }

    private fun refreshRows() {
        rows.clear()
        viewModel.allSources.forEachIndexed { index, item ->
            val local = viewModel.checkSources.getOrNull(index)
            rows.add(
                AssociationImportRow(
                    name = item.bookSourceName,
                    status = when {
                        local == null -> "新增"
                        item.lastUpdateTime > local.lastUpdateTime -> "更新"
                        else -> "已有"
                    },
                    selected = viewModel.selectStatus.getOrNull(index) == true,
                    detail = item.bookSourceComment?.takeIf { AppConfig.importShowComment }
                )
            )
        }
    }

    private fun openSource(index: Int) {
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
            GSON.fromJsonObject<BookSource>(code).getOrNull()?.let { item ->
                if (index in viewModel.allSources.indices) {
                    viewModel.allSources[index] = item
                    refreshRows()
                }
            }
        }
    }
}
