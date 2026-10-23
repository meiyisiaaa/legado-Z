package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.RssMediaDownloadTask
import kotlinx.coroutines.flow.Flow

@Dao
interface RssMediaDownloadDao {
    @Query("SELECT * FROM rssMediaDownloadTasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RssMediaDownloadTask>>

    @Query("SELECT * FROM rssMediaDownloadTasks ORDER BY createdAt")
    fun all(): List<RssMediaDownloadTask>

    @Query("SELECT * FROM rssMediaDownloadTasks WHERE id = :id")
    fun get(id: String): RssMediaDownloadTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(task: RssMediaDownloadTask)

    @Query("UPDATE rssMediaDownloadTasks SET status = :status, error = :error WHERE id = :id")
    fun setStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE rssMediaDownloadTasks SET bytesDownloaded = :bytes, totalBytes = :total WHERE id = :id")
    fun setProgress(id: String, bytes: Long, total: Long)

    @Query("UPDATE rssMediaDownloadTasks SET status = 'exporting', error = NULL WHERE id = :id AND status IN ('queued', 'downloading')")
    fun beginExport(id: String): Int

    @Query("UPDATE rssMediaDownloadTasks SET headersJson = :headersJson WHERE id = :id")
    fun setHeaders(id: String, headersJson: String)

    @Query("UPDATE rssMediaDownloadTasks SET mimeType = :mimeType WHERE id = :id")
    fun setMimeType(id: String, mimeType: String)

    @Query("UPDATE rssMediaDownloadTasks SET status = 'completed', outputUri = :uri, error = NULL WHERE id = :id AND status = 'exporting'")
    fun completeExport(id: String, uri: String): Int

    @Query("UPDATE rssMediaDownloadTasks SET status = :status, error = NULL WHERE id = :id AND status = :expectedStatus")
    fun requestStatus(id: String, status: String, expectedStatus: String): Int
}
