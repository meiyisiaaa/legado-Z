package io.legado.app.help.gsyVideo

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.shuyu.gsyvideoplayer.listener.LockClickListener
import com.shuyu.gsyvideoplayer.utils.CommonUtil
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer
import io.legado.app.R
import io.legado.app.model.VideoPlay
import io.legado.app.ui.widget.compose.LegadoTheme
import master.flame.danmaku.controller.DrawHandler
import master.flame.danmaku.danmaku.loader.IllegalDataException
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.SpannedCacheStuffer
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser
import master.flame.danmaku.ui.widget.DanmakuView
import java.io.File
import java.io.FileInputStream
import io.legado.app.utils.LogUtils
private const val TAG = "VideoPlayer"

class VideoPlayer: StandardGSYVideoPlayer {
    constructor(context: Context?, fullFlag: Boolean?) : super(context, fullFlag) //必须的,全屏时依靠这个构建知道获取全屏布局
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    private var playSpeed: Float = 1.0f
    private var isChanging = false
    private var isLongPressSpeed = false
    private var actionHasEpisodes by mutableStateOf(false)
    private var actionHasDanmaku by mutableStateOf(false)
    private var actionDanmakuLabel by mutableStateOf("开弹幕")
    private var actionSpeedLabel by mutableStateOf("倍速")
    private var actionPositionMs by mutableStateOf(0L)
    private var actionDurationMs by mutableStateOf(0L)
    private var actionProgress by mutableStateOf(0f)
    private var actionIsPlaying by mutableStateOf(false)
    private var actionIsScrubbing = false
    private var errorVisible by mutableStateOf(false)
    private var overlayTip by mutableStateOf<String?>(null)
    private var overlayTipGeneration = 0
    private var overlayComposeView: ComposeView? = null
    private var overlayStateInitialized = false
    private var startVisibilityBeforeOverlay: Int? = null

    private var mParser: BaseDanmakuParser? = null //解析器对象
    private var mDanmakuView: DanmakuView? = null //弹幕view
    private var mDanmakuContext: DanmakuContext? = null
    private var mDanmakuStartSeekPosition: Long = -1

    init {
        // GSY calls init from its constructor, before this class's Compose state exists.
        initView()
    }

    override fun getLayoutId(): Int {
        return if (mIfCurrentIsFullscreen)
            R.layout.video_layout_controller_full
        else R.layout.video_layout_controller
    }

    override fun getFullWindowPlayer(): VideoPlayer? {
        val activity = CommonUtil.scanForActivity(context) ?: return null
        val vp = activity.findViewById<View?>(Window.ID_ANDROID_CONTENT) as ViewGroup
        val full = vp.findViewById<View?>(fullId)
        var gsyVideoPlayer: VideoPlayer? = null
        if (full != null) {
            gsyVideoPlayer = full as VideoPlayer
        }
        return gsyVideoPlayer
    }
    override fun getSmallWindowPlayer(): VideoPlayer? = null

    override fun getCurrentPlayer(): VideoPlayer {
        val fullVideoPlayer = getFullWindowPlayer()
        if (fullVideoPlayer != null) {
            return fullVideoPlayer
        }
        val smallVideoPlayer = getSmallWindowPlayer()
        if (smallVideoPlayer != null) {
            return smallVideoPlayer
        }
        return this
    }

    fun getLockCurScreen() = mLockCurScreen

    public override fun lockTouchLogic() = super.lockTouchLogic()

