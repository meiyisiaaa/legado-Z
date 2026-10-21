package io.legado.app.ui.book.bookmark

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkDialog() : BaseDialogFragment(R.layout.dialog_bookmark, true) {

    constructor(bookmark: Bookmark, editPos: Int = -1) : this() {
        arguments = Bundle().apply {
            putInt("editPos", editPos)
            putParcelable("bookmark", bookmark)
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val arguments = arguments ?: let {
            dismiss()
            return
        }

        @Suppress("DEPRECATION")
        val bookmark = arguments.getParcelable<Bookmark>("bookmark")
        bookmark ?: let {
            dismiss()
            return
        }
        val editPos = arguments.getInt("editPos", -1)
        val container = view as? FrameLayout ?: return
        container.addView(
            LegadoComposeView(requireContext()).apply {
                setContent {
                    LegadoTheme {
                        BookmarkEditScreen(
                            chapterName = bookmark.chapterName,
                            initialBookText = bookmark.bookText,
                            initialNote = bookmark.content,
                            canDelete = editPos >= 0,
                            onSave = { bookText, note ->
                                bookmark.bookText = bookText
                                bookmark.content = note
                                lifecycleScope.launch {
                                    withContext(IO) {
                                        appDb.bookmarkDao.insert(bookmark)
                                    }
                                    dismiss()
                                }
                            },
                            onDelete = {
                                lifecycleScope.launch {
                                    withContext(IO) {
                                        appDb.bookmarkDao.delete(bookmark)
                                    }
                                    dismiss()
                                }
                            },
                            onCancel = { dismiss() }
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

}
