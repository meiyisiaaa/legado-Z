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
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.putPrefInt

/** Click-area mapping editor for the reader's nine touch regions. */
class ClickActionConfigDialog : BaseDialogFragment(R.layout.dialog_click_action_config) {

    private var revision by mutableIntStateOf(0)
    private var activeArea by mutableStateOf<ClickArea?>(null)

    private val actions by lazy {
        listOf(
            -1 to getString(R.string.non_action),
            0 to getString(R.string.menu),
            1 to getString(R.string.next_page),
            2 to getString(R.string.prev_page),
            3 to getString(R.string.next_chapter),
            4 to getString(R.string.previous_chapter),
            5 to getString(R.string.read_aloud_prev_paragraph),
            6 to getString(R.string.read_aloud_next_paragraph),
            7 to getString(R.string.bookmark_add),
            8 to getString(R.string.edit_content),
            9 to getString(R.string.replace_state_change),
            10 to getString(R.string.chapter_list),
            11 to getString(R.string.search_content),
            12 to getString(R.string.sync_book_progress_t),
            13 to getString(R.string.read_aloud_pause_resume)
        )
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? ReadBookActivity)?.bottomDialog =
            ((activity as? ReadBookActivity)?.bottomDialog ?: 1) - 1
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? ReadBookActivity)?.bottomDialog =
            ((activity as? ReadBookActivity)?.bottomDialog ?: 0) + 1
        view.findViewById<ComposeView>(R.id.legado_compose_host).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                @Suppress("UNUSED_VARIABLE")
                val currentRevision = revision
                LegadoTheme {
                    ClickActionContent(
                        actions = actions,
                        selected = ClickArea.entries.associateWith { actionValue(it) },
                        onSelect = { activeArea = it },
                        onDismiss = { dismissAllowingStateLoss() }
                    )
                    activeArea?.let { area ->
                        val selectedIndex = actions.indexOfFirst { it.first == actionValue(area) }.coerceAtLeast(0)
                        ReaderConfigChoiceDialog(
                            title = getString(R.string.select_action),
                            options = actions.map { it.second },
                            selectedIndex = selectedIndex,
                            onDismiss = { activeArea = null },
                            onSelect = { index ->
                                val action = actions[index].first
                                putPrefInt(preferenceKey(area), action)
                                activeArea = null
                                revision++
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppConfig.detectClickArea()
    }

    private fun actionValue(area: ClickArea): Int = when (area) {
        ClickArea.TOP_LEFT -> AppConfig.clickActionTL
        ClickArea.TOP_CENTER -> AppConfig.clickActionTC
        ClickArea.TOP_RIGHT -> AppConfig.clickActionTR
        ClickArea.MIDDLE_LEFT -> AppConfig.clickActionML
        ClickArea.MIDDLE_CENTER -> AppConfig.clickActionMC
        ClickArea.MIDDLE_RIGHT -> AppConfig.clickActionMR
        ClickArea.BOTTOM_LEFT -> AppConfig.clickActionBL
        ClickArea.BOTTOM_CENTER -> AppConfig.clickActionBC
        ClickArea.BOTTOM_RIGHT -> AppConfig.clickActionBR
    }

    private fun preferenceKey(area: ClickArea): String = when (area) {
        ClickArea.TOP_LEFT -> PreferKey.clickActionTL
        ClickArea.TOP_CENTER -> PreferKey.clickActionTC
        ClickArea.TOP_RIGHT -> PreferKey.clickActionTR
        ClickArea.MIDDLE_LEFT -> PreferKey.clickActionML
        ClickArea.MIDDLE_CENTER -> PreferKey.clickActionMC
        ClickArea.MIDDLE_RIGHT -> PreferKey.clickActionMR
        ClickArea.BOTTOM_LEFT -> PreferKey.clickActionBL
        ClickArea.BOTTOM_CENTER -> PreferKey.clickActionBC
        ClickArea.BOTTOM_RIGHT -> PreferKey.clickActionBR
    }
}
