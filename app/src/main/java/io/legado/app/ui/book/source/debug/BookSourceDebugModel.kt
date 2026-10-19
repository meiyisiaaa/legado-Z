package io.legado.app.ui.book.source.debug

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.model.Debug
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunResult
import io.legado.app.model.DebugRunner
import java.util.UUID

class BookSourceDebugModel(application: Application) : BaseViewModel(application) {

    var bookSource: BookSource? = null
    private var callback: ((Int, String) -> Unit)? = null
    private val runOwnerId = "book-ui:${UUID.randomUUID()}"
    @Volatile
    var searchSrc: String? = null
        private set
    @Volatile
    var bookSrc: String? = null
        private set
    @Volatile
    var tocSrc: String? = null
        private set
    @Volatile
    var contentSrc: String? = null
        private set

    fun init(sourceUrl: String?, finally: () -> Unit) {
        sourceUrl?.let {
            //优先使用这个，不会抛出异常
            execute {
                bookSource = appDb.bookSourceDao.getBookSource(sourceUrl)
            }.onFinally {
                finally.invoke()
            }
        }
    }

    fun observe(callback: (Int, String) -> Unit) {
        this.callback = callback
    }

    fun startDebug(key: String, start: (() -> Unit)? = null, error: (() -> Unit)? = null) {
        execute {
            val source = requireNotNull(bookSource) { "Book source is unavailable" }
            DebugRunner.run(
                ownerId = runOwnerId,
                kind = DebugRunKind.BOOK_UI,
                sourceUrl = source.bookSourceUrl,
                onEvent = { event -> printLog(event.state, event.displayMessage) }
            ) {
                Debug.startDebug(source, key)
            }
        }.onStart {
            start?.invoke()
        }.onSuccess { result ->
            if (result is DebugRunResult.Busy ||
                result is DebugRunResult.Completed && result.snapshot.terminalState == "failed"
            ) {
                error?.invoke()
            }
        }.onError {
            error?.invoke()
        }
    }

    private fun printLog(state: Int, msg: String) {
        when (state) {
            10 -> searchSrc = msg
            20 -> bookSrc = msg
            30 -> tocSrc = msg
            40 -> contentSrc = msg
            else -> callback?.invoke(state, msg)
        }
    }

}
