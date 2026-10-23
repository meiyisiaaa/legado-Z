package io.legado.app.service

import android.app.Service
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.NotificationId
import io.legado.app.model.rss.media.RssMediaDownloadEngine
import io.legado.app.model.rss.media.RssMediaDownloads
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssMediaDownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class RssMediaDownloadService : LifecycleService() {
    private lateinit var engine: RssMediaDownloadEngine
    private var runner: Job? = null
    private var latestStartId = 0
    private var receivedCommandVersion = 0L
    private companion object {
        var shutdown: Job? = null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NotificationId.RssMediaDownloadService, notification(emptyList()))
        lifecycleScope.launch { RssMediaDownloads.observeTasks() }
        lifecycleScope.launch {
            RssMediaDownloads.tasks.collect { tasks ->
                getSystemService(NotificationManager::class.java)
                    .notify(NotificationId.RssMediaDownloadService, notification(tasks))
            }
        }
    }

    private fun startRunner() {
        if (runner?.isCompleted == false) return
        runner = lifecycleScope.launch {
            try {
                shutdown?.join()
                if (!::engine.isInitialized) engine = RssMediaDownloadEngine(lifecycleScope)
                engine.run { stopWhenIdle() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                markFailed()
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationId.RssMediaDownloadService, notification(RssMediaDownloads.tasks.value))
        latestStartId = startId
        receivedCommandVersion = maxOf(receivedCommandVersion,
            intent?.getLongExtra(RssMediaDownloads.COMMAND_VERSION, 0) ?: 0)
        super.onStartCommand(intent, flags, startId)
        startRunner()
        if (::engine.isInitialized) engine.command(intent?.getStringExtra("id"))
        return Service.START_STICKY
    }

    private suspend fun stopWhenIdle(): Boolean {
        val startId = latestStartId
        val version = receivedCommandVersion
        if (startId == 0) return false
        val hasWork = withContext(Dispatchers.IO) {
            appDb.rssMediaDownloadDao.all().any {
                it.status in listOf(RssMediaDownloadTask.QUEUED, RssMediaDownloadTask.DOWNLOADING, RssMediaDownloadTask.EXPORTING)
            }
        }
        if (hasWork || latestStartId != startId || receivedCommandVersion != version) return false
        return RssMediaDownloads.serviceCommands.stopIfIdle(version) { stopSelfResult(startId) }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopSelf()
    }

    override fun onDestroy() {
        if (::engine.isInitialized) shutdown = engine.close()
        super.onDestroy()
    }

    private suspend fun markFailed() = withContext(Dispatchers.IO) {
        appDb.rssMediaDownloadDao.all().filter {
            it.status in listOf(RssMediaDownloadTask.QUEUED, RssMediaDownloadTask.DOWNLOADING, RssMediaDownloadTask.EXPORTING)
        }.forEach {
            appDb.rssMediaDownloadDao.setStatus(it.id, RssMediaDownloadTask.FAILED, "下载服务已停止，请检查可用存储空间后重试")
        }
    }

    private fun notification(tasks: List<RssMediaDownloadTask>): Notification {
        val active = tasks.filter {
            it.status in listOf(RssMediaDownloadTask.QUEUED, RssMediaDownloadTask.DOWNLOADING, RssMediaDownloadTask.EXPORTING)
        }
        val exporting = active.any { it.status == RssMediaDownloadTask.EXPORTING }
        val downloading = active.any { it.status == RssMediaDownloadTask.DOWNLOADING }
        val phase = when {
            exporting && downloading -> R.string.rss_media_notification_mixed
            exporting -> R.string.rss_media_exporting
            downloading -> R.string.rss_media_downloading
            else -> R.string.rss_media_queued
        }
        val progress = rssMediaNotificationProgress(active)
        return NotificationCompat.Builder(this, AppConst.channelIdDownload)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.rss_media_notification_title, active.size))
            .setContentText(getString(phase))
            .setProgress(1000, progress ?: 0, progress == null)
            .setOngoing(true).setOnlyAlertOnce(true)
            .apply {
                packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                    setContentIntent(PendingIntent.getActivity(this@RssMediaDownloadService,
                        NotificationId.RssMediaDownloadService, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }.build()
    }
}

internal fun rssMediaNotificationProgress(active: List<RssMediaDownloadTask>): Int? {
    if (active.isEmpty() || active.any { it.status == RssMediaDownloadTask.EXPORTING || it.totalBytes <= 0 }) return null
    val downloaded = active.sumOf { it.bytesDownloaded.coerceAtLeast(0).toDouble() }
    val total = active.sumOf { it.totalBytes.toDouble() }
    return (downloaded / total * 1000).toInt().coerceIn(0, 1000)
}
