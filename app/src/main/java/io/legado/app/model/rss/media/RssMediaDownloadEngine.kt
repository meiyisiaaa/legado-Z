package io.legado.app.model.rss.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloaderFactory
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.CANCELLED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.COMPLETED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.DOWNLOADING
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.EXPORTING
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.EXPORT_FAILED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.FAILED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.PAUSED
import io.legado.app.data.entities.RssMediaDownloadTask.Companion.QUEUED
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owned, started and closed by RssMediaDownloadService. All manager calls run on main. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RssMediaDownloadEngine(private val scope: CoroutineScope) {
    private val dao get() = appDb.rssMediaDownloadDao
    private val cache = RssMediaCache()
    private val executor = Executors.newFixedThreadPool(2)
    private val index = DefaultDownloadIndex(cache.database)
    private val preparations = mutableMapOf<String, Job>()
    private val preparationSlots = Semaphore(2)
    private var exportJob: Job? = null
    private var exportingId: String? = null
    private var initialized = false
    private val manager = DownloadManager(appCtx, index, DownloaderFactory { request ->
        val task = checkNotNull(dao.get(request.id))
        DefaultDownloaderFactory(cache.factory(task), executor).createDownloader(request)
    }).apply {
        maxParallelDownloads = 2
        addListener(object : DownloadManager.Listener {
            override fun onInitialized(downloadManager: DownloadManager) {
                initialized = true
            }
            override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
                val task = dao.get(download.request.id) ?: return
                if (task.status in listOf(COMPLETED, CANCELLED, PAUSED, EXPORTING, EXPORT_FAILED)) return
                when (download.state) {
                    Download.STATE_DOWNLOADING -> dao.setStatus(task.id, DOWNLOADING)
                    Download.STATE_QUEUED, Download.STATE_RESTARTING -> dao.setStatus(task.id, QUEUED)
                    Download.STATE_FAILED -> dao.setStatus(task.id, FAILED, "下载失败，请检查网络或登录状态后重试")
                }
            }
        })
    }

    suspend fun run(onIdle: suspend () -> Boolean) {
        withContext(Dispatchers.IO) {
            dao.all().filter { it.status != COMPLETED }.forEach { RssMediaFiles.clearPartial(it) }
            dao.all().filter { it.status == EXPORTING || it.status == DOWNLOADING }
                .forEach { dao.setStatus(it.id, QUEUED) }
        }
        while (!initialized) delay(25)
        withContext(Dispatchers.IO) { dao.all() }.forEach { task ->
            when (task.status) {
                PAUSED -> manager.setStopReason(task.id, 1)
                CANCELLED -> manager.removeDownload(task.id)
            }
        }
        manager.resumeDownloads()
        while (scope.isActive) {
            if (initialized) {
                manager.currentDownloads.forEach { download ->
                    withContext(Dispatchers.IO) {
                        dao.setProgress(download.request.id, download.bytesDownloaded, download.contentLength)
                    }
                }
                val tasks = withContext(Dispatchers.IO) { dao.all() }
                tasks.filter { it.status == CANCELLED }.forEach { task ->
                    if (withContext(Dispatchers.IO) { index.getDownload(task.id) } != null) manager.removeDownload(task.id)
                }
                tasks.filter { it.status == PAUSED }.forEach { manager.setStopReason(it.id, 1) }
                tasks.filter { it.status == QUEUED || it.status == DOWNLOADING }.forEach { task ->
                    val download = withContext(Dispatchers.IO) { index.getDownload(task.id) }
                    when {
                        download?.state == Download.STATE_COMPLETED -> startExport(task, download)
                        download == null && !preparations.containsKey(task.id) &&
                            preparations.size + manager.currentDownloads.count {
                                it.state in listOf(Download.STATE_DOWNLOADING, Download.STATE_QUEUED, Download.STATE_RESTARTING)
                            } < 2 -> prepare(task)
                        download?.state == Download.STATE_FAILED -> manager.addDownload(download.request)
                        download?.stopReason != null && download.stopReason != 0 -> manager.setStopReason(task.id, 0)
                    }
                }
                if (tasks.none { it.status in listOf(QUEUED, DOWNLOADING, EXPORTING) } &&
                    preparations.isEmpty() && exportJob?.isCompleted != false && manager.isIdle) {
                    if (onIdle()) return
                }
            }
            delay(1000)
        }
    }

    fun command(id: String?) {
        if (id == null) return
        when (val action = dao.get(id)?.status) {
            PAUSED, CANCELLED -> {
                preparations.remove(id)?.cancel()
                if (exportingId == id) exportJob?.cancel()
                if (action == CANCELLED) manager.removeDownload(id) else manager.setStopReason(id, 1)
            }
            QUEUED -> manager.setStopReason(id, 0)
        }
    }

    private fun prepare(task: RssMediaDownloadTask) {
        preparations[task.id] = scope.launch {
            try {
                preparationSlots.withPermit {
                    val request = withTimeout(60_000) { prepareRequest(task) }
                    val current = withContext(Dispatchers.IO) { dao.get(task.id) }
                    if (current?.status == QUEUED) manager.addDownload(request)
                }
            } catch (_: TimeoutCancellationException) {
                withContext(Dispatchers.IO) {
                    if (dao.get(task.id)?.status == QUEUED) dao.setStatus(task.id, FAILED, "媒体解析超时，请重试")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.IO) {
                    if (dao.get(task.id)?.status == QUEUED) dao.setStatus(task.id, FAILED,
                        rssMediaPreparationError(error))
                }
            } finally {
                preparations.remove(task.id)
            }
        }
    }

    private suspend fun prepareRequest(task: RssMediaDownloadTask): DownloadRequest {
        val preparedTask = if (RssMediaCache.requiresProbe(task) ||
            RssMediaCache.adaptiveMime(task) == androidx.media3.common.MimeTypes.APPLICATION_M3U8) {
            val detected = cache.probeMime(task)
            withContext(Dispatchers.IO) { dao.setMimeType(task.id, detected) }
            task.copy(mimeType = detected)
        } else task
        val mime = RssMediaCache.adaptiveMime(preparedTask)
        if (mime == null) return DownloadRequest.Builder(task.id, Uri.parse(task.url))
            .setMimeType(preparedTask.mimeType).build()
        val item = MediaItem.Builder().setUri(task.url).setMimeType(mime).build()
        val parameters = DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon()
            .setForceHighestSupportedBitrate(true)
            .setViewportSize(Int.MAX_VALUE, Int.MAX_VALUE, false)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val helper = RssMediaTrackSelection.createHelper(item, cache.upstream(task), parameters)
            continuation.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post { helper.release() }
            }
            helper.prepare(object : DownloadHelper.Callback {
                override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                    try {
                        if (!tracksInfoAvailable) throw UnsupportedRssMediaException("未能读取媒体轨道，请从原文重新识别")
                        val codecs = mutableMapOf<String, MutableSet<String>>()
                        repeat(helper.periodCount) { period ->
                            val info = helper.getMappedTrackInfo(period)
                            repeat(info.rendererCount) { renderer ->
                                val type = info.getRendererType(renderer)
                                val key = when (type) {
                                    C.TRACK_TYPE_VIDEO -> "video"
                                    C.TRACK_TYPE_AUDIO -> "audio"
                                    else -> null
                                }
                                if (key != null) helper.getTrackSelections(period, renderer).forEach { selection ->
                                    repeat(selection.length()) { track ->
                                        val format = selection.getFormat(track)
                                        if (format.drmInitData != null) throw UnsupportedRssMediaException("DRM 加密保护媒体暂不支持保存")
                                        format.sampleMimeType?.let { codecs.getOrPut(key) { mutableSetOf() }.add(it) }
                                    }
                                }
                            }
                        }
                        val request = helper.getDownloadRequest(task.id, GSON.toJson(codecs).toByteArray(Charsets.UTF_8))
                        if (request.streamKeys.isEmpty()) throw UnsupportedRssMediaException("未找到设备可用的音视频轨道")
                        if (continuation.isActive) continuation.resume(request)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        helper.release()
                    }
                }
                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    helper.release()
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            })
        }
    }

    private fun startExport(task: RssMediaDownloadTask, download: Download) {
        if (exportJob?.isCompleted == false) return
        exportingId = task.id
        exportJob = scope.launch {
            if (withContext(Dispatchers.IO) {
                dao.beginExport(task.id, download.bytesDownloaded,
                    download.contentLength.takeIf { it >= 0 } ?: download.bytesDownloaded)
            } == 0) return@launch
            try {
                RssMediaExporter(cache).export(task, download.request) { uri ->
                    withContext(Dispatchers.IO) {
                        // Download/export progress counts cached media bytes; completion shows the published file size.
                        val bytes = checkNotNull(RssMediaFiles.sizeOf(uri.toString())) { "无法读取导出文件大小" }
                        dao.completeExport(task.id, uri.toString(), bytes) > 0
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: InvalidRssMediaContentException) {
                withContext(Dispatchers.IO) {
                    if (dao.get(task.id)?.status == EXPORTING) dao.setStatus(task.id, FAILED,
                        "响应为登录页、错误页或不支持的媒体格式，请检查登录状态后重试")
                }
                manager.removeDownload(task.id)
            } catch (_: Exception) {
                withContext(Dispatchers.IO) {
                    if (dao.get(task.id)?.status == EXPORTING) dao.setStatus(task.id, EXPORT_FAILED,
                        "导出失败，下载缓存已保留；请确认存储权限、可用空间与设备编解码支持后重试")
                }
            } finally {
                withContext(NonCancellable) {
                    if (withContext(Dispatchers.IO) { dao.get(task.id)?.status == COMPLETED }) manager.removeDownload(task.id)
                }
                exportingId = null
            }
        }
    }

    fun close(): Job {
        val workers = preparations.values.toList() + listOfNotNull(exportJob)
        workers.forEach { it.cancel() }
        // Service destruction must let exporter finally blocks finish before closing their cache.
        return scope.launch(NonCancellable) {
            workers.joinAll()
            manager.release()
            withContext(Dispatchers.IO) {
                executor.shutdown()
                cache.release()
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun rssMediaPreparationError(error: Exception): String = when (error) {
    is DownloadHelper.LiveContentUnsupportedException -> "实时直播流暂不支持保存，请选择已结束的点播媒体"
    is UnsupportedRssMediaException -> error.userMessage
    is InvalidRssMediaContentException -> "媒体响应为登录页、错误页或不支持的格式，请检查登录状态后重试"
    is SecurityException -> "读取媒体权限不足，请检查来源与存储权限"
    is IOException -> "媒体读取失败，请检查网络或登录状态后重试"
    else -> "媒体解析失败，请从原文重新识别或检查来源规则"
}
