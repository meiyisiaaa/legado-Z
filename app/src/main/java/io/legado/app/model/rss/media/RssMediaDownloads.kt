package io.legado.app.model.rss.media

import android.content.Intent
import androidx.core.content.ContextCompat
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.CANCELLED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.COMPLETED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.PAUSED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.QUEUED
import io.legado.app.service.RssMediaDownloadService
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import splitties.init.appCtx

object RssMediaDownloads {
    internal const val COMMAND_VERSION = "rssMediaCommandVersion"
    internal val serviceCommands = RssMediaServiceCommands()
    private val mutableTasks = MutableStateFlow<List<RssMediaDownloadTask>>(emptyList())
    val tasks: StateFlow<List<RssMediaDownloadTask>> = mutableTasks.asStateFlow()

    /** The observing UI or foreground service owns collection and cancellation. */
    suspend fun observeTasks() {
        withContext(Dispatchers.IO) {
            val dao = appDb.rssMediaDownloadDao
            dao.all().filter { it.status == COMPLETED }.forEach { task ->
                RssMediaFiles.sizeOf(task.outputUri)?.let { bytes ->
                    dao.correctCompletedSize(task.id, task.outputUri!!, bytes)
                }
            }
        }
        appDb.rssMediaDownloadDao.observeAll().collect { mutableTasks.value = it }
    }

    suspend fun enqueue(candidates: List<RssMediaCandidate>) = mutation { withContext(Dispatchers.IO) {
        candidates.forEach { raw ->
            val candidate = RssMediaResolver.prepare(raw)
            require(candidate.kind == "image" || candidate.kind == "video")
            require(android.net.Uri.parse(candidate.url).scheme in listOf("http", "https"))
            val old = appDb.rssMediaDownloadDao.get(candidate.id)
            if (old != null && old.status != CANCELLED &&
                !(old.status == COMPLETED && !RssMediaFiles.exists(old.outputUri))) return@forEach
            appDb.rssMediaDownloadDao.upsert(RssMediaDownloadTask(
                id = candidate.id, origin = candidate.article.origin, link = candidate.article.link,
                title = candidate.article.title, url = candidate.url, kind = candidate.kind,
                mimeType = candidate.mimeType, thumbnail = candidate.thumbnail, size = candidate.size,
                label = candidate.label, headersJson = GSON.toJson(candidate.headers),
                requestUrl = candidate.requestUrl,
            ))
        }
        start("run")
    } }

    suspend fun pause(id: String) = command(id, PAUSED)
    suspend fun resume(id: String) = command(id, QUEUED)
    suspend fun retry(id: String) = command(id, QUEUED)
    suspend fun cancel(id: String) = command(id, CANCELLED)

    private suspend fun command(id: String, status: String) = mutation { withContext(Dispatchers.IO) {
        val task = appDb.rssMediaDownloadDao.get(id) ?: return@withContext
        if (task.status == COMPLETED && RssMediaFiles.exists(task.outputUri)) return@withContext
        if (status == QUEUED) {
            val article = appDb.rssArticleDao.getByLink(task.origin, task.link)
                ?: RssArticle(origin = task.origin, link = task.link, title = task.title)
            val previousHeaders = GSON.fromJsonObject<Map<String, String>>(task.headersJson).getOrNull().orEmpty()
            val refreshed = RssMediaResolver.prepare(RssMediaCandidate(
                article, task.url, task.kind, task.mimeType, task.thumbnail, task.size, task.label,
                retryHeaders(previousHeaders), task.requestUrl ?: task.url,
            ))
            require(refreshed.url == task.url) { "媒体地址已变化，请从原文重新识别后下载" }
            appDb.rssMediaDownloadDao.setHeaders(id, GSON.toJson(refreshed.headers))
        }
        if (appDb.rssMediaDownloadDao.requestStatus(id, status, task.status) == 0) return@withContext
        start(status, id)
    } }

    private suspend fun <T> mutation(block: suspend () -> T): T {
        serviceCommands.begin()
        try {
            return block()
        } finally {
            serviceCommands.finish()
        }
    }

    // Request rules regenerate credentials; only public request context survives a retry.
    internal fun retryHeaders(headers: Map<String, String>): Map<String, String> = headers.filterKeys {
        it.lowercase(java.util.Locale.ROOT) in setOf("referer", "user-agent", "accept", "accept-language", "origin")
    }

    private fun start(action: String, id: String? = null) {
        val version = serviceCommands.issue()
        try {
            ContextCompat.startForegroundService(appCtx,
                Intent(appCtx, RssMediaDownloadService::class.java).setAction(action)
                    .putExtra("id", id).putExtra(COMMAND_VERSION, version))
        } catch (error: Exception) {
            serviceCommands.failed(version)
            throw error
        }
    }
}
