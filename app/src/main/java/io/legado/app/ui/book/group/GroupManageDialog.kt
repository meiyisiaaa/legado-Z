package io.legado.app.ui.book.group

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateListOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Compose bookshelf-group manager; persistence remains in the existing DAO and ViewModel. */
class GroupManageDialog : BaseDialogFragment(0) {

    private val viewModel: GroupViewModel by viewModels()
    private val groups = mutableStateListOf<BookGroup>()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: android.os.Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                GroupManageContent(
                    groups = groups,
                    onAdd = ::addGroup,
                    onEdit = { showDialogFragment(GroupEditDialog(it)) },
                    onShowChange = { group, show -> viewModel.upGroup(group.copy(show = show)) },
                    onMove = ::moveGroup,
                    onDragFinished = ::saveOrder,
                    onDismiss = { dismissAllowingStateLoss() }
                )
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: android.os.Bundle?) {
        lifecycleScope.launch {
            appDb.bookGroupDao.flowAll().catch {
                AppLog.put("书籍分组管理界面获取分组数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { latest ->
                groups.clear()
                groups.addAll(latest)
            }
        }
    }

    private fun addGroup() {
        if (appDb.bookGroupDao.canAddGroup) {
            showDialogFragment(GroupEditDialog())
        } else {
            requireContext().toastOnUi("分组已达上限(64个)")
        }
    }

    private fun moveGroup(from: Int, to: Int) {
        if (from !in groups.indices || to !in groups.indices) return
        val group = groups.removeAt(from)
        groups.add(to, group)
    }

    private fun saveOrder() {
        groups.forEachIndexed { index, group -> group.order = index + 1 }
        viewModel.upGroup(*groups.toTypedArray())
    }
}
