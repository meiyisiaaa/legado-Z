package io.legado.app.ui.book.import.remote

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.Server
import io.legado.app.databinding.DialogServerConfigComposeBinding
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ServerConfigDialog() : BaseDialogFragment(R.layout.dialog_server_config_compose, true) {
    constructor(id: Long) : this() {
        arguments = Bundle().apply {
            putLong(ARG_SERVER_ID, id)
        }
    }

    private val binding by viewBinding(DialogServerConfigComposeBinding::bind)
    private val viewModel by viewModels<ServerConfigViewModel>()
    private val formState = mutableStateOf(ServerConfigUiState())
    private var isSaving by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.composeContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    ServerConfigContent(
                        state = formState.value,
                        isSaving = isSaving,
                        onNameChange = { formState.value = formState.value.copy(name = it) },
                        onUrlChange = { formState.value = formState.value.copy(url = it) },
                        onUsernameChange = { formState.value = formState.value.copy(username = it) },
                        onPasswordChange = { formState.value = formState.value.copy(password = it) },
                        onSave = ::saveServer,
                        onDismiss = ::dismissAllowingStateLoss
                    )
                }
            }
        }
        viewModel.init(arguments?.getLong(ARG_SERVER_ID)) {
            formState.value = viewModel.mServer.toUiState()
        }
    }

    private fun saveServer() {
        val state = formState.value
        if (!state.isLoaded || isSaving) return
        val server = viewModel.mServer?.copy() ?: Server()
        server.name = state.name
        server.type = Server.TYPE.WEBDAV
        server.config = GSON.toJson(
            hashMapOf(
                "url" to state.url,
                "username" to state.username,
                "password" to state.password
            )
        )
        isSaving = true
        viewModel.save(
            server = server,
            onSuccess = {
                isSaving = false
                dismissAllowingStateLoss()
            },
            onError = { isSaving = false }
        )
    }

    private fun Server?.toUiState(): ServerConfigUiState {
        val config = this?.getConfigJsonObject()
        return ServerConfigUiState(
            name = this?.name.orEmpty(),
            url = config?.optString("url").orEmpty(),
            username = config?.optString("username").orEmpty(),
            password = config?.optString("password").orEmpty(),
            isLoaded = true
        )
    }

    companion object {
        private const val ARG_SERVER_ID = "id"
    }
}
