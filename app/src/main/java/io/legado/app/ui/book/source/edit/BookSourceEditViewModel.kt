package io.legado.app.ui.book.source.edit

import android.app.Application
import android.content.Intent
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.ConcurrentRateLimiter.Companion.concurrentRecordMap
import io.legado.app.help.RuleComplete
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.http.CookieStore
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.storage.ImportOldData
import io.legado.app.help.source.BookSourceImport
import io.legado.app.model.SharedJsScope
import io.legado.app.utils.getClipText
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.jsonPath
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext


class BookSourceEditViewModel(application: Application) : BaseViewModel(application) {
    var autoComplete = false
    var bookSource: BookSource? = null

    fun initData(intent: Intent, onFinally: () -> Unit) {
        execute {
            val sourceUrl = intent.getStringExtra("sourceUrl")
            var source: BookSource? = null
            if (sourceUrl != null) {
                source = appDb.bookSourceDao.getBookSource(sourceUrl)
            }
            source?.let {
                bookSource = it
            }
        }.onFinally {
            onFinally()
        }
    }

    fun save(source: BookSource, success: ((BookSource) -> Unit)? = null) {
        execute {
            val normalizedSource = BookSourceImport.canonicalize(
                source,
                currentCoroutineContext()
            )
            if (normalizedSource.bookSourceUrl.isBlank() || normalizedSource.bookSourceName.isBlank()) {
                throw NoStackTraceException(context.getString(R.string.non_null_name_url))
            }
            val oldSource = bookSource ?: BookSource()
            if (!normalizedSource.equal(oldSource)) {
                normalizedSource.lastUpdateTime = System.currentTimeMillis()
                if (oldSource.exploreUrl != normalizedSource.exploreUrl) {
                    oldSource.clearExploreKindsCache()
                }
                if (oldSource.jsLib != normalizedSource.jsLib) {
                    SharedJsScope.remove(oldSource.jsLib)
                }
            }
            bookSource?.let {
                if (it.bookSourceUrl != normalizedSource.bookSourceUrl) {
                    SourceHelp.deleteBookSource(it.bookSourceUrl)
                } else {
                    appDb.bookSourceDao.delete(it)
                    SourceConfig.removeSource(it.bookSourceUrl)
                }
            }
            appDb.bookSourceDao.insert(normalizedSource)
            bookSource = normalizedSource
            concurrentRecordMap.remove(normalizedSource.bookSourceUrl) //删除并发限制缓存
            normalizedSource
        }.onSuccess {
            success?.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage)
            it.printOnDebug()
        }
    }

    fun pasteSource(onSuccess: (source: BookSource) -> Unit) {
        execute(context = Dispatchers.Main) {
            val text = context.getClipText()
            if (text.isNullOrBlank()) {
                throw NoStackTraceException("剪贴板为空")
            } else {
                importSource(text, onSuccess)
            }
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    fun importSource(text: String, finally: (source: BookSource) -> Unit) {
        execute {
            importSource(text)
        }.onSuccess {
            finally.invoke(it)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: "Error")
            it.printOnDebug()
        }
    }

    suspend fun importSource(text: String): BookSource {
        val input = if (text.isAbsUrl()) {
            okHttpClient.newCallStrResponse { url(text) }.body
                ?: throw NoStackTraceException("书源链接返回空内容")
        } else {
            text
        }
        val trimmed = input.trim()
        if (trimmed.contains("ruleSearchUrl") || trimmed.contains("ruleFindUrl")) {
            return when {
                trimmed.isJsonArray() -> {
                    val items: List<Map<String, Any>> = jsonPath.parse(trimmed).read("$")
                    val first = items.firstOrNull() ?: throw NoStackTraceException("书源数组为空")
                    ImportOldData.fromOldBookSource(jsonPath.parse(first))
                }
                trimmed.isJsonObject() -> ImportOldData.fromOldBookSource(jsonPath.parse(trimmed))
                else -> throw NoStackTraceException("格式不对")
            }
        }
        return BookSourceImport.parse(input, currentCoroutineContext())
    }

    fun clearCookie(url: String) {
        execute {
            CookieStore.removeCookie(url)
        }
    }

    fun ruleComplete(rule: String?, preRule: String? = null, type: Int = 1): String? {
        if (autoComplete) {
            return RuleComplete.autoComplete(rule, preRule, type)
        }
        return rule
    }

}
