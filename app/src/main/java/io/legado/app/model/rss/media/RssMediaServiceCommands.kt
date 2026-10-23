package io.legado.app.model.rss.media

/** Prevent an idle service from stopping across a command's database write and delivery. */
internal class RssMediaServiceCommands {
    private val lock = Any()
    private var pending = 0
    private var issued = 0L

    fun begin() = synchronized(lock) { pending++ }
    fun finish() = synchronized(lock) { pending-- }
    fun issue(): Long = synchronized(lock) { ++issued }

    fun failed(version: Long) = synchronized(lock) {
        if (issued == version) issued--
    }

    fun stopIfIdle(receivedVersion: Long, stop: () -> Boolean): Boolean = synchronized(lock) {
        pending == 0 && issued == receivedVersion && stop()
    }
}
