package io.legado.app.ui.book.read

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadManga
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.help.source.getSourceType
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity

/** Compose overlay for manga controls; the Webtoon RecyclerView remains the image renderer. */
class MangaMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val callBack: CallBack get() = context as CallBack
    private val composeView = LegadoComposeView(context).apply { id = View.generateViewId() }
    private var menuVisible by mutableStateOf(false)
    private var animateMenu by mutableStateOf(!AppConfig.isEInkMode)
    private var isMenuOutAnimating = false
    private var uiState by mutableStateOf(createState())
    private var onMenuOutEnd: (() -> Unit)? = null
    var canShowMenu: Boolean = false

    init {
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent {
            LegadoTheme {
                MangaMenuContent(
                    visible = menuVisible,
                    animate = animateMenu,
                    title = uiState.title,
                    chapterName = uiState.chapterName,
                    chapterUrl = uiState.chapterUrl,
                    sourceName = uiState.sourceName,
                    showTitleBarAddition = uiState.showTitleBarAddition,
                    progress = uiState.progress,
                    pageCount = uiState.pageCount,
                    onAction = ::handleAction,
                    onClosed = ::finishMenuOut
                )
            }
        }
        refreshBookState()
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode) {
        if (isMenuOutAnimating || !menuVisible) return
        isMenuOutAnimating = true
        animateMenu = anim
        menuVisible = false
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        isMenuOutAnimating = false
        onMenuOutEnd = null
        animateMenu = anim
        refreshBookState()
        visibility = View.VISIBLE
        menuVisible = true
        callBack.upSystemUiVisibility(true)
    }

    fun upSeekBar(value: Int, count: Int) {
        uiState = uiState.copy(
            progress = value.coerceIn(0, (count - 1).coerceAtLeast(0)),
            pageCount = count.coerceAtLeast(0)
        )
    }

    private fun refreshBookState() {
        val book = ReadManga.book
        val chapter = ReadManga.curMangaChapter?.chapter
        uiState = uiState.copy(
            title = book?.name.orEmpty(),
            chapterName = chapter?.title.orEmpty(),
            chapterUrl = chapter?.getAbsoluteURL().orEmpty(),
            sourceName = ReadManga.bookSource?.bookSourceName.orEmpty(),
            showTitleBarAddition = AppConfig.showReadTitleBarAddition,
            progress = ReadManga.durChapterPos.coerceAtLeast(0),
            pageCount = ReadManga.curMangaChapter?.imageCount ?: 0
        )
    }

    private fun handleAction(action: ReaderMenuAction, value: Int) {
        when (action) {
            ReaderMenuAction.BookInfo -> if (value == 0) callBack.openBookInfoActivity()
            ReaderMenuAction.OpenChapter -> if (value == 1) showOpenUrlPreference() else openCurrentChapter()
            ReaderMenuAction.PreviousChapter -> ReadManga.moveToPrevChapter(true)
            ReaderMenuAction.NextChapter -> ReadManga.moveToNextChapter(true)
            ReaderMenuAction.SeekPage -> callBack.skipToPage(value)
            ReaderMenuAction.Dismiss -> runMenuOut()
            else -> Unit
        }
    }

    private fun openCurrentChapter() {
        val chapter = ReadManga.curMangaChapter?.chapter ?: return
        val url = chapter.getAbsoluteURL()
        if (AppConfig.readUrlInBrowser) {
            context.openUrl(url.substringBefore(",{"))
        } else {
            context.startActivity<WebViewActivity> {
                putExtra("title", chapter.title)
                putExtra("url", url)
                putExtra("sourceOrigin", ReadManga.bookSource?.bookSourceUrl)
                putExtra("sourceName", ReadManga.bookSource?.bookSourceName)
                putExtra("sourceType", ReadManga.bookSource?.getSourceType())
            }
        }
    }

    private fun showOpenUrlPreference() {
        context.alert(R.string.open_fun) {
            setMessage(R.string.use_browser_open)
            okButton { AppConfig.readUrlInBrowser = true }
            noButton { AppConfig.readUrlInBrowser = false }
        }
    }

    private fun finishMenuOut() {
        if (menuVisible) return
        visibility = View.GONE
        isMenuOutAnimating = false
        canShowMenu = false
        callBack.upSystemUiVisibility(false)
        onMenuOutEnd?.invoke()
        onMenuOutEnd = null
    }

    private fun createState() = MangaMenuUiState(
        title = ReadManga.book?.name.orEmpty(),
        chapterName = ReadManga.curMangaChapter?.chapter?.title.orEmpty(),
        chapterUrl = ReadManga.curMangaChapter?.chapter?.getAbsoluteURL().orEmpty(),
        sourceName = ReadManga.bookSource?.bookSourceName.orEmpty(),
        showTitleBarAddition = AppConfig.showReadTitleBarAddition,
        progress = ReadManga.durChapterPos.coerceAtLeast(0),
        pageCount = ReadManga.curMangaChapter?.imageCount ?: 0
    )

    private data class MangaMenuUiState(
        val title: String = "",
        val chapterName: String = "",
        val chapterUrl: String = "",
        val sourceName: String = "",
        val showTitleBarAddition: Boolean = true,
        val progress: Int = 0,
        val pageCount: Int = 0
    )

    interface CallBack {
        fun openBookInfoActivity()
        fun upSystemUiVisibility(menuIsVisible: Boolean)
        fun skipToPage(index: Int)
    }
}
