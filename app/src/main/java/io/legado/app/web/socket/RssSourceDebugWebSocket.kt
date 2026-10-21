package io.legado.app.web.socket


import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.model.Debug
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunResult
import io.legado.app.model.DebugRunner
import io.legado.app.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import splitties.init.appCtx
import java.io.IOException
import java.util.UUID

/**
 * web端订阅源调试
 */
class RssSourceDebugWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) :
    NanoWSD.WebSocket(handshakeRequest),
    CoroutineScope by MainScope() {

    private val notPrintState = arrayOf(10, 20, 30, 40)
    private val runOwnerId = "rss-ws:${UUID.randomUUID()}"

    override fun onOpen() {
        launch(IO) {
            try {
                while (isOpen) {
                    ping("ping".toByteArray())
                    delay(30000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                closeNormally()
                this@RssSourceDebugWebSocket.cancel()
            }
        }
    }

    override fun onClose(
        code: NanoWSD.WebSocketFrame.CloseCode,
        reason: String,
        initiatedByRemote: Boolean
    ) {
        this@RssSourceDebugWebSocket.cancel()
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        launch(IO) {
            try {
                val payload = message.textPayload
                if (!payload.isJson()) {
                    sendOrCancel("数据必须为Json格式")
                    return@launch
                }
                val debugBean = GSON.fromJsonObject<Map<String, String>>(payload).getOrNull()
                if (debugBean == null) {
                    sendOrCancel("数据必须为Json格式")
                    return@launch
                }
                val tag = debugBean["tag"]
                if (tag.isNullOrBlank()) {
                    sendOrCancel(appCtx.getString(R.string.cannot_empty))
                    return@launch
                }
                val source = appDb.rssSourceDao.getByKey(tag)
                if (source == null) {
                    sendOrCancel("未找到订阅源：$tag")
                    return@launch
                }
                when (
                    val result = DebugRunner.run(
                        ownerId = runOwnerId,
                        kind = DebugRunKind.RSS_WEBSOCKET,
                        sourceUrl = source.sourceUrl,
                        onEvent = { event ->
                            if (event.state !in notPrintState) {
                                sendOrCancel(event.displayMessage)
                            }
                        }
                    ) {
                        Debug.startDebug(source)
                    }
                ) {
                    is DebugRunResult.Busy -> sendOrCancel("调试任务忙：${result.activeOwnerId}")
                    is DebugRunResult.Completed -> if (result.snapshot.terminalState == "failed") {
                        sendOrCancel(
                            "调试失败：${result.snapshot.error ?: "未知错误"}"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                sendOrCancel(
                    "调试失败：${failure.localizedMessage ?: failure.javaClass.simpleName}"
                )
            } finally {
                try {
                    closeNormally()
                } finally {
                    this@RssSourceDebugWebSocket.cancel()
                }
            }
        }
    }

    private fun sendOrCancel(message: String) {
        if (!isOpen) return
        try {
            send(message)
        } catch (_: IOException) {
            this@RssSourceDebugWebSocket.cancel()
        }
    }

    private fun closeNormally() {
        if (!isOpen) return
        try {
            close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "调试结束", false)
        } catch (_: IOException) {
        }
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {

    }

    override fun onException(exception: IOException) {
        this@RssSourceDebugWebSocket.cancel()
    }

}
