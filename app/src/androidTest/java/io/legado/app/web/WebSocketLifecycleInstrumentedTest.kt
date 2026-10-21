package io.legado.app.web

import androidx.test.ext.junit.runners.AndroidJUnit4
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunner
import io.legado.app.web.socket.BookSearchWebSocket
import io.legado.app.web.socket.BookSourceDebugWebSocket
import io.legado.app.web.socket.RssSourceDebugWebSocket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket as OkHttpWebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WebSocketLifecycleInstrumentedTest {

    @Test
    fun invalidRequestsReturnTextAndFixedNormalCloseOnAllSocketEndpoints() = runBlocking {
        val token = UUID.randomUUID().toString()
        withSocketServer { server, client ->
            val missingBook = "https://r41.invalid/book/missing-$token"
            val missingRss = "https://r41.invalid/rss/missing-$token"

            listOf(
                "{",
                "null",
                "[]",
                "{}",
                """{"key":""}"""
            ).forEach { payload ->
                assertTextAndClose(client, server, "/searchBook", payload, "Search finish")
            }

            listOf(
                "{",
                "null",
                "[]",
                "{}",
                """{"tag":"https://r41.invalid/book/no-key-$token"}""",
                """{"key":"key-$token"}""",
                """{"tag":"$missingBook","key":"key-$token"}"""
            ).forEach { payload ->
                assertTextAndClose(client, server, "/bookSourceDebug", payload, "调试结束")
            }

            listOf(
                "{",
                "null",
                "[]",
                "{}",
                """{"tag":""}""",
                """{"tag":"$missingRss"}"""
            ).forEach { payload ->
                assertTextAndClose(client, server, "/rssSourceDebug", payload, "调试结束")
            }
        }
    }

    @Test
    fun unacknowledgedServerCloseTimesOutAndCleansUpOnce() = runBlocking {
        withSocketServer(readTimeoutMillis = 0) { server, client ->
            val listener = RecordingSocketListener(respondToServerClose = false)
            val socket = client.newWebSocket(
                Request.Builder().url(server.url("/unacknowledgedClose")).build(),
                listener
            )
            try {
                awaitOpen(listener)
                assertTrue(
                    "The server did not send its normal close frame",
                    listener.closing.await(5, TimeUnit.SECONDS)
                )
                assertEquals(1000, listener.serverCloseCode)
                assertEquals("timeout probe", listener.serverCloseReason)
                assertTrue(
                    "The server did not release a socket after the close timeout",
                    server.closeTimeoutProbe.closed.await(8, TimeUnit.SECONDS)
                )
                assertEquals(1, server.closeTimeoutProbe.callbackCount.get())
                assertEquals(
                    NanoWSD.WebSocketFrame.CloseCode.InternalServerError,
                    server.closeTimeoutProbe.closeCode
                )
            } finally {
                socket.cancel()
            }
        }
    }

    @Test
    fun busyBookAndRssSocketsCloseWithoutCancellingExistingOwner() = runBlocking {
        val token = UUID.randomUUID().toString()
        val bookUrl = "https://r41.invalid/book/busy-$token"
        val rssUrl = "https://r41.invalid/rss/busy-$token"
        val bookSource = BookSource(
            bookSourceUrl = bookUrl,
            bookSourceName = "R41 busy fixture",
            enabled = false
        )
        val rssSource = RssSource(
            sourceUrl = rssUrl,
            sourceName = "R41 busy fixture",
            enabled = false
        )
        val ownerId = "r41-test-owner:$token"
        val ownerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ownerStarted = CompletableDeferred<Unit>()
        val holdOwner = CompletableDeferred<Unit>()
        var ownerJob: Job? = null

        try {
            assertNull("A debug run is already active before the test", DebugRunner.activeOwnerId.value)
            withContext(Dispatchers.IO) {
                appDb.bookSourceDao.insert(bookSource)
                appDb.rssSourceDao.insert(rssSource)
            }

            withSocketServer { server, client ->
                ownerJob = ownerScope.launch {
                    DebugRunner.run(ownerId, DebugRunKind.MCP) {
                        ownerStarted.complete(Unit)
                        holdOwner.await()
                    }
                }
                withTimeout(5_000) { ownerStarted.await() }
                assertEquals(ownerId, DebugRunner.activeOwnerId.value)

                assertTextAndClose(
                    client,
                    server,
                    "/bookSourceDebug",
                    """{"tag":"$bookUrl","key":"busy-query"}""",
                    "调试结束",
                    expectedMessagePart = ownerId
                )
                assertEquals("Busy book request cancelled its existing owner", ownerId, DebugRunner.activeOwnerId.value)

                assertTextAndClose(
                    client,
                    server,
                    "/rssSourceDebug",
                    """{"tag":"$rssUrl"}""",
                    "调试结束",
                    expectedMessagePart = ownerId
                )
                assertEquals("Busy RSS request cancelled its existing owner", ownerId, DebugRunner.activeOwnerId.value)
            }
        } finally {
            try {
                ownerJob?.cancelAndJoin()
                holdOwner.cancel()
                withTimeout(5_000) { DebugRunner.activeOwnerId.first { it == null } }
            } finally {
                ownerScope.cancel()
                withContext(Dispatchers.IO) {
                    appDb.bookSourceDao.delete(bookUrl)
                    appDb.rssSourceDao.delete(rssUrl)
                }
            }
        }
    }

    @Test
    fun disconnectDuringSlowBookSearchReleasesSocketOwnedDebugRun() = runBlocking<Unit> {
        val token = UUID.randomUUID().toString()
        val sourceUrl = "https://r41.invalid/book/cancel-$token"
        val httpServer = SlowSearchHttpServer()
        try {
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            val source = BookSource(
                bookSourceUrl = sourceUrl,
                bookSourceName = "R41 slow fixture",
                enabled = false,
                searchUrl = "http://127.0.0.1:${httpServer.listeningPort}/slow-search",
                ruleSearch = SearchRule(bookList = "li", name = "a@text", bookUrl = "a@href")
            )
            assertNull("A debug run is already active before the test", DebugRunner.activeOwnerId.value)
            withContext(Dispatchers.IO) { appDb.bookSourceDao.insert(source) }
            withSocketServer { server, client ->
                var socket: OkHttpWebSocket? = null
                try {
                    val listener = RecordingSocketListener()
                    val activeSocket = client.newWebSocket(
                        Request.Builder()
                            .url(server.url("/bookSourceDebug"))
                            .build(),
                        listener
                    )
                    socket = activeSocket
                    awaitOpen(listener)
                    assertTrue(
                        "Could not send slow-search debug request",
                        activeSocket.send("""{"tag":"$sourceUrl","key":"slow-query"}""")
                    )
                    assertTrue(
                        "The book source did not reach the test-owned slow HTTP endpoint",
                        httpServer.requestArrived.await(5, TimeUnit.SECONDS)
                    )
                    assertTrue(
                        "The socket did not acquire a debug-run lease",
                        DebugRunner.activeOwnerId.value?.startsWith("book-ws:") == true
                    )

                    assertTrue("Could not close the client WebSocket", activeSocket.close(1000, "R41 cancel test"))
                    assertTrue(
                        "The WebSocket close handshake did not finish",
                        listener.closed.await(5, TimeUnit.SECONDS)
                    )
                    assertNull("WebSocket closed with an unexpected error", listener.failure)
                    withTimeout(3_000) { DebugRunner.activeOwnerId.first { it == null } }
                } finally {
                    socket?.cancel()
                    httpServer.releaseResponse.countDown()
                    withTimeout(5_000) { DebugRunner.activeOwnerId.first { it == null } }
                }
            }
        } finally {
            try {
                httpServer.releaseResponse.countDown()
                httpServer.stop()
            } finally {
                withContext(Dispatchers.IO) { appDb.bookSourceDao.delete(sourceUrl) }
            }
        }
    }

    private suspend fun <T> withSocketServer(
        readTimeoutMillis: Int = NanoHTTPD.SOCKET_READ_TIMEOUT,
        block: suspend (SocketTestServer, OkHttpClient) -> T
    ): T {
        val server = SocketTestServer()
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build()
        return try {
            server.start(readTimeoutMillis, false)
            block(server, client)
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
            server.stop()
        }
    }

    private fun assertTextAndClose(
        client: OkHttpClient,
        server: SocketTestServer,
        path: String,
        payload: String,
        expectedReason: String,
        expectedMessagePart: String? = null
    ) {
        val listener = RecordingSocketListener()
        val socket = client.newWebSocket(
            Request.Builder().url(server.url(path)).build(),
            listener
        )
        try {
            awaitOpen(listener)
            assertTrue("Could not send test payload to $path", socket.send(payload))
            assertTrue("$path did not close after the request", listener.closed.await(10, TimeUnit.SECONDS))
            assertNull(
                "$path failed instead of closing normally; " +
                    "serverClose=${listener.serverCloseCode}/${listener.serverCloseReason}; " +
                    "messages=${listener.messages.toList()}",
                listener.failure
            )
            assertEquals("Unexpected close code for $path", 1000, listener.closeCode)
            assertEquals("Unexpected close reason for $path", expectedReason, listener.closeReason)
            val text = listener.messages.toList().joinToString("\n")
            assertTrue("$path returned no text response", text.isNotBlank())
            expectedMessagePart?.let {
                assertTrue("$path response did not identify the active owner", text.contains(it))
            }
        } finally {
            socket.cancel()
        }
    }

    private fun awaitOpen(listener: RecordingSocketListener) {
        assertTrue("WebSocket did not open: ${listener.failure}", listener.opened.await(5, TimeUnit.SECONDS))
        assertNull("WebSocket failed during handshake", listener.failure)
    }

    private class SocketTestServer : NanoWSD(0) {
        val closeTimeoutProbe = CloseTimeoutProbe()

        override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): NanoWSD.WebSocket? =
            when (handshake.uri) {
                "/bookSourceDebug" -> BookSourceDebugWebSocket(handshake)
                "/rssSourceDebug" -> RssSourceDebugWebSocket(handshake)
                "/searchBook" -> BookSearchWebSocket(handshake)
                "/unacknowledgedClose" -> UnacknowledgedCloseWebSocket(handshake, closeTimeoutProbe)
                else -> null
            }

        fun url(path: String): String = "ws://127.0.0.1:${listeningPort}$path"
    }

    private class RecordingSocketListener(
        private val respondToServerClose: Boolean = true
    ) : WebSocketListener() {
        val opened = CountDownLatch(1)
        val closing = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val messages = LinkedBlockingQueue<String>()

        @Volatile
        var closeCode: Int? = null

        @Volatile
        var closeReason: String? = null

        @Volatile
        var serverCloseCode: Int? = null

        @Volatile
        var serverCloseReason: String? = null

        @Volatile
        var failure: Throwable? = null

        override fun onOpen(webSocket: OkHttpWebSocket, response: Response) {
            opened.countDown()
        }

        override fun onMessage(webSocket: OkHttpWebSocket, text: String) {
            messages.offer(text)
        }

        override fun onClosing(webSocket: OkHttpWebSocket, code: Int, reason: String) {
            serverCloseCode = code
            serverCloseReason = reason
            closing.countDown()
            if (respondToServerClose) {
                webSocket.close(code, reason)
            }
        }

        override fun onClosed(webSocket: OkHttpWebSocket, code: Int, reason: String) {
            closeCode = code
            closeReason = reason
            closed.countDown()
        }

        override fun onFailure(webSocket: OkHttpWebSocket, t: Throwable, response: Response?) {
            failure = t
            opened.countDown()
            closed.countDown()
        }
    }

    private class CloseTimeoutProbe {
        val closed = CountDownLatch(1)
        val callbackCount = AtomicInteger()

        @Volatile
        var closeCode: NanoWSD.WebSocketFrame.CloseCode? = null
    }

    private class UnacknowledgedCloseWebSocket(
        handshakeRequest: NanoHTTPD.IHTTPSession,
        private val probe: CloseTimeoutProbe
    ) : NanoWSD.WebSocket(handshakeRequest) {
        override fun onOpen() {
            runCatching {
                close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "timeout probe", false)
            }
        }

        override fun onClose(
            code: NanoWSD.WebSocketFrame.CloseCode,
            reason: String,
            initiatedByRemote: Boolean
        ) {
            probe.closeCode = code
            probe.callbackCount.incrementAndGet()
            probe.closed.countDown()
        }

        override fun onMessage(message: NanoWSD.WebSocketFrame) = Unit

        override fun onPong(pong: NanoWSD.WebSocketFrame) = Unit

        override fun onException(exception: java.io.IOException) = Unit
    }

    private class SlowSearchHttpServer : NanoHTTPD("127.0.0.1", 0) {
        val requestArrived = CountDownLatch(1)
        val releaseResponse = CountDownLatch(1)

        override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            if (session.uri != "/slow-search") {
                return newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    "text/plain; charset=utf-8",
                    "unexpected fixture path: ${session.uri}"
                )
            }
            requestArrived.countDown()
            runCatching { releaseResponse.await(5, TimeUnit.SECONDS) }
            return newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "text/html; charset=utf-8",
                ""
            )
        }
    }
}
