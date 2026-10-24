package io.legado.app.ui.book.toc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.FragmentBookmarkBinding
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class BookmarkFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_bookmark),
    TocViewModel.BookmarkCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentBookmarkBinding::bind)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private var durChapterIndex by mutableStateOf(0)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.bookMarkCallBack = this
        binding.composeContainer.addView(
            LegadoComposeView(requireContext()).apply {
                setContent {
                    LegadoTheme {
                        BookmarkScreen(
                            bookmarks = bookmarks,
                            currentChapterIndex = durChapterIndex,
                            onBookmarkClick = ::onClick,
                            onBookmarkLongClick = ::onLongClick
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        viewModel.bookData.observe(viewLifecycleOwner) {
            durChapterIndex = it.durChapterIndex
            upBookmark(null)
        }
    }

    override fun upBookmark(searchKey: String?) {
        val book = viewModel.bookData.value ?: return
        lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> appDb.bookmarkDao.flowByBook(book.name, book.author)
                else -> appDb.bookmarkDao.flowSearch(book.name, book.author, searchKey)
            }.catch {
                AppLog.put("目录界面获取书签数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                bookmarks = it
            }
        }
    }

    private fun onClick(bookmark: Bookmark) {
        activity?.run {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra("index", bookmark.chapterIndex)
                putExtra("chapterPos", bookmark.chapterPos)
            })
            finish()
        }
    }

    private fun onLongClick(bookmark: Bookmark, pos: Int) {
        showDialogFragment(BookmarkDialog(bookmark, pos))
    }
}
