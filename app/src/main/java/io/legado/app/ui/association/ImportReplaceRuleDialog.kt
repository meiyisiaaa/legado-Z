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
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.DialogCustomGroupBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
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

class ImportReplaceRuleDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener,
    CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("source", source)
            putBoolean("finishOnDismiss", finishOnDismiss)
        }
    }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by viewModels<ImportReplaceRuleViewModel>()
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
        binding.toolBar.setTitle(R.string.import_replace_rule)
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
            onOpen = ::openRule
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
        viewModel.import(source)
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.import_replace)
        binding.toolBar.menu.findItem(R.id.menu_new_group)?.let(::updateGroupMenuTitle)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_new_group -> item?.let(::alertCustomGroup)
            R.id.menu_keep_original_name -> {
                item.isChecked = !item.isChecked
                putPrefBoolean(PreferKey.importKeepName, item.isChecked)
            }
        }
        return true
    }

    private fun alertCustomGroup(item: MenuItem) {
        alert(R.string.diy_edit_source_group) {
            val alertBinding = DialogCustomGroupBinding.inflate(layoutInflater).apply {
                val groups = appDb.replaceRuleDao.allGroups()
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
            noButton()
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
        viewModel.allRules.forEachIndexed { index, item ->
            val local = viewModel.checkRules.getOrNull(index)
            rows.add(
                AssociationImportRow(
                    name = item.group?.takeIf { it.isNotBlank() }?.let { "${item.name} ($it)" } ?: item.name,
                    status = when {
                        local == null -> "新增"
                        item.pattern != local.pattern || item.replacement != local.replacement ||
                            item.isRegex != local.isRegex || item.scope != local.scope -> "更新"
                        else -> "已有"
                    },
                    selected = viewModel.selectStatus.getOrNull(index) == true,
                    detail = item.pattern
                )
            )
        }
    }

    private fun openRule(index: Int) {
        viewModel.allRules.getOrNull(index)?.let { item ->
            showDialogFragment(CodeDialog(GSON.toJson(item), disableEdit = false, requestId = index.toString()))
        }
    }

    private fun upSelectText() {
        binding.tvFooterLeft.text = getString(
            if (viewModel.isSelectAll) R.string.select_cancel_count else R.string.select_all_count,
            viewModel.selectCount,
            viewModel.allRules.size
        )
    }

    override fun onCodeSave(code: String, requestId: String?) {
        requestId?.toIntOrNull()?.let { index ->
            GSON.fromJsonObject<ReplaceRule>(code).getOrNull()?.let { item ->
                if (index in viewModel.allRules.indices) {
                    viewModel.allRules[index] = item
                    refreshRows()
                }
            }
        }
    }
}
