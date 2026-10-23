package io.legado.app.model.rss.media

import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import io.legado.app.data.entities.RssMediaDownloadTask
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume

internal object RssMediaFiles {
    fun clearPartial(task: RssMediaDownloadTask) {
        File(File(appCtx.filesDir, "rss-media-export"), "${task.id}.mp4").delete()
        val image = task.kind == "image"
        val folder = if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            appCtx.contentResolver.delete(collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 1",
                arrayOf("%-${task.id.take(12)}.%", "$folder/Legado/"))
        } else {
            @Suppress("DEPRECATION")
            val directory = File(Environment.getExternalStoragePublicDirectory(folder), "Legado")
            directory.listFiles()?.filter {
                it.isFile && it.name.endsWith(".part") && it.name.contains("-${task.id.take(12)}.")
            }?.forEach { it.delete() }
        }
    }

    fun fileName(task: RssMediaDownloadTask, mimeType: String): String {
        val title = task.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim().trim('.').take(60).ifBlank { "订阅媒体" }
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: if (task.kind == "image") "jpg" else "mp4"
        return "$title-${task.id.take(12)}.$extension"
    }

    fun exists(outputUri: String?): Boolean {
        if (outputUri == null) return false
        return try {
            appCtx.contentResolver.openAssetFileDescriptor(Uri.parse(outputUri), "r")?.use {
                val length = if (it.length >= 0) it.length else it.parcelFileDescriptor.statSize
                length > 0L
            } == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun save(
        task: RssMediaDownloadTask,
        mimeType: String,
        commit: suspend (Uri) -> Boolean = { true },
        write: suspend (OutputStream) -> Unit,
    ): Uri {
        val caller = currentCoroutineContext()
        var committed = false
        suspend fun finish(uri: Uri, created: Boolean, file: File? = null): Uri = withContext(NonCancellable) {
            try {
                if (!caller.isActive || !commit(uri)) throw CancellationException("媒体保存已取消")
                committed = true
                uri
            } catch (error: Throwable) {
                if (created && !committed) {
                    file?.delete()
                    if (uri.scheme == "content") appCtx.contentResolver.delete(uri, null, null)
                }
                throw error
            }
        }
        val image = task.kind == "image"
        val folder = if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val name = fileName(task, mimeType)
        val resolver = appCtx.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val relativePath = "$folder/Legado/"
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0",
                arrayOf(name, relativePath), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val existing = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    if (exists(existing.toString())) return finish(existing, false)
                }
            }
            // Remove an interrupted export before recreating this task's deterministic output.
            resolver.delete(collection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 1",
                arrayOf(name, relativePath))
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = checkNotNull(resolver.insert(collection, values)) { "无法创建相册文件" }
            try {
                checkNotNull(resolver.openOutputStream(uri, "w")) { "无法写入相册文件" }.use { write(it) }
                currentCoroutineContext().ensureActive()
                check(resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) > 0) { "无法发布相册文件" }
                return finish(uri, true)
            } catch (error: Throwable) {
                if (!committed) resolver.delete(uri, null, null)
                throw error
            }
        }
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(folder), "Legado")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建相册目录" }
        val target = File(directory, name)
        if (target.isFile && target.length() > 0) return finish(scan(target, mimeType), false)
        val pending = File(directory, "$name.part")
        var renamed = false
        try {
            pending.outputStream().use { write(it) }
            currentCoroutineContext().ensureActive()
            check(pending.renameTo(target)) { "无法保存相册文件" }
            renamed = true
            // A cancelled scan must still return its new row so finish can remove it with the file.
            return withContext(NonCancellable) { finish(scan(target, mimeType), true, target) }
        } catch (error: Throwable) {
            if (renamed && !committed) target.delete()
            throw error
        } finally {
            pending.delete()
        }
    }

    suspend fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private suspend fun scan(file: File, mimeType: String): Uri = suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(appCtx, arrayOf(file.absolutePath), arrayOf(mimeType)) { _, uri ->
            if (continuation.isActive) continuation.resume(uri ?: Uri.fromFile(file))
        }
    }
}
