package io.legado.app.ui.book.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

class GroupSelectDialog() : BaseDialogFragment(0) {

    constructor(groupId: Long, requestCode: Int = -1) : this() {
        arguments = Bundle().apply {
            putLong("groupId", groupId)
            putInt("requestCode", requestCode)
        }
    }

    private val groups = mutableStateListOf<BookGroup>()
    private val viewModel: GroupViewModel by viewModels()
    private val callBack get() = activity as? CallBack
    private var requestCode = -1
    private var groupId by mutableLongStateOf(0L)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        groupId = arguments?.getLong("groupId") ?: 0L
        requestCode = arguments?.getInt("requestCode", -1) ?: -1
        return LegadoComposeView(requireContext()).apply {
            setContent {
                LegadoTheme {
                    GroupSelectContent(
                        groups = groups,
                        selectedGroupId = groupId,
                        onSelectedGroupIdChange = { groupId = it },
                        onAdd = { showDialogFragment(GroupEditDialog()) },
                        onEdit = { showDialogFragment(GroupEditDialog(it)) },
                        onMove = ::moveGroup,
                        onDragFinished = ::saveOrder,
                        onCancel = { dismissAllowingStateLoss() },
                        onConfirm = {
                            callBack?.upGroup(requestCode, groupId)
                            dismissAllowingStateLoss()
                        }
                    )
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            appDb.bookGroupDao.flowSelect().conflate().collect { latest ->
                groups.clear()
                groups.addAll(latest)
            }
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

    interface CallBack {
        fun upGroup(requestCode: Int, groupId: Long)
    }
}