    override fun init(context: Context) {
        super.init(context)
        post {
            gestureDetector = GestureDetector(
                getContext().applicationContext,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        touchDoubleUp(e)
                        return super.onDoubleTap(e)
                    }

                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        if (!mChangePosition && !mChangeVolume && !mBrightness && mCurrentState != CURRENT_STATE_ERROR
                        ) {
                            onClickUiToggle(e)
                        }
                        return super.onSingleTapConfirmed(e)
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (mCurrentState == CURRENT_STATE_PLAYING) {
                            val speed = VideoPlay.longPressSpeed / 10.0f
                            setVideoSpeed(speed)
                            showOverlayTip("${speed}倍速播放中")
                            isLongPressSpeed = true
                        }
                        super.onLongPress(e)
                    }
                }
            )
            mLockClickListener = LockClickListener { view, lock ->
                VideoPlay.lockCurScreen = lock
            }
        }
    }
    override fun touchSurfaceUp(){
        if (isLongPressSpeed) {
            isLongPressSpeed = false
            setVideoSpeed(playSpeed)
            showOverlayTip()
            val time = getCurrentPositionWhenPlaying()
            resolveDanmakuStart(time)
        }
        super.touchSurfaceUp()
    }

    private fun setVideoSpeed(speed: Float) {
        setSpeed(speed, true)
        if (mDanmakuView != null&& !mDanmakuView!!.isPaused) {
            mDanmakuContext!!.setScrollSpeedFactor(VideoPlay.danmakuSpeed - (speed - 1f) / 6f)
            mDanmakuView!!.invalidate()
        }
    }

    override fun onPrepared() {
        super.onPrepared()
        errorVisible = false
        syncOverlayVisibility()
        refreshPlaybackState()
        onPrepareDanmaku(this)
    }
    private fun onPrepareDanmaku(gsyVideoPlayer: VideoPlayer) {
        val view = gsyVideoPlayer.mDanmakuView
        val par = gsyVideoPlayer.mParser
        val con = gsyVideoPlayer.mDanmakuContext
        if ( view != null && !view.isPrepared && par != null) {
            view.prepare(par, con)
        }
    }

    override fun onVideoPause() {
        super.onVideoPause()
        actionIsPlaying = false
        danmakuOnPause()
    }
    fun danmakuOnPause() {
        if (mDanmakuView != null && mDanmakuView!!.isPrepared) {
            mDanmakuView!!.pause()
        }
    }

    override fun onVideoResume(isResume: Boolean) {
        super.onVideoResume(isResume)
        actionIsPlaying = true
        danmakuOnResume()
    }
    fun danmakuOnResume() {
        if (mDanmakuView != null && mDanmakuView!!.isPrepared && mDanmakuView!!.isPaused) {
            mDanmakuView!!.resume()
        }
    }

    override fun clickStartIcon() {
        errorVisible = false
        syncOverlayVisibility()
        super.clickStartIcon()
        actionIsPlaying = mCurrentState == CURRENT_STATE_PLAYING
        if (mCurrentState == CURRENT_STATE_PLAYING) {
            danmakuOnResume()
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {
            danmakuOnPause()
        }
    }

    override fun onAutoCompletion() { //播放完成
        super.onAutoCompletion()
        actionIsPlaying = false
        VideoPlay.upDurIndex(1, this)
    }

    override fun onCompletion() {
        super.onCompletion()
        actionIsPlaying = false
        releaseDanmaku(this)
    }
    fun releaseDanmaku(gsyVideoPlayer: VideoPlayer) {
        gsyVideoPlayer.mDanmakuView?.release()
    }


    override fun onSeekComplete() {
        super.onSeekComplete()
        actionDurationMs = getDuration().coerceAtLeast(0L)
        actionPositionMs = getCurrentPositionWhenPlaying().coerceAtLeast(0L)
        val time = actionPositionMs
        actionProgress = if (actionDurationMs > 0L) {
            (actionPositionMs.toFloat() / actionDurationMs * 100f).coerceIn(0f, 100f)
        } else {
            mProgressBar.progress.toFloat().coerceIn(0f, 100f)
        }
        actionIsScrubbing = false
        //如果已经初始化过的，直接seek到对于位置
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            resolveDanmakuSeek(time)
        } else if (mHadPlay && mDanmakuView != null && !mDanmakuView!!.isPrepared) {
            //如果没有初始化过的，记录位置等待
            mDanmakuStartSeekPosition = time
        }
    }


    fun showOverlayTip(message: String? = null, delay: Long = 0) {
        val generation = ++overlayTipGeneration
        overlayTip = message
        syncOverlayVisibility()
        if (message != null && delay > 0) {
            postDelayed({
                if (overlayTipGeneration == generation) {
                    overlayTip = null
                    syncOverlayVisibility()
                }
            }, delay)
        }
    }

    private fun initView() {
        isNeedLockFull = true //使用锁定按钮
        findViewById<ComposeView>(R.id.compose_actions)?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                LegadoTheme {
                    VideoPlayerActions(
                        isFullscreen = mIfCurrentIsFullscreen,
                        showEpisodes = actionHasEpisodes,
                        showDanmaku = actionHasDanmaku,
                        danmakuLabel = actionDanmakuLabel,
                        speedLabel = actionSpeedLabel,
                        currentPositionMs = if (actionIsScrubbing && actionDurationMs > 0L) {
                            (actionDurationMs * (actionProgress / 100f)).toLong().coerceIn(0L, actionDurationMs)
                        } else {
                            actionPositionMs
                        },
                        durationMs = actionDurationMs,
                        progress = actionProgress,
                        isPlaying = actionIsPlaying,
                        onProgressChange = { value ->
                            actionProgress = value
                            actionIsScrubbing = true
                        },
                        onSeekFinished = ::seekFromCompose,
                        onPlayPause = ::clickStartIcon,
                        onNext = { if (mHadPlay) VideoPlay.upDurIndex(1, this@VideoPlayer) },
                        onEpisodeList = { if (mHadPlay && !isChanging) showEpisodeDialog() },
                        onSpeed = { if (mHadPlay && !isChanging) showSpeedDialog() },
                        onDanmaku = {
                            VideoPlay.danmakuShow = !VideoPlay.danmakuShow
                            resolveDanmakuShow()
                        }
                    )
                }
            }
        }
        findViewById<ComposeView>(R.id.compose_overlay)?.apply {
            overlayComposeView = this
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                LegadoTheme {
                    VideoPlayerOverlay(
                        errorVisible = errorVisible,
                        tip = overlayTip,
                        onRetry = { clickStartIcon() }
                    )
                }
            }
        }
        overlayStateInitialized = true
        syncOverlayVisibility()
        if (mIfCurrentIsFullscreen && !VideoPlay.fullBottomProgressBar) {
            mBottomProgressBar = null
        }
        refreshControllerActions()
    }

    fun refreshControllerActions() {
        actionHasEpisodes = !VideoPlay.episodes.isNullOrEmpty()
    }

    private fun syncOverlayVisibility() {
        val show = errorVisible || overlayTip != null
        overlayComposeView?.apply {
            visibility = if (show) View.VISIBLE else View.INVISIBLE
            isClickable = errorVisible
            isFocusable = errorVisible
        }
        if (!overlayStateInitialized) return

        val startButton = mStartButton ?: return
        if (show) {
            if (startVisibilityBeforeOverlay == null) {
                startVisibilityBeforeOverlay = startButton.visibility
            }
            startButton.visibility = View.INVISIBLE
        } else {
            startVisibilityBeforeOverlay?.let { startButton.visibility = it }
            startVisibilityBeforeOverlay = null
        }
    }

    override fun setViewShowState(view: View?, visibility: Int) {
        if (overlayStateInitialized && view === mStartButton && (errorVisible || overlayTip != null)) {
            startVisibilityBeforeOverlay = visibility
            super.setViewShowState(view, View.INVISIBLE)
        } else {
            super.setViewShowState(view, visibility)
        }
    }


    override fun setUp(url: String?, cacheWithPlay: Boolean, cachePath: File?, title: String?): Boolean {
        actionPositionMs = 0L
        actionDurationMs = 0L
        actionProgress = 0f
        actionIsPlaying = false
        actionIsScrubbing = false
        initDanmaku()
        return super.setUp(url, cacheWithPlay, cachePath, title)
    }

    override fun setProgressAndTime(
        progress: Long,
        secProgress: Long,
        currentTime: Long,
        totalTime: Long,
        forceChange: Boolean
    ) {
        super.setProgressAndTime(progress, secProgress, currentTime, totalTime, forceChange)
        actionDurationMs = totalTime.coerceAtLeast(0L)
        if (!actionIsScrubbing) {
            actionPositionMs = currentTime.coerceAtLeast(0L)
            actionProgress = progress.toFloat().coerceIn(0f, 100f)
        }
    }

    private fun refreshPlaybackState() {
        actionPositionMs = getCurrentPositionWhenPlaying().coerceAtLeast(0L)
        actionDurationMs = getDuration().coerceAtLeast(0L)
        actionProgress = if (actionDurationMs > 0L) {
            (actionPositionMs.toFloat() / actionDurationMs * 100f).coerceIn(0f, 100f)
        } else {
            0f
        }
        actionIsPlaying = mCurrentState == CURRENT_STATE_PLAYING
    }

    private fun seekFromCompose() {
        val duration = actionDurationMs
        if (duration <= 0L) return
        val target = (duration * (actionProgress / 100f)).toLong().coerceIn(0L, duration)
        actionPositionMs = target
        actionIsScrubbing = false
        seekTo(target)
    }

    private fun initDanmaku() {
        val danmakuFile = VideoPlay.danmakuFile
        val danmakuStr = VideoPlay.danmakuStr
        if (danmakuFile == null && danmakuStr.isNullOrBlank()) {
            actionHasDanmaku = false
            return
        }
        mDanmakuView = findViewById<DanmakuView>(R.id.danmaku_view)?.also {
            it.visibility = VISIBLE
        }
        //弹幕开关
        actionHasDanmaku = mDanmakuView != null
        if (mDanmakuView != null) {
            // 设置最大显示行数
            val maxLinesPair = HashMap<Int?, Int?>()
            maxLinesPair[BaseDanmaku.TYPE_SCROLL_RL] = 5 // 滚动弹幕最大显示5行
            // 设置是否禁止重叠
            val overlappingEnablePair = HashMap<Int?, Boolean?>()
            overlappingEnablePair[BaseDanmaku.TYPE_SCROLL_RL] = true
            overlappingEnablePair[BaseDanmaku.TYPE_FIX_TOP] = true
            val danmakuAdapter = DanmakuAdapter(mDanmakuView)
            mDanmakuContext = DanmakuContext.create() //初始化上下文
            mDanmakuContext!!.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f) //设置弹幕类型
                .setDuplicateMergingEnabled(false) //设置是否合并重复弹幕
                .setScrollSpeedFactor(VideoPlay.danmakuSpeed) //设置弹幕滚动速度
                .setScaleTextSize(1.0f) //设置弹幕字体大小
                .setCacheStuffer(SpannedCacheStuffer(), danmakuAdapter) //设置缓存绘制填充器 图文混排使用SpannedCacheStuffer
                .setMaximumLines(maxLinesPair) //设置最大行数
                .preventOverlapping(overlappingEnablePair) //设置是否禁止重叠
            mParser = createParser(danmakuFile, danmakuStr) //加载弹幕资源文件
            mDanmakuView!!.setCallback(object : DrawHandler.Callback {
                override fun updateTimer(timer: DanmakuTimer?) {}
                override fun drawingFinished() {}
                override fun danmakuShown(danmaku: BaseDanmaku?) {}
                override fun prepared() {
                    if (mDanmakuView != null) {
                        mDanmakuView!!.start()
                        if (mDanmakuStartSeekPosition != -1L) {
                            resolveDanmakuSeek(mDanmakuStartSeekPosition)
                            mDanmakuStartSeekPosition = -1L
                        }
                        resolveDanmakuShow()
                    }
                }
            })
            mDanmakuView!!.enableDanmakuDrawingCache(true)
        }
    }

    /**
     * 弹幕偏移
     */
    private fun resolveDanmakuSeek(time: Long) {
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            mDanmakuView!!.seekTo(time)
        }
    }

    private fun resolveDanmakuStart(time: Long) {
        if (mHadPlay && mDanmakuView != null && mDanmakuView!!.isPrepared) {
            mDanmakuView!!.seekTo(time)
        }
    }


    private fun resolveDanmakuShow() {
        actionDanmakuLabel = if (VideoPlay.danmakuShow) "关弹幕" else "开弹幕"
        val danmakuView = mDanmakuView ?: return
        post {
            if (VideoPlay.danmakuShow) {
                if (!danmakuView.isShown) danmakuView.show()
            } else {
                if (danmakuView.isShown) danmakuView.hide()
            }
        }
    }

    /**
     * 创建解析器对象，解析输入流
     *
     * @param stream
     * @return
     */
    private fun createParser(danmakuFile: File?, danmakuStr: String?): BaseDanmakuParser {
        val loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI)
        try {
            if (danmakuFile != null) {
                loader.load(FileInputStream(danmakuFile))
            } else if (danmakuStr != null) {
                if (danmakuStr.startsWith("http",true)) {
                    loader.load(danmakuStr)
                } else {
                    loader.load(danmakuStr.byteInputStream())
                }
            }
        } catch (e: IllegalDataException) {
            LogUtils.e(TAG, e.stackTraceToString())
        }
        val parser: BaseDanmakuParser = BiliDanmukuParser()
        val dataSource = loader.dataSource
        parser.load(dataSource)
        return parser
    }

    private fun showEpisodeDialog() {
        if (!mHadPlay || VideoPlay.episodes.isNullOrEmpty()) {
            return
        }
        isChanging = true
        val choiceEpisodeDialog = ChoiceEpisodeDialog(mContext)
        choiceEpisodeDialog.initList(VideoPlay.episodes!!, object :
            ChoiceEpisodeDialog.OnListItemClickListener {
            override fun onItemClick(position: Int) {
                VideoPlay.chapterInVolumeIndex = position
                VideoPlay.saveRead(0)
                VideoPlay.startPlay(this@VideoPlayer)
            }

            override fun finishDialog() {
                isChanging = false
            }
        }, VideoPlay.chapterInVolumeIndex)
        choiceEpisodeDialog.show()
    }

    private fun showSpeedDialog() {
        if (!mHadPlay) {
            return
        }
        isChanging = true
        val choiceSpeedDialog = ChoiceSpeedDialog(mContext)
        choiceSpeedDialog.initList(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f).reversed(), object :
            ChoiceSpeedDialog.OnListItemClickListener {
            @SuppressLint("SetTextI18n")
            override fun onItemClick(value: Float) {
                playSpeed = value
                setSpeed(playSpeed, true)
                if (playSpeed != 1.0f) {
                    actionSpeedLabel = "${playSpeed}X"
                    showOverlayTip("${playSpeed}倍播放中", 2000)
                } else {
                    actionSpeedLabel = "倍速"
                }
            }

            override fun finishDialog() {
                isChanging = false
            }
        })
        choiceSpeedDialog.show()
    }

    override fun updateStartImage() {
        actionIsPlaying = mCurrentState == CURRENT_STATE_PLAYING
        if (mIfCurrentIsFullscreen) {
            if (mStartButton is ImageView) {
                val imageView = mStartButton as ImageView
                when (mCurrentState) {
                    CURRENT_STATE_PLAYING -> {
                        imageView.setImageResource(R.drawable.ic_pause_24dp)
                    }
                    CURRENT_STATE_ERROR -> {
                        imageView.setImageResource(R.drawable.ic_pause_outline_24dp)
                    }
                    else -> {
                        imageView.setImageResource(R.drawable.ic_play_24dp)
                    }
                }
            }
        } else {
            super.updateStartImage()
        }
    }

    override fun onError(what: Int, extra: Int) {
        super.onError(what, extra)
        errorVisible = true
        actionIsPlaying = false
        syncOverlayVisibility()
        VideoPlay.saveRead()
        mSeekOnStart = VideoPlay.durChapterPos.toLong()
    }


    /**
     * 处理播放器在全屏切换时，弹幕显示的逻辑
     * 需要格外注意的是，因为全屏和小屏，是切换了播放器，所以需要同步之间的弹幕状态
     */
    override fun startWindowFullscreen(
        context: Context?,
        actionBar: Boolean,
        statusBar: Boolean
    ): VideoPlayer? {
        val gsyBaseVideoPlayer = super.startWindowFullscreen(context, actionBar, statusBar)
        if (gsyBaseVideoPlayer != null) {
            val gsyVideoPlayer = gsyBaseVideoPlayer as VideoPlayer
            //对弹幕设置偏移记录
//            gsyVideoPlayer.mDanmakuView = this.mDanmakuView
            gsyVideoPlayer.mDanmakuStartSeekPosition = this.getCurrentPositionWhenPlaying()
            onPrepareDanmaku(gsyVideoPlayer)
        }
        return gsyBaseVideoPlayer
    }

    /**
     * 处理播放器在退出全屏时，弹幕显示的逻辑
     * 需要格外注意的是，因为全屏和小屏，是切换了播放器，所以需要同步之间的弹幕状态
     */
    override fun resolveNormalVideoShow(
        oldF: View?,
        vp: ViewGroup?,
        gsyVideoPlayer: GSYVideoPlayer?
    ) {
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer)
        if (gsyVideoPlayer != null) {
            val videoPlayer = gsyVideoPlayer as VideoPlayer
            if (mDanmakuView != null && mDanmakuView!!.isPrepared) {
                resolveDanmakuSeek(videoPlayer.getCurrentPositionWhenPlaying())
                resolveDanmakuShow()
                releaseDanmaku(videoPlayer)
            }
        }
    }

    override fun release() {
        super.release()
        releaseDanmaku(this)
    }

    /**********以下重载GSYVideoPlayer的GSYVideoViewBridge相关实现***********/
    override fun getGSYVideoManager(): ExoVideoManager {
        return VideoPlay.videoManager.apply { initContext(context.applicationContext) }
    }
    public override fun backFromFull(context: Context?): Boolean {
        return VideoPlay.backFromWindowFull(context)
    }
    override fun releaseVideos() {
        VideoPlay.releaseAllVideos()
    }

    override fun getFullId(): Int {
        return ExoVideoManager.FULLSCREEN_ID
    }

    override fun getSmallId(): Int {
        return ExoVideoManager.SMALL_ID
    }
    override fun setDisplay(surface: Surface?) {
        if (surface != null && mTextureView.getShowView() is SurfaceView) {
            val surfaceView = (mTextureView.getShowView() as SurfaceView?)
            gsyVideoManager.setDisplayNew(surfaceView)
        } else if (surface != null) {
            gsyVideoManager.setDisplay(surface)
        } else {
            gsyVideoManager.setDisplayNew(null)
        }
    }
    fun nextUI() { resetProgressAndTime() }


    //播放器转移
    fun setSurfaceToPlay() {
        addTextureView()
        gsyVideoManager.setListener(this)
        checkoutState()
    }

    var needDestroy: Boolean = true
    override fun onSurfaceDestroyed(surface: Surface?): Boolean {
        if (needDestroy) {
            return super.onSurfaceDestroyed(surface)
        } else {
            releaseSurface(surface)
            needDestroy = true
            return true
        }
    }

    fun saveState(): VideoPlayer {
        return this
    }

    fun cloneState(switchVideo: StandardGSYVideoPlayer) {
        cloneParams(switchVideo, this)
    }
}
