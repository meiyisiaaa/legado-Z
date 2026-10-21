package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoConfigDialog
import io.legado.app.ui.widget.compose.LegadoDetailSlider
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout

class MangaEpaperDialog : BaseDialogFragment(0) {
    private var threshold by mutableIntStateOf(AppConfig.mangaEInkThreshold.coerceIn(0, 255))
    private val callback get() = activity as? Callback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                LegadoConfigDialog(
                    title = getString(R.string.manga_epaper_stting),
                    onDismiss = ::dismiss
                ) {
                    LegadoDetailSlider(
                        title = getString(R.string.manga_epaper_value),
                        value = threshold,
                        max = 255,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = { callback?.updateEepaper(threshold) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaEInkThreshold = threshold
    }

    interface Callback {
        fun updateEepaper(value: Int)
    }
}
