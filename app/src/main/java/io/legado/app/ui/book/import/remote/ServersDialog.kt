package io.legado.app.ui.book.import.remote

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppConst.DEFAULT_WEBDAV_ID
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Server
import io.legado.app.databinding.DialogServersComposeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Server selection and CRUD dialog for the remote bookshelf. */
class ServersDialog : BaseDialogFragment(R.layout.dialog_servers_compose) {

    private val binding by viewBinding(DialogServersComposeBinding::bind)
    private val viewModel by viewModels<ServersViewModel>()
    private val callback get() = (activity as? Callback)
    private val servers = mutableStateOf<List<Server>>(emptyList())
    private val selectedServerId = mutableStateOf(AppConfig.remoteServerId)
    private val isLoading = mutableStateOf(true)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.composeContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    ServersContent(
                        servers = servers.value,
                        selectedServerId = selectedServerId.value,
                        isLoading = isLoading.value,
                        onAdd = { showDialogFragment(ServerConfigDialog()) },
                        onSelect = { selectedServerId.value = it },
                        onEdit = { showDialogFragment(ServerConfigDialog(it.id)) },
                        onDelete = ::confirmDelete,
                        onDefault = {
                            AppConfig.remoteServerId = DEFAULT_WEBDAV_ID
                            dismissAllowingStateLoss()
                        },
                        onCancel = { dismissAllowingStateLoss() },
                        onConfirm = {
                            AppConfig.remoteServerId = selectedServerId.value
                            dismissAllowingStateLoss()
                        }
                    )
                }
            }
        }
        initData()
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.serverDao.observeAll()
                .catch {
                    AppLog.put("服务器配置界面获取数据失败\n${it.localizedMessage}", it)
                    isLoading.value = false
                    emit(emptyList())
                }
                .flowOn(IO)
                .collect {
                    servers.value = it
                    isLoading.value = false
                }
        }
    }

    private fun confirmDelete(server: Server) {
        alert {
            setTitle(R.string.draw)
            setMessage(R.string.sure_del)
            yesButton { viewModel.delete(server) }
            noButton()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        callback?.onDialogDismiss("serversDialog")
    }

    interface Callback {
        fun onDialogDismiss(tag: String)
    }
}
