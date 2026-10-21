package io.legado.app.ui.book.audio

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityAudioPlayBinding
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import io.legado.app.ui.book.audio.config.AudioSkipCredits
import com.dirror.lyricviewx.OnPlayClickListener
import io.legado.app.lib.theme.ThemeStore.Companion.accentColor
import io.legado.app.ui.book.audio.SliderPopup.Companion.SPEED
import io.legado.app.ui.book.audio.SliderPopup.Companion.TIMER
import io.legado.app.model.SourceCallBack
import io.legado.app.utils.gone

/**
 * 音频播放
 */
@SuppressLint("ObsoleteSdkInt")
class AudioPlayActivity :
    VMBaseActivity<ActivityAudioPlayBinding, AudioPlayViewModel>(toolBarTheme = Theme.Dark),
    ChangeBookSourceDialog.CallBack,
    AudioPlay.CallBack {

    override val binding by viewBinding(ActivityAudioPlayBinding::inflate)
    override val viewModel by viewModels<AudioPlayViewModel>()
    private val timerSliderPopup by lazy { SliderPopup(this, TIMER) }
    private val speedControlPopup by lazy { SliderPopup(this, SPEED) }
    private val playerUiState = mutableStateOf(
        AudioPlayerUiState(
            modeIcon = AudioPlay.playMode.iconRes,
            showSpeed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        )
    )
    private var adjustProgress = false
    private var playMode = AudioPlay.PlayMode.LIST_END_STOP
    private val lyricViewX by lazy { binding.lyricViewX }
    private var lyricOn = false
    private var oldLyric: String? = null
    private var menuCustomBtn: MenuItem? = null

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            if (it[0] != AudioPlay.book?.durChapterIndex
                || it[1] == 0
            ) {
                AudioPlay.skipTo(it[0] as Int)
            }
        }
    }
    private val sourceEditResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upSource()
            }
        }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.setBackgroundResource(R.color.transparent)
        AudioPlay.register(this)
        viewModel.titleData.observe(this) { name ->
            binding.titleBar.title = name
            val lyric = AudioPlay.durChapter?.getVariable("lyric")?.takeIf { it.isNotBlank() }
            upLyric(lyric ?: AudioPlay.durLyric)
        }
        viewModel.coverData.observe(this) {
            upCover(it)
        }
        viewModel.customBtnListData.observe(this) { menuCustomBtn?.isVisible = it }
        viewModel.initData(intent) {}
        initView()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.audio_play, menu)
        menuCustomBtn = menu.findItem(R.id.menu_custom_btn)?.also {
            it.isVisible = viewModel.customBtnListData.value == true
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_wake_lock)?.isChecked = AppConfig.audioPlayUseWakeLock
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_custom_btn -> {
                AudioPlay.bookSource?.let {source ->
                    AudioPlay.book?.let { book ->
                        SourceCallBack.callBackBtn(
                            this,
                            SourceCallBack.CLICK_CUSTOM_BUTTON,
                            source,
                            book,
                            AudioPlay.durChapter,
                            BookType.audio
                        )
                    }
                }
            }
            R.id.menu_change_source -> AudioPlay.book?.let {
                showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
            }

            R.id.menu_login -> AudioPlay.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("bookType", BookType.audio)
                }
            }

            R.id.menu_wake_lock -> AppConfig.audioPlayUseWakeLock = !AppConfig.audioPlayUseWakeLock
            R.id.menu_copy_audio_url -> {
                AudioPlay.book?.let {
                    val url = AudioPlayService.url
                    SourceCallBack.callBackBtn(
                        this,
                        SourceCallBack.CLICK_COPY_PLAY_URL,
                        AudioPlay.bookSource,
                        it,
                        AudioPlay.durChapter,
                        BookType.audio,
                        url
                    ) {
                        sendToClip(url)
                    }
                }
            }
            R.id.menu_edit_source -> AudioPlay.bookSource?.let {
                sourceEditResult.launch {
                    putExtra("sourceUrl", it.bookSourceUrl)
                }
            }

            /* 跳过片头片尾设定按钮 */
            R.id.menu_skip_credits -> AudioPlay.book?.let {
                showDialogFragment(AudioSkipCredits.newInstance(it))
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.audioControls.setContent {
            LegadoTheme {
                AudioPlayerContent(
                    state = playerUiState.value,
                    onAction = ::onPlayerAction,
                    onSeekChange = { progress ->
                        adjustProgress = true
                        playerUiState.value = playerUiState.value.copy(currentProgress = progress)
                    },
                    onSeekFinished = {
                        adjustProgress = false
                        AudioPlay.adjustProgress(playerUiState.value.currentProgress)
                    }
                )
            }
        }
        updatePlaybackIndicators()
        observeEventSticky<AudioPlay.PlayMode>(EventBus.PLAY_MODE_CHANGED) {
            playMode = it
            updatePlayModeIcon()
        }
    }

    private fun onPlayerAction(action: AudioPlayerAction) {
        when (action) {
            AudioPlayerAction.Timer -> timerSliderPopup.showAtLocation(binding.audioControls, Gravity.BOTTOM, 0, 0)
            AudioPlayerAction.Speed -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                speedControlPopup.showAtLocation(binding.audioControls, Gravity.BOTTOM, 0, 0)
            }
            AudioPlayerAction.Previous -> AudioPlay.prev()
            AudioPlayerAction.Play -> playButton()
            AudioPlayerAction.Stop -> AudioPlay.stop()
            AudioPlayerAction.Next -> AudioPlay.next()
            AudioPlayerAction.Mode -> AudioPlay.changePlayMode()
            AudioPlayerAction.Chapters -> AudioPlay.book?.let { tocActivityResult.launch(it.bookUrl) }
        }
    }

    private fun updatePlayModeIcon() {
        playerUiState.value = playerUiState.value.copy(modeIcon = playMode.iconRes)
    }

    private fun updatePlaybackIndicators() {
        val state = playerUiState.value
        binding.tvTimer.apply {
            text = state.timer.orEmpty()
            visible(state.timer != null)
        }
        binding.tvSpeed.apply {
            text = state.speed.orEmpty()
            visible(state.showSpeed && state.speed != null)
        }
    }

    private fun upCover(path: String?) {
        BookCover.load(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl) {
            BookCover.loadBlur(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl)
                .into(binding.ivBg)
        }.into(binding.ivCover)
    }

    override fun upLyric(lyric: String?) {
        if (oldLyric == lyric) return
        oldLyric = lyric
        if(lyric.isNullOrBlank()) {
            binding.lyricViewX.gone()
            return
        }
        lyricViewX.loadLyric(lyric)
        binding.lyricViewX.visible()
        if (lyricOn) {
            upLyricP(AudioPlay.durChapterPos)
        } else {
            lyricOn = true
            lyricViewX.apply {
                setNormalTextSize(50F)
                setCurrentTextSize(60F)
                setTimelineTextColor(accentColor)
                setDraggable(true, object : OnPlayClickListener {
                    override fun onPlayClick(time: Long): Boolean {
                        AudioPlay.adjustProgress(time.toInt())
                        playButton(false)
                        return true
                    }
                })
            }
            lyricViewX.postDelayed({
                upLyricP(AudioPlay.durChapterPos)
            }, 100)
        }
    }
    override fun upLyricP(position: Int) {
        lyricViewX.updateTime(position.toLong(),false)
    }

    private fun playButton(noLyr: Boolean = true) {
        val status = AudioPlay.status
        when (status) {
            Status.PLAY if noLyr -> {
                AudioPlay.pause(this)
            }
            Status.PAUSE -> {
                AudioPlay.resume(this)
            }
            else -> {
                AudioPlay.loadOrUpPlayUrl()
            }
        }
    }

    override val oldBook: Book?
        get() = AudioPlay.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isAudio) {
            viewModel.changeTo(source, book, toc)
        } else {
            AudioPlay.stop()
            lifecycleScope.launch {
                withContext(IO) {
                    AudioPlay.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    AudioPlay.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun finish() {
        val book = AudioPlay.book ?: return super.finish()
        if (AudioPlay.inBookshelf) {
            callBackBookEnd()
            return super.finish()
        }
        if (!AppConfig.showAddToShelfAlert) {
            callBackBookEnd()
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    AudioPlay.book?.removeType(BookType.notShelf)
                    AudioPlay.book?.save()
                    SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, AudioPlay.bookSource, AudioPlay.book)
                    AudioPlay.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton {
                    callBackBookEnd()
                    viewModel.removeFromBookshelf { super.finish() }
                }
            }
        }
    }

    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(SourceCallBack.END_READ, AudioPlay.bookSource, AudioPlay.book, AudioPlay.durChapter)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AudioPlay.status != Status.PLAY) {
            AudioPlay.stop()
        }
        AudioPlay.unregister(this)
    }

    @SuppressLint("SetTextI18n")
    override fun observeLiveBus() {
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                playButton()
            }
        }
        observeEventSticky<Int>(EventBus.AUDIO_STATE) {
            AudioPlay.status = it
            playerUiState.value = playerUiState.value.copy(isPlaying = it == Status.PLAY)
        }
        observeEventSticky<String>(EventBus.AUDIO_SUB_TITLE) {
            playerUiState.value = playerUiState.value.copy(
                subtitle = it,
                canPrevious = AudioPlay.durChapterIndex > 0,
                canNext = AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1
            )
        }
        observeEventSticky<Int>(EventBus.AUDIO_SIZE) {
            playerUiState.value = playerUiState.value.copy(duration = it)
        }
        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) {
            if (!adjustProgress) playerUiState.value = playerUiState.value.copy(currentProgress = it)
        }
        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
            playerUiState.value = playerUiState.value.copy(buffered = it)
        }
        observeEventSticky<Float>(EventBus.AUDIO_SPEED) {
            playerUiState.value = playerUiState.value.copy(
                speed = if (it == 1f) null else String.format(Locale.ROOT, "%.1fX", it)
            )
            updatePlaybackIndicators()
        }
        observeEventSticky<Int>(EventBus.AUDIO_DS) {
            playerUiState.value = playerUiState.value.copy(
                timer = it.takeIf { minutes -> minutes > 0 }?.let { minutes -> "${minutes}m" }
            )
            updatePlaybackIndicators()
        }
    }

    override fun upLoading(loading: Boolean) {
        runOnUiThread {
            playerUiState.value = playerUiState.value.copy(loading = loading)
        }
    }
}
