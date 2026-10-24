package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoConfigDialog
import io.legado.app.ui.widget.compose.LegadoDetailSlider
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.setLayout

class MangaColorFilterDialog : BaseDialogFragment(0) {
    private var config by mutableStateOf(
        GSON.fromJsonObject<MangaColorFilterConfig>(AppConfig.mangaColorFilter).getOrNull()
            ?: MangaColorFilterConfig()
    )
    private val callback get() = activity as? Callback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                LegadoConfigDialog(
                    title = getString(R.string.manga_color_filter),
                    onDismiss = ::dismiss
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LegadoDetailSlider(
                            title = stringResource(R.string.brightness),
                            value = config.l,
                            max = 255,
                            onValueChange = { config = config.copy(l = it) },
                            onValueChangeFinished = { callback?.updateColorFilter(config) }
                        )
                        LegadoDetailSlider(
                            title = "R",
                            value = config.r,
                            max = 255,
                            onValueChange = { config = config.copy(r = it) },
                            onValueChangeFinished = { callback?.updateColorFilter(config) }
                        )
                        LegadoDetailSlider(
                            title = "G",
                            value = config.g,
                            max = 255,
                            onValueChange = { config = config.copy(g = it) },
                            onValueChangeFinished = { callback?.updateColorFilter(config) }
                        )
                        LegadoDetailSlider(
                            title = "B",
                            value = config.b,
                            max = 255,
                            onValueChange = { config = config.copy(b = it) },
                            onValueChangeFinished = { callback?.updateColorFilter(config) }
                        )
                        LegadoDetailSlider(
                            title = "A",
                            value = config.a,
                            max = 255,
                            onValueChange = { config = config.copy(a = it) },
                            onValueChangeFinished = { callback?.updateColorFilter(config) }
                        )
                    }
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
        AppConfig.mangaColorFilter = config.toJson()
    }

    interface Callback {
        fun updateColorFilter(config: MangaColorFilterConfig)
    }
}
