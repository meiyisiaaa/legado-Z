package io.legado.app.ui.book.toc

import android.app.Activity.RESULT_OK
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
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.FragmentChapterListBinding
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.observeEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Callable

class ChapterListFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_chapter_list),
    TocViewModel.ChapterListCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentChapterListBinding::bind)
    private var durChapterIndex = 0
    private var chapterList: List<BookChapter>? = null
    private var chapterQueryJob: Job? = null
    private var chapterQueryGeneration = 0L
    private var uiState by mutableStateOf(ChapterListUiState(isLoading = true))

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.chapterListCallBack = this
        binding.composeContainer.addView(
            LegadoComposeView(requireContext()).apply {
                setContent {
                    LegadoTheme {
                        ChapterListScreen(
                            state = uiState,
                            onChapterClick = ::openChapter,
                            onChapterLongClick = { requireContext().longToastOnUi(it) },
                            onRetry = { upChapterList(uiState.searchKey) }
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        viewModel.bookData.observe(viewLifecycleOwner) { initBook(it) }
    }

    private fun initBook(book: Book) {
        durChapterIndex = book.durChapterIndex
        uiState = uiState.copy(
            currentChapterIndex = durChapterIndex,
            currentChapterInfo = "${book.durChapterTitle}(${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()})",
            isLocalBook = book.isLocal,
            countWords = AppConfig.tocCountWords
        )
        updateTitleRules(book)
        upChapterList(viewModel.searchKey)
        initCacheFileNames(book)
    }

    private fun updateTitleRules(book: Book) {
        lifecycleScope.launch {
            val (rules, replaceBook, useReplace) = withContext(IO) {
                val processor = ContentProcessor.get(book)
                Triple(
                    processor.getTitleReplaceRules().toList(),
                    book.toReplaceBook(),
                    AppConfig.tocUiUseReplace && book.getUseReplaceRule()
                )
            }
            uiState = uiState.copy(
                replaceRules = rules,
                replaceBook = replaceBook,
                useReplace = useReplace,
                replaceRevision = uiState.replaceRevision + 1
            )
        }
    }

    private fun initCacheFileNames(book: Book) {
        lifecycleScope.launch {
            val names = withContext(IO) { BookHelp.getChapterFiles(book) }
            uiState = uiState.copy(cachedFileNames = uiState.cachedFileNames + names)
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (savedBook, chapter) ->
            val currentBookUrl = viewModel.bookData.value?.bookUrl
            if (savedBook.bookUrl == currentBookUrl) {
                uiState = uiState.copy(cachedFileNames = uiState.cachedFileNames + chapter.getFileName())
            }
        }
    }

    override fun upChapterList(searchKey: String?) {
        val currentBook = viewModel.bookData.value ?: return
        val generation = ++chapterQueryGeneration
        chapterQueryJob?.cancel()
        uiState = uiState.copy(
            chapters = emptyList(),
            searchKey = searchKey,
            isLoading = true,
            loadError = null
        )
        chapterQueryJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (items, fullChapterList) = withContext(IO) {
                    val end = currentBook.simulatedTotalChapterNum() - 1
                    val bookUrl = currentBook.bookUrl
                    val chapterDao = appDb.bookChapterDao
                    if (searchKey.isNullOrBlank()) {
                        val chapters = chapterDao.getChapterList(bookUrl, 0, end)
                        chapters to chapters
                    } else if (currentBook.isVideo) {
                        // Filtering and volume indexes must use the same directory snapshot.
                        appDb.runInTransaction(Callable {
                            val allVideoChapters = chapterDao.getChapterList(bookUrl, 0, end)
                            val matchingChapters = chapterDao.search(bookUrl, searchKey, 0, end)
                            matchingChapters to allVideoChapters
                        })
                    } else {
                        chapterDao.search(bookUrl, searchKey, 0, end) to null
                    }
                }
                if (generation != chapterQueryGeneration) return@launch
                if (fullChapterList != null) chapterList = fullChapterList
                uiState = uiState.copy(
                    chapters = items,
                    isLoading = false,
                    loadError = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == chapterQueryGeneration) {
                    AppLog.put("目录列表加载失败", error)
                    uiState = uiState.copy(
                        chapters = emptyList(),
                        isLoading = false,
                        loadError = getString(R.string.error_get_data)
                    )
                }
            } finally {
                if (generation == chapterQueryGeneration) {
                    chapterQueryJob = null
                }
            }
        }
    }

    override fun onDestroyView() {
        chapterQueryGeneration++
        chapterQueryJob?.cancel()
        chapterQueryJob = null
        if (viewModel.chapterListCallBack === this) {
            viewModel.chapterListCallBack = null
        }
        super.onDestroyView()
    }

    override fun clearDisplayTitle() {
        viewModel.bookData.value?.let(::updateTitleRules)
    }

    override fun upAdapter() {
        uiState = uiState.copy(countWords = AppConfig.tocCountWords)
    }

    private fun openChapter(bookChapter: BookChapter) {
        val currentBook = viewModel.bookData.value
        activity?.run {
            if (currentBook?.isVideo == true) {
                val volumes = arrayListOf<BookChapter>()
                chapterList?.forEach { chapter ->
                    if (chapter.isVolume) volumes.add(chapter)
                }
                var chapterInVolumeIndex = 0
                var durVolumeIndex = 0
                if (volumes.isNotEmpty()) {
                    for ((index, volume) in volumes.reversed().withIndex()) {
                        val first = bookChapter.index
                        if (volume.index < first) {
                            chapterInVolumeIndex = first - volume.index - 1
                            durVolumeIndex = volumes.size - index - 1
                            break
                        } else if (volume.index == first) {
                            chapterInVolumeIndex = 0
                            durVolumeIndex = volumes.size - index - 1
                            break
                        }
                    }
                } else {
                    chapterInVolumeIndex = bookChapter.index
                }
                setResult(
                    RESULT_OK, Intent()
                        .putExtra("index", bookChapter.index)
                        .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
                        .putExtra("durVolumeIndex", durVolumeIndex)
                        .putExtra("chapterInVolumeIndex", chapterInVolumeIndex)
                )
                finish()
                return@run
            }
            setResult(
                RESULT_OK, Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
            )
            finish()
        }
    }
}
