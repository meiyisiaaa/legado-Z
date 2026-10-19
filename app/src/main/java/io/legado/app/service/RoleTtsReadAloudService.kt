package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
import io.legado.app.ai.AiConfig
import io.legado.app.ai.tts.RoleTtsRoleRegistry
import io.legado.app.ai.tts.RoleTtsRolesConfig
import io.legado.app.ai.tts.RoleTtsSegment
import io.legado.app.ai.tts.RoleTtsVoiceBinding
import io.legado.app.ai.tts.StoryboardEngine
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.tts.RoleTtsCacheManager
import io.legado.app.help.tts.RoleTtsConfigStore
import io.legado.app.help.tts.RoleTtsRouter
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.servicePendingIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Plays only already-cached storyboards. Missing or stale storyboards use ordinary narration. */
@SuppressLint("UnsafeOptInUsageError")
class RoleTtsReadAloudService : BaseReadAloudService(), Player.Listener {

    private val exoPlayer: ExoPlayer by lazy { ExoPlayer.Builder(this).build() }
    private val systemAudio: RoleTtsSystemAudioProvider by lazy { RoleTtsSystemAudioProvider(this) }
    private val httpAudio by lazy { HttpTtsAudioProvider() }
    private var prepareJob: Job? = null
    private var progressJob: Job? = null
    private var sessionId = 0L
    private var activeClip: PlaybackClip? = null

    override fun onCreate() {
        super.onCreate()
        exoPlayer.addListener(this)
    }

    override fun onDestroy() {
        stopCurrentSession()
        exoPlayer.release()
        systemAudio.release()
        super.onDestroy()
    }

    override fun useReadAloudByPage(): Boolean = false

