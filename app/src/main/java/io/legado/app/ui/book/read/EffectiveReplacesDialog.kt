package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.databinding.DialogEffectiveReplacesComposeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/** Shows only replacement rules active for the currently displayed chapter. */
class EffectiveReplacesDialog : BaseDialogFragment(R.layout.dialog_effective_replaces_compose) {

    private val binding by viewBinding(DialogEffectiveReplacesComposeBinding::bind)
    private val viewModel by activityViewModels<ReadBookViewModel>()
    private val chineseConvert by lazy { ReplaceRule(0, "繁简转换") }
    private var rules by mutableStateOf<List<ReplaceRule>>(emptyList())
    private var isEdit = false

    private val editActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) isEdit = true
        }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val activeRules = ReadBook.curTextChapter?.effectiveReplaceRules.orEmpty()
        rules = if (AppConfig.chineseConverterType > 0) activeRules + chineseConvert else activeRules
        binding.composeContent.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    EffectiveReplacesContent(
                        rules = rules,
                        isChineseConverterEnabled = AppConfig.chineseConverterType > 0,
                        chineseConverterType = AppConfig.chineseConverterType,
                        chineseModes = resources.getStringArray(R.array.chinese_mode).toList(),
                        onRuleClick = { rule ->
                            editActivity.launch(ReplaceEditActivity.startIntent(requireContext(), rule.id))
                        },
                        onRuleDisable = ::disableRule,
                        onChineseModeSelected = ::setChineseMode,
                        onDismiss = { dismissAllowingStateLoss() }
                    )
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (isEdit) viewModel.replaceRuleChanged()
    }

    private fun disableRule(rule: ReplaceRule) {
        isEdit = true
        rules = rules.filterNot { it.id == rule.id }
        rule.isEnabled = false
        appDb.replaceRuleDao.insert(rule)
    }

    private fun setChineseMode(index: Int) {
        if (AppConfig.chineseConverterType != index) {
            AppConfig.chineseConverterType = index
            isEdit = true
        }
        if (index == 0) rules = rules.filterNot { it.id == chineseConvert.id }
    }
}
