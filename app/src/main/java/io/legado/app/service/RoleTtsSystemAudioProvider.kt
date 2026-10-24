package io.legado.app.service

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RoleTtsSystemVoice(
    val engineId: String,
    val voiceName: String,
    val localeTag: String,
    val networkConnectionRequired: Boolean
)

data class RoleTtsSystemEngine(val id: String, val label: String)

/** Enumerates installed voices of the selected system engine for the role-binding UI. */
object RoleTtsSystemVoiceCatalog {
    suspend fun enumerateEngines(context: Context): List<RoleTtsSystemEngine> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var instance: TextToSpeech? = null
                val listener = TextToSpeech.OnInitListener { status ->
                    val tts = instance ?: return@OnInitListener
                    if (!continuation.isActive) {
                        tts.shutdown()
                    } else if (status == TextToSpeech.SUCCESS) {
                        val engines = tts.engines.orEmpty().map { engine ->
                            RoleTtsSystemEngine(engine.name, engine.label.toString())
                        }
                        tts.shutdown()
                        continuation.resume(engines)
                    } else {
                        tts.shutdown()
                        continuation.resume(emptyList())
                    }
                }
                instance = TextToSpeech(context.applicationContext, listener)
                continuation.invokeOnCancellation { instance?.shutdown() }
            }
        }

    suspend fun enumerate(context: Context, requestedEngineId: String?): List<RoleTtsSystemVoice> =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                var instance: TextToSpeech? = null
                val listener = TextToSpeech.OnInitListener { status ->
                    val tts = instance ?: return@OnInitListener
                    if (!continuation.isActive) {
                        tts.shutdown()
                    } else if (status == TextToSpeech.SUCCESS) {
                        val engineId = requestedEngineId ?: tts.defaultEngine.orEmpty()
                        val voices = tts.voices.orEmpty().map { voice ->
                            voice.toRoleTtsVoice(engineId)
                        }
                        tts.shutdown()
                        continuation.resume(voices)
                    } else {
                        tts.shutdown()
                        continuation.resume(emptyList())
                    }
                }
                instance = if (requestedEngineId.isNullOrBlank()) {
                    TextToSpeech(context.applicationContext, listener)
                } else {
                    TextToSpeech(context.applicationContext, listener, requestedEngineId)
                }
                continuation.invokeOnCancellation { instance?.shutdown() }
            }
        }

    private fun Voice.toRoleTtsVoice(engineId: String) = RoleTtsSystemVoice(
        engineId = engineId,
        voiceName = name,
        localeTag = locale.toLanguageTag(),
        networkConnectionRequired = isNetworkConnectionRequired
    )
}

internal class RoleTtsVoiceUnavailableException(message: String) : IllegalStateException(message)

/** Sequential file synthesis using Android's existing installed system TTS engines. */
internal class RoleTtsSystemAudioProvider(context: Context) {
    private val appContext = context.applicationContext
    private val engines = ConcurrentHashMap<String, EngineSession>()

    suspend fun availableVoices(engineId: String?): Set<String> =
        session(engineId).availableVoiceNames()

    suspend fun resolvedEngineId(engineId: String?): String = session(engineId).engineId

    suspend fun synthesize(
        engineId: String?,
        voiceName: String?,
        text: String,
        rate: Float,
        outputFile: File,
        utteranceId: String
    ) {
        val engine = session(engineId)
        engine.synthesize(voiceName, text, rate, outputFile, utteranceId)
    }

    fun stop() {
        engines.values.forEach { it.stop() }
    }

    fun release() {
        engines.values.forEach { it.release() }
        engines.clear()
    }

    private suspend fun session(engineId: String?): EngineSession {
        val key = engineId?.takeIf { it.isNotBlank() } ?: DEFAULT_ENGINE_KEY
        val existing = engines[key]
        if (existing != null) {
            existing.awaitReady()
            return existing
        }
        val created = withContext(Dispatchers.Main.immediate) {
            EngineSession(appContext, engineId?.takeIf { it.isNotBlank() })
        }
        val engine = engines.putIfAbsent(key, created) ?: created
        if (engine !== created) created.release()
        engine.awaitReady()
        return engine
    }

