package io.legado.app.ui.book.read

import io.legado.app.help.IntentData
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.launch

/**
 * 处理全文搜索导航逻辑。
 *
 * 职责：
 * - 导航到搜索结果位置（跨章节跳转 + 页内定位）
 * - 打开搜索界面
 *
 * 与 ReadBookActivity 解耦，便于单独测试。
 */
class ReadBookNavigationHandler(
    private val activity: ReadBookActivity,
) {
    /** 导航到搜索结果 */
    fun navigateToSearch(searchResult: SearchResult, index: Int) {
        activity.handlerViewModel.searchResultIndex = index
        skipToSearch(searchResult)
    }

    private fun skipToSearch(searchResult: SearchResult) {
        if (searchResult.chapterIndex != ReadBook.durChapterIndex) {
            activity.handlerViewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition(searchResult)
            }
        } else {
            jumpToPosition(searchResult)
        }
    }

    /** 定位到搜索结果在页面中的具体位置 */
    fun jumpToPosition(searchResult: SearchResult) {
        val curTextChapter = ReadBook.curTextChapter ?: return
        activity.handlerBinding.searchMenu.updateSearchInfo()
        val searchResultPositions =
            activity.handlerViewModel.searchResultPositions(curTextChapter, searchResult)
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) = searchResultPositions
        ReadBook.skipToPage(pageIndex) {
            activity.isSelectingSearchResult = true
            activity.handlerBinding.readView.curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> activity.handlerBinding.readView.curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + searchResultPositions[5] - 1
                )
                1 -> activity.handlerBinding.readView.curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                //consider change page, jump to scroll position
                -1 -> activity.handlerBinding.readView.curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            activity.handlerBinding.readView.isTextSelected = true
            activity.isSelectingSearchResult = false
        }
    }

    /** 打开搜索界面 */
    fun openSearchActivity(searchWord: String?) {
        val book = ReadBook.book ?: return
        activity.searchContentActivity.launch {
            putExtra("bookUrl", book.bookUrl)
            putExtra("searchWord", searchWord ?: activity.handlerViewModel.searchContentQuery)
            putExtra("searchResultIndex", activity.handlerViewModel.searchResultIndex)
            activity.handlerViewModel.searchResultList?.first()?.let {
                if (it.query == activity.handlerViewModel.searchContentQuery) {
                    IntentData.put("searchResultList", activity.handlerViewModel.searchResultList)
                }
            }
        }
    }
}
