package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.BaseReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout

class AutoReadDialog : BaseDialogFragment(R.layout.dialog_auto_read) {

    private val callBack: CallBack? get() = activity as? CallBack
    private var pendingSpeed by mutableIntStateOf(1)

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? ReadBookActivity)?.bottomDialog =
            ((activity as? ReadBookActivity)?.bottomDialog ?: 1) - 1
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val readerActivity = activity as? ReadBookActivity
        val bottomDialog = readerActivity?.bottomDialog ?: 0
        if (readerActivity != null) readerActivity.bottomDialog = bottomDialog + 1
        if (bottomDialog > 0) {
            dismiss()
            return
        }
        pendingSpeed = ReadBookConfig.autoReadSpeed.coerceAtLeast(1)
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    AutoReadContent(
                        speed = pendingSpeed,
                        onSpeedChange = { pendingSpeed = it },
                        onSpeedChangeFinished = ::commitSpeed,
                        onCatalog = { callBack?.openChapterList() },
                        onMainMenu = {
                            callBack?.showMenuBar()
                            dismissAllowingStateLoss()
                        },
                        onStop = {
                            callBack?.autoPageStop()
                            dismissAllowingStateLoss()
                        },
                        onSettings = {
                            (activity as? BaseReadBookActivity)?.showPageAnimConfig {
                                (activity as? ReadBookActivity)?.upPageAnim()
                                ReadBook.loadContent(false)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun commitSpeed() {
        val speedValue = pendingSpeed.coerceAtLeast(1)
        ReadBookConfig.autoReadSpeed = speedValue
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun autoPageStop()
    }
}
