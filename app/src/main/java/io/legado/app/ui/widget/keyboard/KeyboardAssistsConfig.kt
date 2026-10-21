package io.legado.app.ui.widget.keyboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.setLayout
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Compose management surface for the keyboard's secondary buttons. */
class KeyboardAssistsConfig(private val callBack: CallBack) : BaseDialogFragment(0) {

    private val assists = mutableStateListOf<KeyboardAssist>()
    private var editingAssist by mutableStateOf<KeyboardAssist?>(null)
    private var editorVisible by mutableStateOf(false)
    private var lineCount by mutableIntStateOf(AppConfig.showBoardLine)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                KeyboardAssistsConfigContent(
                    assists = assists.toList(),
                    lineCount = lineCount,
                    editorVisible = editorVisible,
                    editingAssist = editingAssist,
                    onClose = { dismissAllowingStateLoss() },
                    onChooseLineCount = ::chooseLineCount,
                    onAdd = { openEditor(null) },
                    onEdit = ::openEditor,
                    onDelete = ::deleteAssist,
                    onMove = ::moveAssist,
                    onDragFinished = ::saveOrder,
                    onEditDismiss = ::dismissEditor,
                    onEditSave = ::saveAssist
                )
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            appDb.keyboardAssistsDao.flowAll.catch {
                AppLog.put("辅助按键配置获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect { latest ->
                assists.clear()
                assists.addAll(latest)
            }
        }
    }

    private fun chooseLineCount() {
        NumberPickerDialog(requireContext())
            .setTitle(getString(R.string.setting_show_line_number))
            .setMaxValue(5)
            .setMinValue(1)
            .setValue(lineCount)
            .show { selected ->
                putPrefInt(PreferKey.showBoardLine, selected)
                lineCount = selected
                callBack.requestLayout()
            }
    }

    private fun openEditor(keyboardAssist: KeyboardAssist?) {
        editingAssist = keyboardAssist
        editorVisible = true
    }

    private fun dismissEditor() {
        editorVisible = false
        editingAssist = null
    }

    private fun saveAssist(key: String, value: String) {
        val original = editingAssist
        dismissEditor()
        lifecycleScope.launch(IO) {
            val updated = KeyboardAssist(key = key, value = value)
            if (original == null) {
                updated.serialNo = appDb.keyboardAssistsDao.maxSerialNo + 1
                appDb.keyboardAssistsDao.insert(updated)
            } else {
                updated.serialNo = original.serialNo
                appDb.keyboardAssistsDao.delete(original)
                appDb.keyboardAssistsDao.insert(updated)
            }
        }
    }

    private fun deleteAssist(keyboardAssist: KeyboardAssist) {
        lifecycleScope.launch(IO) {
            appDb.keyboardAssistsDao.delete(keyboardAssist)
        }
    }

    private fun moveAssist(from: Int, to: Int) {
        if (from !in assists.indices || to !in assists.indices || from == to) return
        val item = assists.removeAt(from)
        assists.add(to, item)
    }

    private fun saveOrder() {
        assists.forEachIndexed { index, item -> item.serialNo = index + 1 }
        val ordered = assists.toTypedArray()
        lifecycleScope.launch(IO) {
            appDb.keyboardAssistsDao.update(*ordered)
        }
    }

    interface CallBack {
        /** Notify the keyboard popup that its grid column count changed. */
        fun requestLayout()
    }
}
