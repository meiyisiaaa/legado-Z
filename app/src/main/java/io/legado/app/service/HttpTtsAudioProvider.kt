package io.legado.app.service

import com.script.ScriptException
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.HttpTTS
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.printOnDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Response
import org.mozilla.javascript.WrappedException
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Uses the app's existing HttpTTS URL/JS/login pipeline for role-specific synthesis. */
internal class HttpTtsAudioProvider {
    suspend fun getSpeakStream(httpTts: HttpTTS, speakText: String, speechRate: Int): java.io.InputStream? {
        var failureCount = 0
        while (true) {
            try {
                val analyzeUrl = AnalyzeUrl(
                    httpTts.url,
                    speakText = speakText,
                    speakSpeed = speechRate,
                    source = httpTts,
                    readTimeout = 300 * 1000L,
                    coroutineContext = currentCoroutineContext()
                )
                val checkJs = httpTts.loginCheckJs
                val response = runCatching {
                    analyzeUrl.getResponseAwait().let {
                        currentCoroutineContext().ensureActive()
                        if (!checkJs.isNullOrBlank()) analyzeUrl.evalJS(checkJs, it) as Response else it
                    }
                }.getOrElse { throwable ->
                    currentCoroutineContext().ensureActive()
                    if (checkJs.isNullOrBlank()) throw throwable
                    val errorResponse = analyzeUrl.getErrResponse(throwable)
                    try {
                        (analyzeUrl.evalJS(checkJs, errorResponse) as Response).also {
                            if (it.code == 500) throw throwable
                        }
                    } catch (_: Throwable) {
                        throw throwable
                    }
                }

                response.headers["Content-Type"]?.let { rawType ->
                    val responseType = rawType.substringBefore(";")
                    val expectedType = httpTts.contentType
                    if (responseType == "application/json" || responseType.startsWith("text/")) {
                        throw NoStackTraceException(response.body.string())
                    }
                    if (!expectedType.isNullOrBlank() && !responseType.matches(expectedType.toRegex())) {
                        throw NoStackTraceException("TTS服务器返回错误：${response.body.string()}")
                    }
                }
                currentCoroutineContext().ensureActive()
                return response.body.byteStream()
            } catch (error: Exception) {
                when (error) {
                    is CancellationException -> throw error
                    is ScriptException, is WrappedException -> {
                        AppLog.put("js错误\n${error.localizedMessage}", error, true)
                        error.printOnDebug()
                        throw error
                    }

                    is SocketTimeoutException, is ConnectException -> {
                        failureCount++
                        if (failureCount > MAX_RETRIES) {
                            AppLog.put("tts超时或连接错误超过5次\n${error.localizedMessage}", error, true)
                            throw error
                        }
                    }

                    else -> {
                        failureCount++
                        AppLog.put("tts下载错误\n${error.localizedMessage}", error)
                        error.printOnDebug()
                        if (failureCount > MAX_RETRIES) throw error
                        AppLog.put("TTS下载音频出错，使用无声音频代替。")
                        return null
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 5
    }
}
