package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rssMediaDownloadTasks")
data class RssMediaDownloadTask(
    @PrimaryKey val id: String,
    val origin: String,
    val link: String,
    val title: String,
    val url: String,
    val kind: String,
    val mimeType: String? = null,
    val thumbnail: String? = null,
    val size: Long? = null,
    val label: String? = null,
    val headersJson: String = "{}",
    val status: String = QUEUED,
    val outputUri: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val requestUrl: String? = null,
) {
    companion object {
        const val QUEUED = "queued"
        const val DOWNLOADING = "downloading"
        const val PAUSED = "paused"
        const val EXPORTING = "exporting"
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val EXPORT_FAILED = "export_failed"
        const val CANCELLED = "cancelled"
    }
}
