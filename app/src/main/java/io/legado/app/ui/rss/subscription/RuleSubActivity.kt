package io.legado.app.ui.rss.subscription

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RuleSub
import io.legado.app.databinding.ActivityRuleSubBinding
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleSubActivity : BaseActivity<ActivityRuleSubBinding>() {

    override val binding by viewBinding(ActivityRuleSubBinding::inflate)
    private val subscriptions = mutableStateListOf<RuleSub>()
    private var editing by mutableStateOf<RuleSub?>(null)
    private var orderDirty = false
    private val typeLabels by lazy { resources.getStringArray(R.array.rule_type).toList() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                RuleSubContent(
                    subscriptions = subscriptions,
                    editing = editing,
                    typeLabels = typeLabels,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onAdd = { editing = RuleSub(customOrder = appDb.ruleSubDao.maxOrder + 1) },
                    onOpen = ::openSubscription,
                    onEdit = ::editSubscription,
                    onDelete = ::deleteSubscription,
                    onMove = ::moveSubscription,
                    onDragFinished = ::persistOrder,
                    onSave = ::saveSubscription,
                    onDismissEditor = { editing = null }
                )
            }
        }
        initData()
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.ruleSubDao.flowAll().catch {
                AppLog.put("规则订阅界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect { rows ->
                subscriptions.clear()
                subscriptions.addAll(rows)
            }
        }
    }

    private fun openSubscription(ruleSub: RuleSub) {
        when (ruleSub.type) {
            0 -> showDialogFragment(ImportBookSourceDialog(ruleSub.url))
            1 -> showDialogFragment(ImportRssSourceDialog(ruleSub.url))
            2 -> showDialogFragment(ImportReplaceRuleDialog(ruleSub.url))
        }
    }

    private fun editSubscription(ruleSub: RuleSub) {
        editing = ruleSub.copy(type = ruleSub.type.takeIf { it in typeLabels.indices } ?: 0)
    }

    private fun saveSubscription(ruleSub: RuleSub) {
        lifecycleScope.launch {
            if (ruleSub.url.isBlank()) {
                toastOnUi(getString(R.string.null_url))
                return@launch
            }
            val duplicate = withContext(IO) { appDb.ruleSubDao.findByUrl(ruleSub.url) }
            if (duplicate != null && duplicate.id != ruleSub.id) {
                toastOnUi("${getString(R.string.url_already)}(${duplicate.name})")
                return@launch
            }
            withContext(IO) { appDb.ruleSubDao.insert(ruleSub) }
            editing = null
        }
    }

    private fun deleteSubscription(ruleSub: RuleSub) {
        lifecycleScope.launch(IO) { appDb.ruleSubDao.delete(ruleSub) }
    }

    private fun moveSubscription(from: Int, to: Int) {
        if (from !in subscriptions.indices || to !in subscriptions.indices || from == to) return
        val item = subscriptions.removeAt(from)
        subscriptions.add(to, item)
        orderDirty = true
    }

    private fun persistOrder() {
        if (!orderDirty) return
        orderDirty = false
        val reordered = subscriptions.mapIndexed { index, ruleSub -> ruleSub.copy(customOrder = index + 1) }
        subscriptions.clear()
        subscriptions.addAll(reordered)
        lifecycleScope.launch(IO) {
            appDb.ruleSubDao.update(*reordered.toTypedArray())
        }
    }
}
