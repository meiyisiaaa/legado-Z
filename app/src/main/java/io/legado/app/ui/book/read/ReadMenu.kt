package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.source.getSourceType
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.activity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.startActivity

/** Reader controls are Compose; the page renderer remains the existing ReadView. */
class ReadMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var canShowMenu: Boolean = false
    private val callBack: CallBack get() = activity as CallBack
    private val composeView = LegadoComposeView(context).apply {
        id = View.generateViewId()
    }
    private var menuVisible by mutableStateOf(false)
    private var isMenuOutAnimating = false
    private var onMenuOutEnd: (() -> Unit)? = null
    private var uiState by mutableStateOf(createInitialState())
    private var contentObserver: ContentObserver? = null

    init {
        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        composeView.setContent {
            LegadoTheme {
                ReadMenuContent(
                    state = uiState,
                    visible = menuVisible,
                    onAction = ::handleAction,
                    onBrightnessChange = { value ->
                        uiState = uiState.copy(brightness = value.coerceIn(0, 255))
                        setScreenBrightness(value.toFloat())
                    },
                    onBrightnessFinished = { value -> AppConfig.readBrightness = value.coerceIn(0, 255) },
                    onClosed = ::finishMenuOut,
                    onHelp = {
                        if (!LocalConfig.readMenuHelpVersionIsLast) callBack.showHelp()
                    },
                    onOpenUrlPreference = ::showOpenUrlPreference
                )
            }
        }
        refreshState()
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        onMenuOutEnd = null
        isMenuOutAnimating = false
        refreshState()
        uiState = uiState.copy(animate = anim)
        visibility = View.VISIBLE
        menuVisible = true
        callBack.onMenuShow()
        callBack.upSystemUiVisibility()
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode, onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating || !menuVisible) return
        isMenuOutAnimating = true
        this.onMenuOutEnd = onMenuOutEnd
        callBack.onMenuHide()
        uiState = uiState.copy(animate = anim)
        menuVisible = false
    }

    fun reset() {
        refreshState()
    }

    fun refreshMenuColorFilter() {
        refreshState()
    }

    fun upBrightnessState() {
        val auto = brightnessAuto()
        uiState = uiState.copy(
            brightness = AppConfig.readBrightness.coerceIn(0, 255),
            brightnessAuto = auto,
            brightnessEnabled = !auto,
            showBrightness = context.getPrefBoolean(PreferKey.showBrightnessView, true)
        )
        setScreenBrightness(AppConfig.readBrightness.toFloat())
    }

    fun upBookView() {
        refreshBookState()
    }

    fun upSeekBar() {
        val chapter = ReadBook.curTextChapter
        val chapterProgress = AppConfig.progressBarBehavior == "chapter"
        val maximum = if (chapterProgress) {
            ReadBook.simulatedChapterSize - 1
        } else {
            (chapter?.pageSize ?: 1) - 1
        }
        val progress = if (chapterProgress) ReadBook.durChapterIndex else ReadBook.durPageIndex
        uiState = uiState.copy(
            chapterProgress = chapterProgress,
            maxProgress = maximum.coerceAtLeast(0),
            progress = progress.coerceIn(0, maximum.coerceAtLeast(0))
        )
    }

    fun setSeekPage(seek: Int) {
        uiState = uiState.copy(progress = seek.coerceIn(0, uiState.maxProgress))
    }

    fun setAutoPage(autoPage: Boolean) {
        uiState = uiState.copy(autoPage = autoPage)
    }

    private fun refreshState() {
        refreshPalette()
        refreshBookState()
        upBrightnessState()
    }

    private fun refreshPalette() {
        val followsPage = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
        val background = if (followsPage) {
            runCatching { ReadBookConfig.durConfig.curBgStr().toColorInt() }
                .getOrDefault(context.bottomBackground)
        } else {
            context.bottomBackground
        }
        val foreground = if (followsPage) {
            ReadBookConfig.durConfig.curTextColor()
        } else {
            context.getPrimaryTextColor(ColorUtils.isColorLight(background))
        }
        uiState = uiState.copy(
            background = androidx.compose.ui.graphics.Color(background),
            foreground = androidx.compose.ui.graphics.Color(foreground),
            accent = androidx.compose.ui.graphics.Color(context.accentColor),
            brightnessOnStart = AppConfig.brightnessVwPos
        )
    }

    private fun refreshBookState() {
        val book = ReadBook.book
        val source = ReadBook.bookSource
        val chapter = ReadBook.curTextChapter
        val sourceLogin = !source?.loginUrl.isNullOrEmpty()
        uiState = uiState.copy(
            title = book?.name.orEmpty(),
            chapterName = chapter?.title.orEmpty(),
            chapterUrl = if (ReadBook.isLocalBook) "" else chapter?.chapter?.getAbsoluteURL().orEmpty(),
            sourceName = source?.bookSourceName.orEmpty(),
            showTitleBarAddition = AppConfig.showReadTitleBarAddition,
            showSource = !ReadBook.isLocalBook,
            canLogin = sourceLogin,
            canPayChapter = sourceLogin && chapter?.isVip == true && chapter?.isPay != true,
            hasCustomButton = source?.customButton == true,
            isLocalBook = ReadBook.isLocalBook,
            canGoPrevious = ReadBook.durChapterIndex > 0,
            canGoNext = ReadBook.durChapterIndex < ReadBook.simulatedChapterSize - 1
        )
        upSeekBar()
    }

    private fun handleAction(action: ReaderMenuAction, value: Int) {
        when (action) {
            ReaderMenuAction.BookInfo -> if (value == 0) callBack.openBookInfoActivity()
            ReaderMenuAction.OpenChapter -> openCurrentChapter()
            ReaderMenuAction.CustomButton -> runCustomButton(SourceCallBack.CLICK_CUSTOM_BUTTON)
            ReaderMenuAction.CustomButtonLong -> runCustomButton(SourceCallBack.LONG_CLICK_CUSTOM_BUTTON)
            ReaderMenuAction.Search -> runMenuOut { callBack.openSearchActivity(null) }
            ReaderMenuAction.AutoPage -> runMenuOut { callBack.autoPage() }
            ReaderMenuAction.Replace -> callBack.openReplaceRule()
            ReaderMenuAction.NightTheme -> {
                AppConfig.isNightTheme = !AppConfig.isNightTheme
                ThemeConfig.applyDayNight(context)
                refreshPalette()
            }
            ReaderMenuAction.PreviousChapter -> ReadBook.moveToPrevChapter(upContent = true, toLast = false)
            ReaderMenuAction.NextChapter -> ReadBook.moveToNextChapter(true)
            ReaderMenuAction.ChapterList -> runMenuOut { callBack.openChapterList() }
            ReaderMenuAction.ReadAloud -> runMenuOut { callBack.onClickReadAloud() }
            ReaderMenuAction.ReadAloudSettings -> runMenuOut { callBack.showReadAloudDialog() }
            ReaderMenuAction.ReadStyle -> runMenuOut { callBack.showReadStyle() }
            ReaderMenuAction.MoreSettings -> runMenuOut { callBack.showMoreSetting() }
            ReaderMenuAction.Login -> callBack.showLogin()
            ReaderMenuAction.PayChapter -> callBack.payAction()
            ReaderMenuAction.EditSource -> callBack.openSourceEditActivity()
            ReaderMenuAction.DisableSource -> callBack.disableSource()
            ReaderMenuAction.ToggleBrightnessAuto -> {
                context.putPrefBoolean("brightnessAuto", !brightnessAuto())
                upBrightnessState()
            }
            ReaderMenuAction.ChangeBrightnessPosition -> {
                AppConfig.brightnessVwPos = !AppConfig.brightnessVwPos
                refreshPalette()
            }
            ReaderMenuAction.PreviewPage -> setSeekPage(value)
            ReaderMenuAction.SeekPage -> ReadBook.skipToPage(value)
            ReaderMenuAction.SeekChapter -> seekChapter(value)
            ReaderMenuAction.Dismiss -> runMenuOut()
        }
    }

    private fun seekChapter(index: Int) {
        if (confirmSkipToChapter) {
            callBack.skipToChapter(index)
            return
        }
        context.alert("章节跳转确认", "确定要跳转章节吗？") {
            yesButton {
                confirmSkipToChapter = true
                callBack.skipToChapter(index)
            }
            noButton { upSeekBar() }
            onCancelled { upSeekBar() }
        }
    }

    private var confirmSkipToChapter = false

    private fun runCustomButton(action: String) {
        val book = ReadBook.book ?: return
        val hostActivity = activity ?: return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        SourceCallBack.callBackBtn(
            hostActivity,
            action,
            ReadBook.bookSource,
            book,
            chapter,
            BookType.text
        )
    }

    private fun openCurrentChapter() {
        val chapter = ReadBook.curTextChapter ?: return
        val url = chapter.chapter.getAbsoluteURL()
        if (AppConfig.readUrlInBrowser) {
            context.openUrl(url.substringBefore(",{"))
        } else {
            context.startActivity<WebViewActivity> {
                putExtra("title", chapter.title)
                putExtra("url", url)
                putExtra("sourceOrigin", ReadBook.bookSource?.bookSourceUrl)
                putExtra("sourceName", ReadBook.bookSource?.bookSourceName)
                putExtra("sourceType", ReadBook.bookSource?.getSourceType())
            }
        }
    }

    private fun showOpenUrlPreference() {
        if (ReadBook.isLocalBook) return
        context.alert(R.string.open_fun) {
            setMessage(R.string.use_browser_open)
            okButton { AppConfig.readUrlInBrowser = true }
            noButton { AppConfig.readUrlInBrowser = false }
        }
    }

    private fun finishMenuOut() {
        if (menuVisible) return
        visibility = View.GONE
        canShowMenu = false
        isMenuOutAnimating = false
        callBack.upSystemUiVisibility()
        onMenuOutEnd?.invoke()
        onMenuOutEnd = null
    }

    private fun brightnessAuto(): Boolean =
        context.getPrefBoolean("brightnessAuto", true) ||
            !context.getPrefBoolean(PreferKey.showBrightnessView, true)

    @SuppressLint("ObsoleteSdkInt")
    fun setScreenBrightness(value: Float) {
        val hostActivity = activity ?: return
        fun setBrightness(brightness: Float) {
            hostActivity.window.attributes = hostActivity.window.attributes.apply {
                screenBrightness = brightness
            }
        }
        if (brightnessAuto() || value == BRIGHTNESS_OVERRIDE_NONE) {
            setBrightness(BRIGHTNESS_OVERRIDE_NONE)
            return
        }
        val brightness = if (value < 1f) 0.004f else value / 255f
        val systemBrightness = getCurrentBrightness()
        if (brightness == 1f && systemBrightness == 255) {
            contentObserver = object : ContentObserver(buildMainHandler()) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (contentObserver == null || uri != Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)) return
                    val current = getCurrentBrightness()
                    when {
                        current < 200 -> {
                            setBrightness(brightness)
                            unregisterContentObserver()
                        }
                        current < 255 -> setBrightness(brightness)
                        else -> setBrightness(BRIGHTNESS_OVERRIDE_NONE)
                    }
                }
            }
            val uri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)
            context.contentResolver.registerContentObserver(uri, false, contentObserver!!)
            setBrightness(BRIGHTNESS_OVERRIDE_NONE)
        } else {
            setBrightness(brightness)
        }
    }

    private fun getCurrentBrightness(): Int = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (_: Settings.SettingNotFoundException) {
        -1
    }

    private fun unregisterContentObserver() {
        contentObserver?.let(context.contentResolver::unregisterContentObserver)
        contentObserver = null
    }

    override fun onDetachedFromWindow() {
        unregisterContentObserver()
        super.onDetachedFromWindow()
    }

    private fun createInitialState() = ReadMenuUiState(
        background = androidx.compose.ui.graphics.Color(context.bottomBackground),
        foreground = androidx.compose.ui.graphics.Color(context.getPrimaryTextColor(true)),
        accent = androidx.compose.ui.graphics.Color(context.accentColor),
        showBrightness = context.getPrefBoolean(PreferKey.showBrightnessView, true),
        brightness = AppConfig.readBrightness.coerceIn(0, 255),
        brightnessOnStart = AppConfig.brightnessVwPos
    )

    interface CallBack {
        fun autoPage()
        fun openReplaceRule()
        fun openChapterList()
        fun openSearchActivity(searchWord: String?)
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun showReadStyle()
        fun showMoreSetting()
        fun showReadAloudDialog()
        fun upSystemUiVisibility()
        fun onClickReadAloud()
        fun showHelp()
        fun showLogin()
        fun payAction()
        fun disableSource()
        fun skipToChapter(index: Int)
        fun onMenuShow()
        fun onMenuHide()
    }
}
