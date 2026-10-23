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

    @Query("UPDATE rssMediaDownloadTasks SET bytesDownloaded = :bytes, totalBytes = :total WHERE id = :id AND status IN ('queued', 'downloading')")
    fun setProgress(id: String, bytes: Long, total: Long)

    @Query("UPDATE rssMediaDownloadTasks SET status = 'exporting', bytesDownloaded = :bytes, totalBytes = :total, error = NULL WHERE id = :id AND status IN ('queued', 'downloading')")
    fun beginExport(id: String, bytes: Long, total: Long): Int

    @Query("UPDATE rssMediaDownloadTasks SET headersJson = :headersJson WHERE id = :id")
    fun setHeaders(id: String, headersJson: String)

    @Query("UPDATE rssMediaDownloadTasks SET mimeType = :mimeType WHERE id = :id")
    fun setMimeType(id: String, mimeType: String)

    @Query("UPDATE rssMediaDownloadTasks SET status = 'completed', outputUri = :uri, bytesDownloaded = :outputBytes, totalBytes = :outputBytes, error = NULL WHERE id = :id AND status = 'exporting'")
    fun completeExport(id: String, uri: String, outputBytes: Long): Int

    @Query("UPDATE rssMediaDownloadTasks SET bytesDownloaded = :bytes, totalBytes = :bytes WHERE id = :id AND status = 'completed' AND outputUri = :uri AND (bytesDownloaded != :bytes OR totalBytes != :bytes)")
    fun correctCompletedSize(id: String, uri: String, bytes: Long)

    @Query("UPDATE rssMediaDownloadTasks SET status = :status, error = NULL WHERE id = :id AND status = :expectedStatus")
    fun requestStatus(id: String, status: String, expectedStatus: String): Int
}
