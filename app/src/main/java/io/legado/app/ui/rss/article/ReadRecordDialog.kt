package io.legado.app.ui.rss.article

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ReadRecordDialog(private val origin: String? = null) : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val viewModel by viewModels<RssSortViewModel>()
    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val records = mutableStateListOf<RssReadRecord>()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        records.clear()
        records.addAll(viewModel.getRecords(origin))
        val host = LegadoComposeView(requireContext())
        host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        host.setContent {
            LegadoTheme {
                RssReadRecordContent(
                    records = records,
                    onRead = { record ->
                        ReadRss.readRss(activity as AppCompatActivity, record)
                        dismiss()
                    },
                    onClear = {
                        viewModel.deleteAllRecord(origin)
                        records.clear()
                    }
                )
            }
        }
        binding.root.removeAllViews()
        binding.root.addView(
            host,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }
}
