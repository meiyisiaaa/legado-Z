package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
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
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout

class PaddingConfigDialog : BaseDialogFragment(R.layout.dialog_read_padding) {

    private var revision by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val attributes = it.attributes
            attributes.dimAmount = 0f
            it.attributes = attributes
        }
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentRevision = revision
                LegadoTheme {
                    PaddingConfigContent(
                        state = currentPaddingState(currentRevision),
                        onValueChange = ::updatePadding,
                        onHeaderLineChange = { enabled ->
                            ReadBookConfig.showHeaderLine = enabled
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            revision++
                        },
                        onFooterLineChange = { enabled ->
                            ReadBookConfig.showFooterLine = enabled
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            revision++
                        }
                    )
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
    }

    private fun currentPaddingState(@Suppress("UNUSED_PARAMETER") revision: Int) = PaddingUiState(
        values = mapOf(
            PaddingField.BODY_TOP to ReadBookConfig.paddingTop,
            PaddingField.BODY_BOTTOM to ReadBookConfig.paddingBottom,
            PaddingField.BODY_LEFT to ReadBookConfig.paddingLeft,
            PaddingField.BODY_RIGHT to ReadBookConfig.paddingRight,
            PaddingField.HEADER_TOP to ReadBookConfig.headerPaddingTop,
            PaddingField.HEADER_BOTTOM to ReadBookConfig.headerPaddingBottom,
            PaddingField.HEADER_LEFT to ReadBookConfig.headerPaddingLeft,
            PaddingField.HEADER_RIGHT to ReadBookConfig.headerPaddingRight,
            PaddingField.FOOTER_TOP to ReadBookConfig.footerPaddingTop,
            PaddingField.FOOTER_BOTTOM to ReadBookConfig.footerPaddingBottom,
            PaddingField.FOOTER_LEFT to ReadBookConfig.footerPaddingLeft,
            PaddingField.FOOTER_RIGHT to ReadBookConfig.footerPaddingRight
        ),
        showHeaderLine = ReadBookConfig.showHeaderLine,
        showFooterLine = ReadBookConfig.showFooterLine
    )

    private fun updatePadding(field: PaddingField, value: Int) {
        when (field) {
            PaddingField.BODY_TOP -> ReadBookConfig.paddingTop = value
            PaddingField.BODY_BOTTOM -> ReadBookConfig.paddingBottom = value
            PaddingField.BODY_LEFT -> ReadBookConfig.paddingLeft = value
            PaddingField.BODY_RIGHT -> ReadBookConfig.paddingRight = value
            PaddingField.HEADER_TOP -> ReadBookConfig.headerPaddingTop = value
            PaddingField.HEADER_BOTTOM -> ReadBookConfig.headerPaddingBottom = value
            PaddingField.HEADER_LEFT -> ReadBookConfig.headerPaddingLeft = value
            PaddingField.HEADER_RIGHT -> ReadBookConfig.headerPaddingRight = value
            PaddingField.FOOTER_TOP -> ReadBookConfig.footerPaddingTop = value
            PaddingField.FOOTER_BOTTOM -> ReadBookConfig.footerPaddingBottom = value
            PaddingField.FOOTER_LEFT -> ReadBookConfig.footerPaddingLeft = value
            PaddingField.FOOTER_RIGHT -> ReadBookConfig.footerPaddingRight = value
        }
        val updateTargets = if (field.name.startsWith("BODY")) arrayListOf(10, 5) else arrayListOf(2)
        postEvent(EventBus.UP_CONFIG, updateTargets)
        revision++
    }
}
