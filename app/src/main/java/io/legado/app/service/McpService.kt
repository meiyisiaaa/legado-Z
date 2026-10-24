package io.legado.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.legado.app.R
import io.legado.app.constant.NotificationId
import io.legado.app.constant.AppLog
import io.legado.app.mcp.McpConfig
import io.legado.app.mcp.McpServer
import io.legado.app.mcp.mcpAuthority
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.startForegroundServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 用户在前台开启的调试会话，系统回收后不会自动恢复。 */
class McpService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: McpServer? = null
    private var stopping = false
    private var startup: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.mcp_title),
            NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, McpService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_outline_cloud_24)
            .setContentTitle(getString(R.string.mcp_title))
            .setContentText(getString(R.string.mcp_running_notification))
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stop)
            .build()
        startForeground(NotificationId.McpService, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) {
            shutdown()
            return START_NOT_STICKY
        }
        if (server != null || stopping) return START_NOT_STICKY
        mutableStatus.value = McpStatus(starting = true)
        startup = scope.launch {
            try {
                McpConfig.token() // 密钥不可用时直接显示错误，不启动无鉴权服务。
                val hosts = (NetworkUtils.getLocalIPAddress().mapNotNull { it.hostAddress?.substringBefore('%') } +
                    listOf("localhost", "127.0.0.1", "::1")).toSet()
                val port = McpConfig.port
                val instance = McpServer(port, hosts)
                server = instance
                instance.start()
                if (!stopping) mutableStatus.value = McpStatus(running = true,
                    addresses = hosts.filter { it != "localhost" && it != "::1" }
                        .map { "http://${mcpAuthority(it, port)}/mcp" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put(getString(R.string.mcp_start_failed), e)
                mutableStatus.value = McpStatus(error = getString(R.string.mcp_start_failed))
                startup = null
                shutdown(preserveError = true)
            }
        }
        return START_NOT_STICKY
    }

    private fun shutdown(preserveError: Boolean = false) {
        if (stopping) return
        stopping = true
        scope.launch {
            try { startup?.cancelAndJoin(); server?.stop() } finally {
                server = null
                if (!preserveError) mutableStatus.value = McpStatus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onTaskRemoved(rootIntent: Intent?) { shutdown() }

    override fun onDestroy() {
        val instance = server
        server = null
        scope.launch {
            withContext(NonCancellable) {
                try { startup?.cancelAndJoin(); instance?.stop() } finally { scope.cancel() }
            }
        }
        if (mutableStatus.value.error == null) mutableStatus.value = McpStatus()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mcp_debug_session"
        private const val STOP = "stop_mcp_session"
        private val mutableStatus = MutableStateFlow(McpStatus())
        val status = mutableStatus.asStateFlow()
        fun start(context: Context) {
            context.startForegroundServiceCompat(Intent(context, McpService::class.java).setAction("start"))
        }
        fun stop(context: Context) { context.stopService(Intent(context, McpService::class.java)) }
    }
}

data class McpStatus(val running: Boolean = false, val starting: Boolean = false,
    val addresses: List<String> = emptyList(), val error: String? = null)
