package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout

class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {

    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898
    }

    private var revision by mutableIntStateOf(0)
    private var choice by mutableStateOf<TipChoice?>(null)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (ReadBookConfig.titleMode !in 0..2) ReadBookConfig.titleMode = 0
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentRevision = revision
                LegadoTheme {
                    TipConfigContent(
                        state = currentState(currentRevision),
                        onTitleModeChange = { value ->
                            ReadBookConfig.titleMode = value
                            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                            revision++
                        },
                        onTitleSizeChange = { value ->
                            ReadBookConfig.titleSize = value
                            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                            revision++
                        },
                        onTitleTopChange = { value ->
                            ReadBookConfig.titleTopSpacing = value
                            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                            revision++
                        },
                        onTitleBottomChange = { value ->
                            ReadBookConfig.titleBottomSpacing = value
                            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                            revision++
                        },
                        onHeaderModeClick = { choice = TipChoice.HeaderMode },
                        onFooterModeClick = { choice = TipChoice.FooterMode },
                        onPositionClick = { choice = TipChoice.Position(it) },
                        onTipColorClick = { choice = TipChoice.TipColor },
                        onDividerColorClick = { choice = TipChoice.DividerColor }
                    )
                    choice?.let { request ->
                        val options = optionsFor(request)
                        ReaderConfigChoiceDialog(
                            title = titleFor(request),
                            options = options,
                            selectedIndex = selectedIndex(request),
                            onDismiss = { choice = null },
                            onSelect = { index -> applyChoice(request, index) }
                        )
                    }
                }
            }
        }
        observeEvent<String>(EventBus.TIP_COLOR) { revision++ }
    }

    private fun currentState(@Suppress("UNUSED_PARAMETER") currentRevision: Int): TipConfigUiState {
        val names = ReadTipConfig.tipNames
        fun name(value: Int): String = names.getOrElse(ReadTipConfig.tipValues.indexOf(value)) {
            names.getOrElse(ReadTipConfig.none) { "" }
        }
        val headerModes = ReadTipConfig.getHeaderModes(requireContext())
        val footerModes = ReadTipConfig.getFooterModes(requireContext())
        val dividerNames = ReadTipConfig.tipDividerColorNames
        val tipColor = if (ReadTipConfig.tipColor == 0) {
            ReadTipConfig.tipColorNames.first()
        } else "#${ReadTipConfig.tipColor.hexString}"
        val dividerColor = when (ReadTipConfig.tipDividerColor) {
            -1, 0 -> dividerNames.getOrElse(ReadTipConfig.tipDividerColor + 1) { dividerNames.first() }
            else -> "#${ReadTipConfig.tipDividerColor.hexString}"
        }
        return TipConfigUiState(
            titleMode = ReadBookConfig.titleMode,
            titleSize = ReadBookConfig.titleSize,
            titleTopSpacing = ReadBookConfig.titleTopSpacing,
            titleBottomSpacing = ReadBookConfig.titleBottomSpacing,
            headerMode = headerModes.keys.indexOf(ReadTipConfig.headerMode).coerceAtLeast(0),
            footerMode = footerModes.keys.indexOf(ReadTipConfig.footerMode).coerceAtLeast(0),
            values = mapOf(
                TipPosition.HEADER_LEFT to name(ReadTipConfig.tipHeaderLeft),
                TipPosition.HEADER_MIDDLE to name(ReadTipConfig.tipHeaderMiddle),
                TipPosition.HEADER_RIGHT to name(ReadTipConfig.tipHeaderRight),
                TipPosition.FOOTER_LEFT to name(ReadTipConfig.tipFooterLeft),
                TipPosition.FOOTER_MIDDLE to name(ReadTipConfig.tipFooterMiddle),
                TipPosition.FOOTER_RIGHT to name(ReadTipConfig.tipFooterRight)
            ),
            tipColor = tipColor,
            dividerColor = dividerColor
        )
    }

    private fun optionsFor(request: TipChoice): List<String> = when (request) {
        TipChoice.HeaderMode -> ReadTipConfig.getHeaderModes(requireContext()).values.toList()
        TipChoice.FooterMode -> ReadTipConfig.getFooterModes(requireContext()).values.toList()
        is TipChoice.Position -> ReadTipConfig.tipNames
        TipChoice.TipColor -> ReadTipConfig.tipColorNames
        TipChoice.DividerColor -> ReadTipConfig.tipDividerColorNames
    }

    private fun titleFor(request: TipChoice): String = when (request) {
        TipChoice.HeaderMode -> getString(R.string.header)
        TipChoice.FooterMode -> getString(R.string.footer)
        is TipChoice.Position -> when (request.position) {
            TipPosition.HEADER_LEFT, TipPosition.FOOTER_LEFT -> getString(R.string.left)
            TipPosition.HEADER_MIDDLE, TipPosition.FOOTER_MIDDLE -> getString(R.string.middle)
            TipPosition.HEADER_RIGHT, TipPosition.FOOTER_RIGHT -> getString(R.string.right)
        }
        TipChoice.TipColor -> getString(R.string.text_color)
        TipChoice.DividerColor -> getString(R.string.tip_divider_color)
    }

    private fun selectedIndex(request: TipChoice): Int = when (request) {
        TipChoice.HeaderMode -> ReadTipConfig.getHeaderModes(requireContext()).keys.indexOf(ReadTipConfig.headerMode)
        TipChoice.FooterMode -> ReadTipConfig.getFooterModes(requireContext()).keys.indexOf(ReadTipConfig.footerMode)
        is TipChoice.Position -> ReadTipConfig.tipValues.indexOf(valueFor(request.position))
        TipChoice.TipColor -> if (ReadTipConfig.tipColor == 0) 0 else 1
        TipChoice.DividerColor -> when (ReadTipConfig.tipDividerColor) {
            -1 -> 0
            0 -> 1
            else -> 2
        }
    }.coerceAtLeast(0)

    private fun applyChoice(request: TipChoice, index: Int) {
        when (request) {
            TipChoice.HeaderMode -> {
                ReadTipConfig.headerMode = ReadTipConfig.getHeaderModes(requireContext()).keys.elementAt(index)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
            TipChoice.FooterMode -> {
                ReadTipConfig.footerMode = ReadTipConfig.getFooterModes(requireContext()).keys.elementAt(index)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
            is TipChoice.Position -> {
                val tipValue = ReadTipConfig.tipValues[index]
                clearRepeat(tipValue)
                setValue(request.position, tipValue)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
            TipChoice.TipColor -> when (index) {
                0 -> {
                    ReadTipConfig.tipColor = 0
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                }
                1 -> showColorPicker(TIP_COLOR)
            }
            TipChoice.DividerColor -> when (index) {
                0, 1 -> {
                    ReadTipConfig.tipDividerColor = index - 1
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                }
                2 -> showColorPicker(TIP_DIVIDER_COLOR)
            }
        }
        choice = null
        revision++
    }

    private fun showColorPicker(dialogId: Int) {
        ColorPickerDialog.newBuilder()
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(dialogId)
            .show(requireActivity())
    }

    private fun valueFor(position: TipPosition): Int = when (position) {
        TipPosition.HEADER_LEFT -> ReadTipConfig.tipHeaderLeft
        TipPosition.HEADER_MIDDLE -> ReadTipConfig.tipHeaderMiddle
        TipPosition.HEADER_RIGHT -> ReadTipConfig.tipHeaderRight
        TipPosition.FOOTER_LEFT -> ReadTipConfig.tipFooterLeft
        TipPosition.FOOTER_MIDDLE -> ReadTipConfig.tipFooterMiddle
        TipPosition.FOOTER_RIGHT -> ReadTipConfig.tipFooterRight
    }

    private fun setValue(position: TipPosition, value: Int) {
        when (position) {
            TipPosition.HEADER_LEFT -> ReadTipConfig.tipHeaderLeft = value
            TipPosition.HEADER_MIDDLE -> ReadTipConfig.tipHeaderMiddle = value
            TipPosition.HEADER_RIGHT -> ReadTipConfig.tipHeaderRight = value
            TipPosition.FOOTER_LEFT -> ReadTipConfig.tipFooterLeft = value
            TipPosition.FOOTER_MIDDLE -> ReadTipConfig.tipFooterMiddle = value
            TipPosition.FOOTER_RIGHT -> ReadTipConfig.tipFooterRight = value
        }
    }

    private fun clearRepeat(repeat: Int) {
        if (repeat != ReadTipConfig.none) {
            TipPosition.entries.forEach { position ->
                if (valueFor(position) == repeat) setValue(position, ReadTipConfig.none)
            }
        }
    }

    private sealed interface TipChoice {
        data object HeaderMode : TipChoice
        data object FooterMode : TipChoice
        data class Position(val position: TipPosition) : TipChoice
        data object TipColor : TipChoice
        data object DividerColor : TipChoice
    }
}
