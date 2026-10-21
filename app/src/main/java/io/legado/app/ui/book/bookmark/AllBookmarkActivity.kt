package io.legado.app.ui.book.bookmark

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ActivityAllBookmarkBinding
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 所有书签
 */
class AllBookmarkActivity : VMBaseActivity<ActivityAllBookmarkBinding, AllBookmarkViewModel>() {

    override val viewModel by viewModels<AllBookmarkViewModel>()
    override val binding by viewBinding(ActivityAllBookmarkBinding::inflate)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private val exportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.exportBookmark(uri)
                2 -> viewModel.exportBookmarkMd(uri)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        installComposeContent()
        lifecycleScope.launch {
            appDb.bookmarkDao.flowAll().catch {
                AppLog.put("所有书签界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                bookmarks = it
            }
        }
    }

    private fun installComposeContent() {
        binding.composeContainer.addView(
            LegadoComposeView(this).apply {
                setContent {
                    LegadoTheme {
                        AllBookmarkScreen(
                            bookmarks = bookmarks,
                            onBookmarkClick = { bookmark ->
                                onItemClick(bookmark, bookmarks.indexOf(bookmark))
                            },
                            onBookmarkLongClick = { bookmark, position ->
                                onItemLongClick(bookmark, position)
                            }
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

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bookmark, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_export -> exportDir.launch {
                requestCode = 1
            }

            R.id.menu_export_md -> exportDir.launch {
                requestCode = 2
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun onItemClick(bookmark: Bookmark, position: Int) {
        lifecycleScope.launch {
            val book = withContext(IO) {
                appDb.bookDao.getBook(bookmark.bookName, bookmark.bookAuthor)
            }
            if (book == null) {
                showDialogFragment(BookmarkDialog(bookmark, position))
            } else {
                startActivityForBook(book) {
                    putExtra("index", bookmark.chapterIndex)
                    putExtra("chapterPos", bookmark.chapterPos)
                }
            }
        }
    }

    private fun onItemLongClick(bookmark: Bookmark, position: Int): Boolean {
        showDialogFragment(BookmarkDialog(bookmark, position))
        return true
    }

}
