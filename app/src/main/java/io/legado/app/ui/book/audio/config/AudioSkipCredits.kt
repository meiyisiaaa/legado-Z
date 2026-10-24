package io.legado.app.ui.book.audio.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.Book
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoConfigDialog
import io.legado.app.ui.widget.compose.LegadoDetailSlider
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import java.lang.ref.WeakReference

class AudioSkipCredits : BaseDialogFragment(0) {
    companion object {
        private var bookRef: WeakReference<Book>? = null

        fun newInstance(book: Book): AudioSkipCredits = AudioSkipCredits().apply {
            bookRef = WeakReference(book)
        }
    }

    private val book: Book by lazy {
        bookRef?.get() ?: throw IllegalStateException("Book reference lost")
    }
    private var openCredits by mutableIntStateOf(0)
    private var closeCredits by mutableIntStateOf(0)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                LegadoConfigDialog(
                    title = getString(R.string.skip_book_credits),
                    onDismiss = ::dismiss
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LegadoDetailSlider(
                            title = "片头",
                            value = openCredits,
                            max = 180,
                            onValueChange = { openCredits = it },
                            onValueChangeFinished = { book.setOpenCredits(openCredits) }
                        )
                        LegadoDetailSlider(
                            title = "片尾",
                            value = closeCredits,
                            max = 180,
                            onValueChange = { closeCredits = it },
                            onValueChangeFinished = { book.setCloseCredits(closeCredits) }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        openCredits = book.getOpenCredits().coerceIn(0, 180)
        closeCredits = book.getCloseCredits().coerceIn(0, 180)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        book.save()
    }
}
