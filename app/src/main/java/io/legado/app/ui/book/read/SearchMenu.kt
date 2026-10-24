package io.legado.app.ui.book.read

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.isVisible
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme

/** Search controls overlay the native ReadView and keep its existing search navigation contract. */
class SearchMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val callBack: CallBack get() = context as CallBack
    private val composeView = LegadoComposeView(context).apply { id = View.generateViewId() }
    private var menuVisible by mutableStateOf(false)
    private var chapterTitle by mutableStateOf("")
    private var resultCount by mutableStateOf(0)
    private var onMenuOutEnd: (() -> Unit)? = null
    private val searchResultList = mutableListOf<SearchResult>()
    private var currentSearchResultIndex = -1
    private var lastSearchResultIndex = -1

    val selectedSearchResult: SearchResult?
        get() = searchResultList.getOrNull(currentSearchResultIndex)
    val previousSearchResult: SearchResult?
        get() = searchResultList.getOrNull(lastSearchResultIndex)
    val bottomMenuVisible: Boolean
        get() = isVisible && menuVisible

    init {
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent {
            LegadoTheme {
                SearchMenuContent(
                    visible = menuVisible,
                    chapterTitle = chapterTitle,
                    resultCount = resultCount,
                    hasResults = searchResultList.isNotEmpty(),
                    onPrevious = { navigateTo(currentSearchResultIndex - 1) },
                    onNext = { navigateTo(currentSearchResultIndex + 1) },
                    onSearchResults = {
                        runMenuOut { callBack.openSearchActivity(selectedSearchResult?.query) }
                    },
                    onMainMenu = {
                        runMenuOut {
                            callBack.cancelSelect()
                            callBack.showMenuBar()
                        }
                    },
                    onExit = { runMenuOut { callBack.exitSearchMenu() } },
                    onClosed = ::finishMenuOut
                )
            }
        }
        updateSearchInfo()
    }

    fun upSearchResultList(resultList: List<SearchResult>) {
        searchResultList.clear()
        searchResultList.addAll(resultList)
        updateSearchInfo()
    }

    fun updateSearchInfo() {
        chapterTitle = ReadBook.curTextChapter?.title.orEmpty()
        resultCount = searchResultList.size
    }

    fun updateSearchResultIndex(updateIndex: Int) {
        lastSearchResultIndex = currentSearchResultIndex
        currentSearchResultIndex = when {
            updateIndex < 0 -> 0
            updateIndex >= searchResultList.size -> searchResultList.lastIndex
            else -> updateIndex
        }
    }

    fun runMenuIn() {
        onMenuOutEnd = null
        visibility = View.VISIBLE
        menuVisible = true
        updateSearchInfo()
        callBack.upSystemUiVisibility()
    }

    fun runMenuOut(onMenuOutEnd: (() -> Unit)? = null) {
        if (!menuVisible) {
            onMenuOutEnd?.invoke()
            return
        }
        this.onMenuOutEnd = onMenuOutEnd
        menuVisible = false
    }

    private fun navigateTo(index: Int) {
        if (searchResultList.isEmpty()) return
        updateSearchResultIndex(index)
        searchResultList.getOrNull(currentSearchResultIndex)?.let {
            callBack.navigateToSearch(it, currentSearchResultIndex)
        }
    }

    private fun finishMenuOut() {
        if (menuVisible) return
        visibility = View.GONE
        callBack.upSystemUiVisibility()
        onMenuOutEnd?.invoke()
        onMenuOutEnd = null
    }

    interface CallBack {
        var isShowingSearchResult: Boolean
        fun openSearchActivity(searchWord: String?)
        fun showSearchSetting()
        fun upSystemUiVisibility()
        fun exitSearchMenu()
        fun showMenuBar()
        fun navigateToSearch(searchResult: SearchResult, index: Int)
        fun onMenuShow()
        fun onMenuHide()
        fun cancelSelect()
    }
}
