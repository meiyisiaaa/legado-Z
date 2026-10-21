package io.legado.app.ui.book.search

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.DialogSearchScopeComposeBinding
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchScopeDialog : BaseDialogFragment(R.layout.dialog_search_scope_compose) {

    private val binding by viewBinding(DialogSearchScopeComposeBinding::bind)
    private val callback: Callback get() = parentFragment as? Callback ?: activity as Callback
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var sources by mutableStateOf<List<BookSourcePart>>(emptyList())
    private val selectedGroups = mutableStateListOf<String>()
    private var selectedSource by mutableStateOf<BookSourcePart?>(null)
    private var mode by mutableStateOf(SearchScopeMode.Group)
    private var filterText by mutableStateOf("")
    private var isFilterVisible by mutableStateOf(false)
    private var sourceFlowJob: Job? = null

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.8f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.composeContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    SearchScopeContent(
                        mode = mode,
                        groups = groups,
                        sources = sources,
                        selectedGroups = selectedGroups.toSet(),
                        selectedSource = selectedSource,
                        filterText = filterText,
                        isFilterVisible = isFilterVisible,
                        onModeChange = ::changeMode,
                        onFilterTextChange = {
                            filterText = it
                            if (mode == SearchScopeMode.Source) upBookSource(it)
                        },
                        onFilterToggle = { isFilterVisible = !isFilterVisible },
                        onGroupToggle = ::toggleGroup,
                        onSourceSelect = { selectedSource = it },
                        onAllSources = {
                            callback.onSearchScopeOk(SearchScope(""))
                            dismissAllowingStateLoss()
                        },
                        onCancel = { dismissAllowingStateLoss() },
                        onConfirm = ::confirmScope
                    )
                }
            }
        }
        initGroups()
    }

    override fun onDestroyView() {
        sourceFlowJob?.cancel()
        sourceFlowJob = null
        super.onDestroyView()
    }

    private fun initGroups() {
        viewLifecycleOwner.lifecycleScope.launch {
            groups = withContext(IO) { appDb.bookSourceDao.allEnabledGroups() }
        }
    }

    private fun changeMode(next: SearchScopeMode) {
        if (mode == next) return
        mode = next
        if (next == SearchScopeMode.Source) upBookSource(filterText)
    }

    private fun toggleGroup(group: String, checked: Boolean) {
        if (checked && group !in selectedGroups) selectedGroups.add(group)
        if (!checked) selectedGroups.remove(group)
    }

    private fun confirmScope() {
        if (mode == SearchScopeMode.Group) {
            callback.onSearchScopeOk(SearchScope(selectedGroups.toList()))
        } else {
            selectedSource?.let { callback.onSearchScopeOk(SearchScope(it)) }
                ?: callback.onSearchScopeOk(SearchScope(""))
        }
        dismissAllowingStateLoss()
    }

    private fun upBookSource(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        sourceFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = if (searchKey.isNullOrEmpty()) {
                appDb.bookSourceDao.flowAll()
            } else {
                appDb.bookSourceDao.flowSearch(searchKey)
            }
            flow.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                table = AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("多分组/书源界面更新书源出错", it)
            }.flowOn(IO).conflate().collect { data ->
                sources = data
                delay(500)
            }
        }
    }

    interface Callback {
        /** Called with the selected multi-group, individual source, or all-source scope. */
        fun onSearchScopeOk(searchScope: SearchScope)
    }
}
