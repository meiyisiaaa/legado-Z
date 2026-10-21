package io.legado.app.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.compose.*
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoverRuleConfigDialog : BaseDialogFragment(0) {
    private var rule by mutableStateOf<BookCover.CoverRule?>(null)
    private var busy by mutableStateOf(false)
    private var loadError by mutableStateOf<String?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent { LegadoTheme {
                Surface {
                    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
                        ConfigDialogToolbar(stringResource(R.string.cover_config), ::dismiss)
                        val config = rule
                        when {
                            loadError != null -> Column(Modifier.padding(20.dp)) {
                                Text(loadError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = ::loadRule) { Text("重新加载") }
                            }
                            config == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
                            else -> CoverRuleContent(config, busy, ::saveRule,
                                { writeRule { BookCover.delCoverRule() } }, ::dismiss)
                        }
                    }
                }
            } }
        }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) { loadRule() }

    private fun loadRule() {
        loadError = null
        viewLifecycleOwner.lifecycleScope.launch {
            try { rule = withContext(Dispatchers.IO) { BookCover.getCoverRule() } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { loadError = e.localizedMessage ?: "封面规则读取失败" }
        }
    }
    private fun saveRule(config: BookCover.CoverRule) = writeRule { BookCover.saveCoverRule(config) }
    private fun writeRule(action: () -> Unit) {
        if (busy) return
        busy = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                dismissAllowingStateLoss()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { toastOnUi(e.localizedMessage ?: "封面规则保存失败") }
            finally { busy = false }
        }
    }
}

@Composable
internal fun CoverRuleContent(initial: BookCover.CoverRule, busy: Boolean,
    onSave: (BookCover.CoverRule) -> Unit, onReset: () -> Unit, onClose: () -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(initial.enable) }
    var searchUrl by rememberSaveable { mutableStateOf(initial.searchUrl) }
    var coverRule by rememberSaveable { mutableStateOf(initial.coverRule) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(enabled, enabled = !busy, role = Role.Switch, onValueChange = { enabled = it }),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.enable), Modifier.weight(1f))
                Switch(enabled, onCheckedChange = null, enabled = !busy)
            }
            OutlinedTextField(searchUrl, { searchUrl = it; invalid = false }, enabled = !busy,
                label = { Text(stringResource(R.string.r_search_url)) },
                isError = invalid && searchUrl.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(coverRule, { coverRule = it; invalid = false }, enabled = !busy,
                label = { Text(stringResource(R.string.rule_cover_url)) },
                isError = invalid && coverRule.isBlank(), modifier = Modifier.fillMaxWidth())
            if (invalid) Text("搜索 URL 和封面规则不能为空", color = MaterialTheme.colorScheme.error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onReset, enabled = !busy) { Text(stringResource(R.string.btn_default_s)) }
            Spacer(Modifier.weight(1f))
            TextButton(onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = {
                invalid = searchUrl.isBlank() || coverRule.isBlank()
                if (!invalid) onSave(initial.copy(enable = enabled, searchUrl = searchUrl, coverRule = coverRule))
            }, enabled = !busy) { Text(stringResource(R.string.ok)) }
        }
    }
}
