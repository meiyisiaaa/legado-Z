package io.legado.app.ui.book.changecover

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogChangeCoverBinding
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/** Full-height Compose cover picker; image decoding stays in the existing CoverImageView renderer. */
class ChangeCoverDialog() : BaseDialogFragment(R.layout.dialog_change_cover) {

    constructor(name: String, author: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
        }
    }

    private val binding by viewBinding(DialogChangeCoverBinding::bind)
    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeCoverViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.initData(arguments)
        val composeView = LegadoComposeView(requireContext()).apply {
            id = View.generateViewId()
        }
        binding.composeContainer.addView(
            composeView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        composeView.setContent {
            LegadoTheme {
                val covers by viewModel.dataFlow.collectAsState(initial = emptyList())
                val searchState by viewModel.searchStateData.observeAsState(initial = 0)
                ChangeCoverContent(
                    covers = covers,
                    searchState = searchState,
                    onBack = { dismissAllowingStateLoss() },
                    onSearchAction = viewModel::startOrStopSearch,
                    onChoose = ::changeTo
                )
            }
        }
    }

    private fun changeTo(coverUrl: String) {
        callBack?.coverChangeTo(coverUrl)
        dismissAllowingStateLoss()
    }

    interface CallBack {
        fun coverChangeTo(coverUrl: String)
    }
}
