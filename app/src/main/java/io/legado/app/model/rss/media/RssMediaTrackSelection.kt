package io.legado.app.model.rss.media

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RendererCapabilitiesList
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object RssMediaTrackSelection {
    fun createHelper(
        mediaItem: MediaItem,
        dataSourceFactory: DataSource.Factory,
        parameters: TrackSelectionParameters,
    ): DownloadHelper {
        // Downloads preserve compressed samples independently of this device's playback decoders.
        val capabilities: Array<RendererCapabilities> = arrayOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO).map { type ->
            object : RendererCapabilities {
                override fun getName() = "RssMediaDownload$type"
                override fun getTrackType() = type
                override fun supportsFormat(format: Format) = RendererCapabilities.create(
                    if (MimeTypes.getTrackType(format.sampleMimeType) == type) C.FORMAT_HANDLED
                    else C.FORMAT_UNSUPPORTED_TYPE
                )
                override fun supportsMixedMimeTypeAdaptation() = RendererCapabilities.ADAPTIVE_NOT_SUPPORTED
            }
        }.toTypedArray()
        return DownloadHelper(mediaItem,
            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem), parameters,
            object : RendererCapabilitiesList {
                override fun getRendererCapabilities() = capabilities
                override fun size() = capabilities.size
                override fun release() = Unit
            })
    }
}
