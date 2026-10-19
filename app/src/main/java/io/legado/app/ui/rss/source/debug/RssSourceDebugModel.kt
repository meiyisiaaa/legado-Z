package io.legado.app.ui.rss.source.debug

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.model.Debug
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunResult
import io.legado.app.model.DebugRunner
import java.util.UUID

class RssSourceDebugModel(application: Application) : BaseViewModel(application) {
    var rssSource: RssSource? = null
    private var callback: ((Int, String) -> Unit)? = null
    private val runOwnerId = "rss-ui:${UUID.randomUUID()}"
    @Volatile
    var listSrc: String? = null
        private set
    @Volatile
    var contentSrc: String? = null
        private set

    fun initData(sourceUrl: String?, finally: () -> Unit) {
        sourceUrl?.let {
            execute {
                rssSource = appDb.rssSourceDao.getByKey(sourceUrl)
            }.onFinally {
                finally()
            }
        }
    }

    fun observe(callback: (Int, String) -> Unit) {
        this.callback = callback
    }

    fun startDebug(key: String, start: (() -> Unit)? = null, error: (() -> Unit)? = null) {
        execute {
            val source = requireNotNull(rssSource) { "RSS source is unavailable" }
            DebugRunner.run(
                ownerId = runOwnerId,
                kind = DebugRunKind.RSS_UI,
                sourceUrl = source.sourceUrl,
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
            10 -> listSrc = msg
            20 -> contentSrc = msg
            else -> callback?.invoke(state, msg)
        }
    }

}
