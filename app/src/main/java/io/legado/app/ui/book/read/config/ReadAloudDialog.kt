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
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi

class ReadAloudDialog : BaseDialogFragment(R.layout.dialog_read_aloud) {

    private val callBack: CallBack? get() = activity as? CallBack
    private var playing by mutableStateOf(false)
    private var timerValue by mutableIntStateOf(0)
    private var speechRate by mutableIntStateOf(AppConfig.ttsSpeechRate)
    private var followSystem by mutableStateOf(AppConfig.ttsFlowSys)
    private var showTimerPresets by mutableStateOf(false)

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
        playing = !BaseReadAloudService.pause
        timerValue = (BaseReadAloudService.timeMinute.takeIf { it > 0 } ?: AppConfig.ttsTimer).coerceIn(0, 180)
        speechRate = AppConfig.ttsSpeechRate.coerceIn(0, 45)
        followSystem = AppConfig.ttsFlowSys
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    ReadAloudContent(
                        playing = playing,
                        timerValue = timerValue,
                        speechRate = speechRate,
                        followSystem = followSystem,
                        onPreviousChapter = { ReadBook.moveToPrevChapter(upContent = true, toLast = false) },
                        onNextChapter = { ReadBook.moveToNextChapter(true) },
                        onPreviousParagraph = { ReadAloud.prevParagraph(requireContext()) },
                        onNextParagraph = { ReadAloud.nextParagraph(requireContext()) },
                        onPlayPause = {
                            callBack?.onClickReadAloud()
                            playing = !BaseReadAloudService.pause
                        },
                        onStop = {
                            ReadAloud.stop(requireContext())
                            dismissAllowingStateLoss()
                        },
                        onTimerChange = { timerValue = it.coerceIn(0, 180) },
                        onTimerChangeFinished = { ReadAloud.setTimer(requireContext(), timerValue) },
                        onTimerPresetClick = { showTimerPresets = true },
                        onSaveTimer = {
                            AppConfig.ttsTimer = timerValue
                            requireContext().toastOnUi("保存设定时间成功！")
                        },
                        onFollowSystemChange = { enabled ->
                            followSystem = enabled
                            AppConfig.ttsFlowSys = enabled
                            updateSpeechRate()
                        },
                        onSpeechRateChange = { speechRate = it.coerceIn(0, 45) },
                        onSpeechRateChangeFinished = { commitSpeechRate() },
                        onPreviousSpeed = {
                            speechRate = (speechRate - 1).coerceAtLeast(0)
                            commitSpeechRate()
                        },
                        onNextSpeed = {
                            speechRate = (speechRate + 1).coerceAtMost(45)
                            commitSpeechRate()
                        },
                        onCatalog = { callBack?.openChapterList() },
                        onMainMenu = {
                            callBack?.showMenuBar()
                            dismissAllowingStateLoss()
                        },
                        onBackground = { callBack?.finish() },
                        onSettings = {
                            ReadAloudConfigDialog().show(childFragmentManager, "readAloudConfigDialog")
                        },
                        onRoleTts = {
                            RoleTtsDialog().show(childFragmentManager, "roleTtsDialog")
                        }
                    )
                    if (showTimerPresets) {
                        val timerOptions = intArrayOf(0, 5, 10, 15, 30, 60, 90, 180)
                        ReaderConfigChoiceDialog(
                            title = getString(R.string.set_timer),
                            options = timerOptions.map { getString(R.string.timer_m, it) },
                            selectedIndex = timerOptions.indexOf(timerValue).takeIf { it >= 0 },
                            onDismiss = { showTimerPresets = false },
                            onSelect = { index ->
                                val selectedMinutes = timerOptions[index]
                                timerValue = selectedMinutes
                                ReadAloud.setTimer(requireContext(), selectedMinutes)
                                showTimerPresets = false
                            }
                        )
                    }
                }
            }
        }
    }

    private fun commitSpeechRate() {
        AppConfig.ttsSpeechRate = speechRate.coerceIn(0, 45)
        updateSpeechRate()
    }

    private fun updateSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    override fun observeLiveBus() {
        observeEvent<Int>(EventBus.ALOUD_STATE) { playing = !BaseReadAloudService.pause }
        observeEvent<Int>(EventBus.READ_ALOUD_DS) { timerValue = it.coerceIn(0, 180) }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun onClickReadAloud()
        fun finish()
    }
}
