package io.legado.app.ui.book.manage

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.DialogSourcePickerComposeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** Compose source selector used by bookshelf batch source changes. */
class SourcePickerDialog : BaseDialogFragment(R.layout.dialog_source_picker_compose) {

    private val binding by viewBinding(DialogSourcePickerComposeBinding::bind)
    private var sourceFlowJob: Job? = null
    private var sources by mutableStateOf<List<BookSourcePart>>(emptyList())
    private var query by mutableStateOf("")
    private var isLoading by mutableStateOf(true)
    private var hasError by mutableStateOf(false)
    private val callback: Callback?
        get() = (parentFragment as? Callback) ?: activity as? Callback

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.composeContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    SourcePickerContent(
                        sources = sources,
                        query = query,
                        isLoading = isLoading,
                        hasError = hasError,
                        onQueryChange = {
                            query = it
                            initData(it)
                        },
                        onChangeDelay = ::showChangeDelay,
                        onRetry = { initData(query) },
                        onSourceClick = ::chooseSource
                    )
                }
            }
        }
        initData()
    }

    private fun initData(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        isLoading = true
        hasError = false
        sourceFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            val sourceFlow = if (searchKey.isNullOrEmpty()) {
                appDb.bookSourceDao.flowEnabled()
            } else {
                appDb.bookSourceDao.flowSearchEnabled(searchKey)
            }
            sourceFlow
                .catch {
                    AppLog.put("书源选择界面获取书源数据失败\n${it.localizedMessage}", it)
                    hasError = true
                    isLoading = false
                }
                .flowOn(IO)
                .collect {
                    sources = it
                    isLoading = false
                    hasError = false
                }
        }
    }

    private fun showChangeDelay() {
        NumberPickerDialog(requireContext())
            .setTitle(getString(R.string.change_source_delay))
            .setMaxValue(9999)
            .setMinValue(0)
            .setValue(AppConfig.batchChangeSourceDelay)
            .show { AppConfig.batchChangeSourceDelay = it }
    }

    private fun chooseSource(sourcePart: BookSourcePart) {
        sourcePart.getBookSource()?.let { callback?.sourceOnClick(it) }
        dismissAllowingStateLoss()
    }

    interface Callback {
        fun sourceOnClick(source: BookSource)
    }
}