    private class EngineSession(context: Context, requestedEngineId: String?) {
        private val ready = CompletableDeferred<Int>()
        private val pending = ConcurrentHashMap<String, CancellableContinuation<Unit>>()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val initialEngineId = requestedEngineId
        private val tts: TextToSpeech = TextToSpeech(
            context,
            TextToSpeech.OnInitListener { status -> ready.complete(status) },
            requestedEngineId
        )
        private val defaultVoice by lazy { tts.defaultVoice ?: tts.voice }
        val engineId: String
            get() = initialEngineId ?: tts.defaultEngine.orEmpty()

        init {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    pending.remove(utteranceId)?.let { continuation ->
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId != null) fail(
                        utteranceId,
                        RoleTtsVoiceUnavailableException("system TTS error $errorCode")
                    )
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    fail(utteranceId, RoleTtsVoiceUnavailableException("system TTS synthesis failed"))
                }
            })
        }

        suspend fun awaitReady() {
            val status = ready.await()
            if (status != TextToSpeech.SUCCESS) throw RoleTtsVoiceUnavailableException("system TTS engine unavailable")
        }

        suspend fun availableVoiceNames(): Set<String> {
            awaitReady()
            return withContext(Dispatchers.Main.immediate) { tts.voices.orEmpty().map { it.name }.toSet() }
        }

        suspend fun synthesize(
            voiceName: String?,
            text: String,
            rate: Float,
            outputFile: File,
            utteranceId: String
        ) {
            awaitReady()
            withContext(Dispatchers.Main.immediate) {
                // Capture the initialized engine default before selecting any role voice.
                val engineDefaultVoice = defaultVoice
                val voice = if (voiceName.isNullOrBlank()) {
                    engineDefaultVoice
                        ?: throw RoleTtsVoiceUnavailableException("system TTS default voice is unavailable")
                } else {
                    tts.voices.orEmpty().firstOrNull { it.name == voiceName }
                        ?: throw RoleTtsVoiceUnavailableException("system TTS voice is no longer available")
                }
                if (tts.setVoice(voice) != TextToSpeech.SUCCESS) {
                    throw RoleTtsVoiceUnavailableException("system TTS voice could not be selected")
                }
                tts.setSpeechRate(if (rate > 0f) rate else 1f)
                outputFile.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("cannot create TTS cache directory") }
                if (outputFile.exists()) outputFile.delete()
            }
            suspendCancellableCoroutine<Unit> { continuation ->
                pending[utteranceId] = continuation
                continuation.invokeOnCancellation {
                    pending.remove(utteranceId, continuation)
                    mainHandler.post { tts.stop() }
                    outputFile.delete()
                }
                mainHandler.post {
                    if (!continuation.isActive) return@post
                    val result = tts.synthesizeToFile(text, Bundle(), outputFile, utteranceId)
                    if (result == TextToSpeech.ERROR) {
                        fail(
                            utteranceId,
                            RoleTtsVoiceUnavailableException("system TTS could not synthesize the segment")
                        )
                    }
                }
            }
            if (!outputFile.isFile || outputFile.length() == 0L) {
                throw RoleTtsVoiceUnavailableException("system TTS returned an empty audio file")
            }
        }

        fun stop() {
            mainHandler.post { tts.stop() }
            pending.values.forEach { if (it.isActive) it.cancel() }
            pending.clear()
        }

        fun release() {
            stop()
            mainHandler.post { tts.shutdown() }
        }

        private fun fail(utteranceId: String, error: Throwable) {
            pending.remove(utteranceId)?.let { continuation ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    companion object {
        private const val DEFAULT_ENGINE_KEY = "__default_engine__"
    }
}
