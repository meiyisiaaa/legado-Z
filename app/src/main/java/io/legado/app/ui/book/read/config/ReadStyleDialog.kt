package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.github.liuyueyi.quick.transfer.constants.TransType
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment

class ReadStyleDialog : BaseDialogFragment(R.layout.dialog_read_book_style), FontSelectDialog.CallBack {

    private enum class Choice { TextWeight, ChineseMode, Indent }

    private val callBack get() = activity as? ReadBookActivity
    private var revision by mutableIntStateOf(0)
    private var choice by mutableStateOf<Choice?>(null)

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.background)
            decorView.setPadding(0, 0, 0, 0)
            val attributes = attributes
            attributes.dimAmount = 0f
            attributes.gravity = Gravity.BOTTOM
            this.attributes = attributes
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    ReadStyleContent(
                        state = currentUiState(revision),
                        onTextSizeChange = { value ->
                            ReadBookConfig.textSize = value
                            postConfig(8, 5)
                        },
                        onLetterSpacingChange = { value ->
                            ReadBookConfig.letterSpacing = value
                            postConfig(8, 5)
                        },
                        onLineSpacingChange = { value ->
                            ReadBookConfig.lineSpacingExtra = value
                            postConfig(8, 5)
                        },
                        onParagraphSpacingChange = { value ->
                            ReadBookConfig.paragraphSpacing = value
                            postConfig(8, 5)
                        },
                        onPageAnimChange = { value ->
                            ReadBook.book?.setPageAnim(-1)
                            ReadBookConfig.pageAnim = value
                            callBack?.upPageAnim()
                            ReadBook.loadContent(false)
                            revision++
                        },
                        onShareLayoutChange = { checked ->
                            ReadBookConfig.shareLayout = checked
                            postConfig(1, 2, 5)
                        },
                        onTextBoldClick = { choice = Choice.TextWeight },
                        onFontClick = { showDialogFragment<FontSelectDialog>() },
                        onIndentClick = { choice = Choice.Indent },
                        onPaddingClick = {
                            dismissAllowingStateLoss()
                            callBack?.showPaddingConfig()
                        },
                        onTipClick = {
                            TipConfigDialog().show(childFragmentManager, "tipConfigDialog")
                        },
                        onChineseClick = { choice = Choice.ChineseMode },
                        onStyleSelect = ::changeBgTextConfig,
                        onStyleEdit = ::showBgTextConfig,
                        onAddStyle = ::addStyle
                    )
                    renderChoiceDialog()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun currentUiState(@Suppress("UNUSED_PARAMETER") revision: Int) = ReadStyleUiState(
        textSize = ReadBookConfig.textSize,
        letterSpacing = ReadBookConfig.letterSpacing,
        lineSpacingExtra = ReadBookConfig.lineSpacingExtra,
        paragraphSpacing = ReadBookConfig.paragraphSpacing,
        pageAnim = ReadBook.pageAnim(),
        shareLayout = ReadBookConfig.shareLayout,
        styleSelect = ReadBookConfig.styleSelect,
        textBold = ReadBookConfig.textBold,
        chineseConverterType = AppConfig.chineseConverterType,
        textFont = ReadBookConfig.textFont,
        paragraphIndent = ReadBookConfig.paragraphIndent.length,
        configs = ReadBookConfig.configList.map { it.copy() }
    )

    private fun postConfig(vararg values: Int) {
        postEvent(EventBus.UP_CONFIG, values.toCollection(ArrayList<Int>()))
        revision++
    }

    @androidx.compose.runtime.Composable
    private fun renderChoiceDialog() {
        when (choice) {
            Choice.TextWeight -> ReaderConfigChoiceDialog(
                title = getString(R.string.text_font_weight_converter),
                options = resources.getStringArray(R.array.text_font_weight).toList(),
                selectedIndex = ReadBookConfig.textBold,
                onDismiss = { choice = null },
                onSelect = { index ->
                    ReadBookConfig.textBold = index
                    choice = null
                    postConfig(8, 9, 6)
                }
            )
            Choice.ChineseMode -> ReaderConfigChoiceDialog(
                title = getString(R.string.chinese_converter),
                options = resources.getStringArray(R.array.chinese_mode).toList(),
                selectedIndex = AppConfig.chineseConverterType,
                onDismiss = { choice = null },
                onSelect = { index ->
                    AppConfig.chineseConverterType = index
                    choice = null
                    ChineseUtils.unLoad(*TransType.entries.toTypedArray())
                    postConfig(5)
                }
            )
            Choice.Indent -> ReaderConfigChoiceDialog(
                title = getString(R.string.text_indent),
                options = resources.getStringArray(R.array.indent).toList(),
                selectedIndex = ReadBookConfig.paragraphIndent.length,
                onDismiss = { choice = null },
                onSelect = { index ->
                    ReadBookConfig.paragraphIndent = "　".repeat(index)
                    choice = null
                    postConfig(8, 5)
                }
            )
            null -> Unit
        }
    }

    private fun changeBgTextConfig(index: Int) {
        val oldIndex = ReadBookConfig.styleSelect
        if (index == oldIndex) return
        ReadBookConfig.styleSelect = index
        revision++
        postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5))
        if (AppConfig.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    private fun addStyle() {
        ReadBookConfig.configList.add(ReadBookConfig.Config())
        showBgTextConfig(ReadBookConfig.configList.lastIndex)
    }

    private fun showBgTextConfig(index: Int) {
        dismissAllowingStateLoss()
        changeBgTextConfig(index)
        callBack?.showBgTextConfig()
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont || path.isEmpty()) {
            ReadBookConfig.textFont = path
            postConfig(2, 5)
        }
    }
}
