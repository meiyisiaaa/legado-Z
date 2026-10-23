package io.legado.app.model.rss.media

import android.os.Handler
import android.os.Looper
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.util.Clock
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Transformer
import io.legado.app.data.entities.RssMediaDownloadTask
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import splitties.init.appCtx
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RssMediaExporter(private val cache: RssMediaCache) {
    suspend fun export(
        task: RssMediaDownloadTask,
        request: DownloadRequest,
        commit: suspend (Uri) -> Boolean = { true },
    ): String = withContext(Dispatchers.IO) {
        if (RssMediaFiles.exists(task.outputUri)) {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                if (!commit(Uri.parse(task.outputUri))) throw CancellationException("媒体保存已取消")
            }
            return@withContext task.outputUri!!
        }
        if (RssMediaCache.adaptiveMime(task) == null) {
            val mime = DataSourceInputStream(cache.factory(task, offline = true).createDataSource(),
                DataSpec.Builder().setUri(request.uri).setKey(request.customCacheKey).build()).use { input ->
                val prefix = ByteArray(512)
                var size = 0
                while (size < prefix.size) {
                    val read = input.read(prefix, size, prefix.size - size)
                    if (read < 0) break
                    size += read
                }
                RssMediaSignatures.requireMimeType(prefix.copyOf(size), task.kind)
            }
            return@withContext RssMediaFiles.save(task, mime, commit) { output ->
                DataSourceInputStream(cache.factory(task, offline = true).createDataSource(),
                    DataSpec.Builder().setUri(request.uri).setKey(request.customCacheKey).build()).use { input ->
                    RssMediaFiles.copy(input, output)
                }
            }.toString()
        }
        val directory = File(appCtx.filesDir, "rss-media-export").apply { mkdirs() }
        val temporary = File(directory, "${task.id}.mp4")
        temporary.delete()
        try {
            transform(task, request, temporary)
            check(temporary.length() > 0) { "导出文件为空" }
            RssMediaFiles.save(task, MimeTypes.VIDEO_MP4, commit) { output ->
                temporary.inputStream().use { RssMediaFiles.copy(it, output) }
            }.toString()
        } finally {
            temporary.delete()
        }
    }

    private suspend fun transform(task: RssMediaDownloadTask, request: DownloadRequest, output: File) =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val mediaSource = DefaultMediaSourceFactory(cache.factory(task, offline = true))
                val muxer = InAppMp4Muxer.Factory()
                val codecs = GSON.fromJsonObject<Map<String, List<String>>>(request.data.toString(Charsets.UTF_8))
                    .getOrNull().orEmpty()
                val builder = Transformer.Builder(appCtx)
                    .setAssetLoaderFactory(ExoPlayerAssetLoader.Factory(appCtx,
                        DefaultDecoderFactory.Builder(appCtx).build(), Clock.DEFAULT, mediaSource))
                    .setMuxerFactory(muxer)
                // Preserve MP4-compatible codecs; transcode only unsupported or changing codecs.
                codecs["video"]?.let { types ->
                    if (types.distinct().size > 1 || types.any { it !in muxer.getSupportedSampleMimeTypes(C.TRACK_TYPE_VIDEO) }) {
                        builder.setVideoMimeType(MimeTypes.VIDEO_H264)
                    }
                }
                codecs["audio"]?.let { types ->
                    if (types.distinct().size > 1 || types.any { it !in muxer.getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO) }) {
                        builder.setAudioMimeType(MimeTypes.AUDIO_AAC)
                    }
                }
                val transformer = builder
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    }).build()
                continuation.invokeOnCancellation {
                    if (Looper.myLooper() == Looper.getMainLooper()) transformer.cancel()
                    else Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
                try {
                    transformer.start(EditedMediaItem.Builder(request.toMediaItem()).build(), output.absolutePath)
                } catch (error: Exception) {
                    transformer.cancel()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
}
