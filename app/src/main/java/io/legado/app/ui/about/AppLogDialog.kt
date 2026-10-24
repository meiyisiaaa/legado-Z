package io.legado.app.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.LogUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import java.util.Date

class AppLogDialog : BaseDialogFragment(0) {
    private var logs by mutableStateOf<List<Triple<Long, String, Throwable?>>>(emptyList())
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent { LegadoTheme {
                DiagnosticsContent(getString(R.string.log), logs.map {
                    DiagnosticItem(it.second, LogUtils.logTimeFormat.format(Date(it.first)), it.third != null)
                }, onClear = { AppLog.clear(); logs = emptyList() }, onOpen = { index ->
                    logs.getOrNull(index)?.third?.let { showDialogFragment(TextDialog("Log", it.stackTraceToString())) }
                }, onClose = ::dismiss)
            } }
        }
    override fun onStart() { super.onStart(); setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT) }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) { logs = AppLog.logs.toList() }
}