    override fun play() {
        pageChanged = false
        playStop()
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            pauseReadAloud()
            return
        }
        val source = textChapter?.getNeedReadAloud(0, false, 0).orEmpty()
        if (source.isEmpty()) {
            pauseReadAloud()
            return
        }
        super.play()
        val token = sessionId
        prepareJob = lifecycleScope.launch(IO) {
            try {
                prepareChapter(token, source)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.put("分角色朗读准备失败，已暂停\n${error.localizedMessage}", error, true)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (token == sessionId) pauseReadAloud()
                }
            }
        }
    }

    override fun playStop() {
        stopCurrentSession()
    }

    private fun stopCurrentSession() {
        sessionId++
        prepareJob?.cancel()
        prepareJob = null
        progressJob?.cancel()
        progressJob = null
        activeClip = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        systemAudio.stop()
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        progressJob?.cancel()
        exoPlayer.pause()
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        exoPlayer.play()
        activeClip?.let(::startProgress)
    }

    override fun upSpeechRate(reset: Boolean) {
        activeClip?.let { clip ->
            readAloudNumber = currentSourcePosition(clip).coerceAtMost(clip.endExclusive)
            updateParagraphCursor(readAloudNumber)
        }
        playStop()
        play()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val clip = currentClip() ?: return
        if (clip.sessionId != sessionId) return
        when (playbackState) {
            Player.STATE_READY -> if (!BaseReadAloudService.pause) {
                exoPlayer.play()
                activeClip = clip
                updateParagraphCursor(clip.start)
                updateReadProgress(clip.start)
                startProgress(clip)
            }

            Player.STATE_ENDED -> {
                progressJob?.cancel()
                val sourceLength = textChapter?.getNeedReadAloud(0, false, 0)?.length ?: 0
                updateParagraphCursor(sourceLength)
                updateReadProgress(sourceLength)
                nextChapter()
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val clip = mediaItem?.localConfiguration?.tag as? PlaybackClip ?: return
        if (clip.sessionId != sessionId) return
        activeClip = clip
        updateParagraphCursor(clip.start)
        updateReadProgress(clip.start)
        if (!BaseReadAloudService.pause) startProgress(clip)
    }

    override fun onPlayerError(error: PlaybackException) {
        val clip = currentClip() ?: return
        if (clip.sessionId != sessionId) return
        AppLog.put("分角色朗读播放失败\n${error.localizedMessage}", error, true)
        pauseReadAloud()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? =
        servicePendingIntent<RoleTtsReadAloudService>(actionStr)

    private suspend fun prepareChapter(token: Long, source: String) {
        val book = ReadBook.book ?: throw NoStackTraceException("当前书籍不存在")
        val chapterIndex = textChapter?.chapter?.index ?: throw NoStackTraceException("当前章节不存在")
        val options = AiConfig.snapshot()
        val cache = RoleTtsCacheManager(book)
        val rolesConfig = RoleTtsConfigStore.load(book)
        val storyboard = cache.loadStoryboard(chapterIndex, source, options, rolesConfig.roles)
        val globalNarrator = loadGlobalNarratorBinding()
        val httpProfiles = appDb.httpTTSDao.all.associateBy { it.id }
        val validHttpIds = httpProfiles.keys
        val availableSystemVoices = collectAvailableSystemVoices(rolesConfig, globalNarrator)
        val narratorName = getString(R.string.role_tts_role_narrator)
        val narrator = RoleTtsRoleRegistry.findByNameOrAlias(rolesConfig, narratorName)
            ?.binding?.takeIf { isBindingValid(it, availableSystemVoices, validHttpIds) }
            ?: globalNarrator.takeIf { isBindingValid(it, availableSystemVoices, validHttpIds) }
            ?: RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.SYSTEM)

        val rawSegments = storyboard?.segments?.takeIf { it.isNotEmpty() }
            ?: ordinaryNarratorSegments(source, narratorRoleId(rolesConfig))
        val segments = splitForSynthesis(source, rawSegments)
        val slices = ArrayList<PlaybackClip>(segments.size)
        val startOffset = readAloudNumber.coerceIn(0, source.length)
        for (segment in segments) {
            currentCoroutineContext().ensureActive()
            if (segment.endExclusive <= startOffset) continue
            val start = maxOf(segment.start, startOffset)
            if (start >= segment.endExclusive) continue
            val speakerId = if (storyboard == null) narratorRoleId(rolesConfig) else segment.speakerId
            val routed = RoleTtsRouter.bindingFor(
                rolesConfig, speakerId, availableSystemVoices, validHttpIds, narrator
            ) ?: narrator
            val route = if (isBindingValid(routed, availableSystemVoices, validHttpIds) ||
                routed.provider == RoleTtsVoiceBinding.Provider.SYSTEM && routed.engineId.isNullOrBlank()
            ) routed else narrator
            val spokenText = source.substring(start, segment.endExclusive)
                .replace(AppPattern.notReadAloudRegex, "")
            if (spokenText.isBlank()) continue

            val extension = if (route.provider == RoleTtsVoiceBinding.Provider.SYSTEM) "wav" else "mp3"
            val fingerprint = storyboard?.requestFingerprint ?: "narrator-only-v1"
            val audioIdentity = routeAudioFingerprint(route, httpProfiles)
            val key = StoryboardEngine.audioCacheKey(
                fingerprint,
                StoryboardEngine.sha256(source),
                start,
                segment.endExclusive,
                audioIdentity.first,
                audioIdentity.second,
                audioIdentity.third
            )
            val cachedFile = cache.audioFile(key, extension)
            var audioFile = cachedFile
            if (!cachedFile.isFile || cachedFile.length() == 0L) {
                cachedFile.delete()
                try {
                    synthesizeCached(
                        cache, key, extension, route, httpProfiles, spokenText,
                        audioIdentity.third, "$token-$start-${segment.endExclusive}"
                    )
                } catch (error: RoleTtsVoiceUnavailableException) {
                    if (route == narrator) throw error
                    val fallback = narrator
                    val fallbackIdentity = routeAudioFingerprint(fallback, httpProfiles)
                    val fallbackExtension = if (fallback.provider == RoleTtsVoiceBinding.Provider.SYSTEM) "wav" else "mp3"
                    val fallbackKey = StoryboardEngine.audioCacheKey(
                        fingerprint,
                        StoryboardEngine.sha256(source),
                        start,
                        segment.endExclusive,
                        fallbackIdentity.first,
                        fallbackIdentity.second,
                        fallbackIdentity.third
                    )
                    audioFile = cache.audioFile(fallbackKey, fallbackExtension)
                    if (!audioFile.isFile || audioFile.length() == 0L) {
                        synthesizeCached(
                            cache, fallbackKey, fallbackExtension, fallback, httpProfiles,
                            spokenText, fallbackIdentity.third, "$token-fallback-$start"
                        )
                    }
                }
            }
            slices += PlaybackClip(start, segment.endExclusive, token, audioFile)
        }
        currentCoroutineContext().ensureActive()
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (token != sessionId) return@withContext
            if (slices.isEmpty()) {
                updateReadProgress(source.length)
                lifecycleScope.launch {
                    delay(400)
                    if (token == sessionId) nextChapter()
                }
                return@withContext
            }
            activeClip = null
            exoPlayer.clearMediaItems()
            slices.forEach { clip ->
                exoPlayer.addMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(clip.audioFile))
                        .setTag(clip)
                        .build()
                )
            }
            exoPlayer.prepare()
        }
    }

    private suspend fun synthesizeCached(
        cache: RoleTtsCacheManager,
        key: String,
        extension: String,
        binding: RoleTtsVoiceBinding,
        profiles: Map<Long, HttpTTS>,
        text: String,
        rate: Float,
        utteranceId: String
    ) {
        val temporary = cache.temporaryAudioFile(key, extension)
        try {
            synthesize(binding, profiles, text, rate, temporary, utteranceId)
            currentCoroutineContext().ensureActive()
            cache.commitAudioFile(key, extension)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private suspend fun collectAvailableSystemVoices(
        config: RoleTtsRolesConfig,
        narrator: RoleTtsVoiceBinding
    ): Set<String> {
        val engineIds = (config.roles.mapNotNull { role ->
            role.binding?.takeIf { it.provider == RoleTtsVoiceBinding.Provider.SYSTEM }?.engineId
        } + listOfNotNull(narrator.engineId.takeIf { narrator.provider == RoleTtsVoiceBinding.Provider.SYSTEM }))
            .filter(String::isNotBlank)
            .toSet()
        val voices = linkedSetOf<String>()
        for (engineId in engineIds) {
            runCatching { systemAudio.availableVoices(engineId) }.getOrDefault(emptySet())
                .forEach { voices += "$engineId\u0000$it" }
        }
        return voices
    }

    private fun ordinaryNarratorSegments(source: String, narratorId: String): List<RoleTtsSegment> {
        var cursor = 0
        return contentList.mapNotNull { paragraph ->
            val start = source.indexOf(paragraph, cursor)
            if (start < 0) return@mapNotNull null
            cursor = start + paragraph.length + 1
            RoleTtsSegment(start, start + paragraph.length, "narration", narratorId)
        }
    }

    private fun splitForSynthesis(source: String, segments: List<RoleTtsSegment>): List<RoleTtsSegment> {
        val result = ArrayList<RoleTtsSegment>()
        for (segment in segments) {
            var start = segment.start
            while (start < segment.endExclusive) {
                var end = minOf(start + MAX_SYNTHESIS_CHARS, segment.endExclusive)
                if (end < segment.endExclusive && isSurrogateBoundary(source, end)) end--
                if (end < segment.endExclusive) {
                    val lowerBound = start + MAX_SYNTHESIS_CHARS / 2
                    val boundary = (end downTo lowerBound.coerceAtMost(end))
                        .firstOrNull { offset -> offset > start && isSpeechBoundary(source, offset) }
                    if (boundary != null) end = boundary
                }
                if (end <= start) end = (start + 1).coerceAtMost(segment.endExclusive)
                result += segment.copy(start = start, endExclusive = end)
                start = end
            }
        }
        return result
    }

    private fun isSurrogateBoundary(source: String, offset: Int): Boolean =
        offset > 0 && offset < source.length && Character.isHighSurrogate(source[offset - 1]) &&
            Character.isLowSurrogate(source[offset])

    private fun isSpeechBoundary(source: String, offset: Int): Boolean {
        val previous = source[offset - 1]
        return previous in "。！？；.!?;\n"
    }

    private fun narratorRoleId(config: RoleTtsRolesConfig): String =
        RoleTtsRoleRegistry.findByNameOrAlias(config, getString(R.string.role_tts_role_narrator))?.id.orEmpty()

    private fun loadGlobalNarratorBinding(): RoleTtsVoiceBinding {
        val current = ReadAloud.ttsEngine
        if (current != null && StringUtils.isNumeric(current)) {
            val id = current!!.toLongOrNull()
            if (id != null && appDb.httpTTSDao.get(id) != null) {
                return RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.HTTP, httpTtsId = id)
            }
        }
        val selectedEngine = GSON.fromJsonObject<SelectItem<String>>(current).getOrNull()?.value
        return RoleTtsVoiceBinding(RoleTtsVoiceBinding.Provider.SYSTEM, engineId = selectedEngine)
    }

    private fun isBindingValid(
        binding: RoleTtsVoiceBinding,
        voices: Set<String>,
        httpIds: Set<Long>
    ): Boolean = when (binding.provider) {
        RoleTtsVoiceBinding.Provider.SYSTEM -> if (binding.engineId.isNullOrBlank()) {
            binding.voiceName.isNullOrBlank()
        } else if (binding.voiceName.isNullOrBlank()) {
            voices.any { it.startsWith("${binding.engineId}\u0000") }
        } else {
            "${binding.engineId}\u0000${binding.voiceName}" in voices
        }

        RoleTtsVoiceBinding.Provider.HTTP -> binding.httpTtsId?.let(httpIds::contains) == true
    }

    private fun routeAudioFingerprint(
        binding: RoleTtsVoiceBinding,
        profiles: Map<Long, HttpTTS>
    ): Triple<String, String, Float> = when (binding.provider) {
        RoleTtsVoiceBinding.Provider.SYSTEM -> {
            val rate = if (AppConfig.ttsFlowSys) 0f else (AppConfig.ttsSpeechRate + 5) / 10f
            val engine = binding.engineId.orEmpty()
            val voice = binding.voiceName.orEmpty()
            Triple(
                StoryboardEngine.sha256("system\u0000$engine\u0000$voice"),
                "$engine\u0000${voice.ifBlank { "default" }}",
                rate
            )
        }

        RoleTtsVoiceBinding.Provider.HTTP -> {
            val profile = binding.httpTtsId?.let(profiles::get)
                ?: throw RoleTtsVoiceUnavailableException("HTTP TTS 配置已删除")
            val rate = (AppConfig.speechRatePlay + 5).toFloat()
            Triple(
                StoryboardEngine.sha256(GSON.toJson(profile)),
                "http:${profile.id}",
                rate
            )
        }
    }

    private suspend fun synthesize(
        binding: RoleTtsVoiceBinding,
        profiles: Map<Long, HttpTTS>,
        text: String,
        rate: Float,
        output: File,
        utteranceId: String
    ) {
        output.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("cannot create TTS cache directory") }
        when (binding.provider) {
            RoleTtsVoiceBinding.Provider.SYSTEM -> {
                systemAudio.synthesize(
                    binding.engineId,
                    binding.voiceName,
                    text,
                    rate,
                    output,
                    utteranceId
                )
            }

            RoleTtsVoiceBinding.Provider.HTTP -> {
                val profile = binding.httpTtsId?.let(profiles::get)
                    ?: throw RoleTtsVoiceUnavailableException("HTTP TTS 配置已删除")
                val stream = httpAudio.getSpeakStream(profile, text, rate.toInt())
                if (stream == null) {
                    resources.openRawResource(R.raw.silent_sound).use { input ->
                        output.outputStream().use { destination -> input.copyTo(destination) }
                    }
                } else {
                    stream.use { input -> output.outputStream().use { destination -> input.copyTo(destination) } }
                }
                if (!output.isFile || output.length() == 0L) {
                    throw NoStackTraceException("HTTP TTS 返回空音频")
                }
            }
        }
    }

    private fun currentClip(): PlaybackClip? =
        exoPlayer.currentMediaItem?.localConfiguration?.tag as? PlaybackClip

    private fun currentSourcePosition(clip: PlaybackClip): Int {
        val duration = exoPlayer.duration
        if (duration <= 0) return clip.start
        val progress = (exoPlayer.currentPosition.coerceAtLeast(0) * 1f / duration).coerceIn(0f, 1f)
        return clip.start + ((clip.endExclusive - clip.start) * progress).toInt()
    }

    private fun startProgress(clip: PlaybackClip) {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive && clip.sessionId == sessionId && exoPlayer.isPlaying) {
                val position = currentSourcePosition(clip).coerceAtMost(clip.endExclusive)
                updateParagraphCursor(position)
                updateReadProgress(position)
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun updateParagraphCursor(position: Int) {
        val source = textChapter?.getNeedReadAloud(0, false, 0) ?: return
        var cursor = 0
        val paragraphRanges = contentList.mapIndexedNotNull { index, paragraph ->
            val start = source.indexOf(paragraph, cursor)
            if (start < 0) return@mapIndexedNotNull null
            cursor = start + paragraph.length + 1
            ParagraphRange(index, start, start + paragraph.length)
        }
        val paragraph = paragraphRanges.firstOrNull { position in it.start until it.endExclusive }
            ?: paragraphRanges.lastOrNull { position >= it.endExclusive }
            ?: return
        nowSpeak = paragraph.index
        readAloudNumber = position.coerceIn(paragraph.start, paragraph.endExclusive)
        paragraphStartPos = (readAloudNumber - paragraph.start).coerceAtLeast(0)
    }

    private fun updateReadProgress(position: Int) {
        val chapter = textChapter ?: return
        while (pageIndex + 1 < chapter.pageSize && position + 1 > chapter.getReadLength(pageIndex + 1)) {
            pageIndex++
            ReadBook.moveToNextPage()
        }
        while (pageIndex > 0 && position < chapter.getReadLength(pageIndex)) {
            pageIndex--
            ReadBook.moveToPrevPage()
        }
        upTtsProgress(position + 1)
    }

    private data class ParagraphRange(val index: Int, val start: Int, val endExclusive: Int)

    private data class PlaybackClip(
        val start: Int,
        val endExclusive: Int,
        val sessionId: Long,
        val audioFile: File
    )

    companion object {
        private const val MAX_SYNTHESIS_CHARS = 500
        private const val PROGRESS_INTERVAL_MS = 250L
    }
}
