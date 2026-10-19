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
import java.util.UUID

import java.io.IOException

/**
 * web端书源调试
 */
class BookSourceDebugWebSocket(handshakeRequest: NanoHTTPD.IHTTPSession) :
    NanoWSD.WebSocket(handshakeRequest),
    CoroutineScope by MainScope() {

    private val notPrintState = arrayOf(10, 20, 30, 40)
    private val runOwnerId = "book-ws:${UUID.randomUUID()}"

    override fun onOpen() {
        launch(IO) {
            kotlin.runCatching {
                while (isOpen) {
                    ping("ping".toByteArray())
                    delay(30000)
                }
            }
        }
    }

    override fun onClose(
        code: NanoWSD.WebSocketFrame.CloseCode,
        reason: String,
        initiatedByRemote: Boolean
    ) {
        cancel()
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        launch(IO) {
            kotlin.runCatching {
                if (!message.textPayload.isJson()) {
                    send("数据必须为Json格式")
                    close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "调试结束", false)
                    return@launch
                }
                val debugBean =
                    GSON.fromJsonObject<Map<String, String>>(message.textPayload).getOrNull()
                if (debugBean != null) {
                    val tag = debugBean["tag"]
                    val key = debugBean["key"]
                    if (tag.isNullOrBlank() || key.isNullOrBlank()) {
                        send(appCtx.getString(R.string.cannot_empty))
                        close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "调试结束", false)
                        return@launch
                    }
                    appDb.bookSourceDao.getBookSource(tag)?.let { source ->
                        when (
                            val result = DebugRunner.run(
                                ownerId = runOwnerId,
                                kind = DebugRunKind.BOOK_WEBSOCKET,
                                sourceUrl = source.bookSourceUrl,
                                onEvent = { event ->
                                    if (isOpen && event.state !in notPrintState) {
                                        runCatching { send(event.displayMessage) }
                                    }
                                }
                            ) {
                                Debug.startDebug(source, key)
                            }
                        ) {
                            is DebugRunResult.Busy -> if (isOpen) send("调试任务忙：${result.activeOwnerId}")
                            is DebugRunResult.Completed -> if (isOpen) {
                                close(
                                    NanoWSD.WebSocketFrame.CloseCode.NormalClosure,
                                    "调试结束",
                                    false
                                )
                            }
                        }
                    }
                } else {
                    send("数据必须为Json格式")
                    close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "调试结束", false)
                    return@launch
                }
            }
        }
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {

    }

    override fun onException(exception: IOException) {
        cancel()
    }

}
