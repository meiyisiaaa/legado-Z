# RSS 视频原画质保存

下载按媒体清单选择主视频的最高像素分辨率，并保留 Media3 对默认音轨、语言、主角色与 trick-play 的处理。同一质量偏好下使用码率比较。下载专用 `RendererCapabilitiesList` 按音视频类型声明压缩样本可下载，避免用当前设备的屏幕、硬件解码能力或编码器偏好限制保存质量。播放能力仍由播放端决定。

MP4 支持的编码优先原样封装；编码容器不兼容时沿用 H.264/AAC 转码策略，关闭 `DefaultEncoderFactory` 的格式回退，使不支持原始尺寸的设备报告导出失败，避免静默缩小输出。转码仍会改变压缩样本。跨 period 改变编码的流需要单独验证；指定与首轨相同的输出 MIME 本身并不强制转码。

## Media3 1.8.0 原始依据

- [DefaultTrackSelector](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/trackselection/DefaultTrackSelector.java)：`VideoTrackInfo.compareQualityPreferences` 先比较 `pixelCount`，再比较 `bitrate`；`compareNonQualityPreferences` 在此之前比较解码支持、主解码器、硬件加速及编码偏好。`forceHighestSupportedBitrate` 使下载选择固定轨道。
- [DownloadHelper](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/offline/DownloadHelper.java)：公开构造函数接受 `RendererCapabilitiesList`；1.8.0 `Factory` 提供 `setRenderersFactory`，尚无 capabilities-list setter。默认参数关闭音频声道数与设备能力的约束。
- [RendererCapabilitiesList](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/RendererCapabilitiesList.java) 与 [RendererCapabilities](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/RendererCapabilities.java)：下载专用能力接口。
- [ExoAssetLoaderBaseRenderer](https://github.com/androidx/media/blob/1.8.0/libraries/transformer/src/main/java/androidx/media3/transformer/ExoAssetLoaderBaseRenderer.java)：Transformer 自身也按媒体类型声明 `FORMAT_HANDLED`，在需要重编码时才创建解码器。
- [TransformerUtil](https://github.com/androidx/media/blob/1.8.0/libraries/transformer/src/main/java/androidx/media3/transformer/TransformerUtil.java)：`shouldTranscodeVideo` / `shouldTranscodeAudio` 决定原样封装或转码；像素宽高比、效果和容器编码支持也可能触发转码。
- [InAppMp4Muxer](https://github.com/androidx/media/blob/1.8.0/libraries/transformer/src/main/java/androidx/media3/transformer/InAppMp4Muxer.java) 与 [Mp4Muxer](https://github.com/androidx/media/blob/1.8.0/libraries/muxer/src/main/java/androidx/media3/muxer/Mp4Muxer.java)：容器支持编码取自库本身，包含 H.264、HEVC、AV1、VP9 等。
- [DefaultEncoderFactory](https://github.com/androidx/media/blob/1.8.0/libraries/transformer/src/main/java/androidx/media3/transformer/DefaultEncoderFactory.java)：默认 `enableFallback=true`，回退可改变分辨率、码率及 profile；`setEnableFallback(false)` 保留请求格式并在设备不支持时失败。

## 可复验的公开短片

2026-10-23 读取 Google [公开素材索引](https://storage.googleapis.com/cpe-sample-media/content.json) 及实际清单：

| 素材 | 时长 | 视频分辨率 | 最高轨与音轨 | 大小 |
| --- | --- | --- | --- | --- |
| Chromecast Ultra Ad | 37.233 秒 | 360p、480p、720p、1080p | 1920×1080@30，H.264 High，独立双声道 AAC | 清单平均码率估算约 16.9 MB |
| For Bigger Blazes | 15.015 秒 | 360p、480p、720p | 1280×720@23.976，H.264，独立双声道 AAC | 最高视频、音频的 init 和分片 Content-Length 合计 3,864,047 字节 |

- Ultra [HLS 清单](https://storage.googleapis.com/cpe-sample-media/content/ads/chromecast_ultra_ad/chromecast_ultra_ad_m4s_master.m3u8)、[DASH 清单](https://storage.googleapis.com/cpe-sample-media/content/ads/chromecast_ultra_ad/chromecast_ultra_ad_m4s_master.mpd)。
- Blazes [HLS 清单](https://storage.googleapis.com/cpe-sample-media/content/ads/for_bigger_blazes/for_bigger_blazes_m4s_master.m3u8)、[TS HLS 清单](https://storage.googleapis.com/cpe-sample-media/content/ads/for_bigger_blazes/for_bigger_blazes_ts_master.m3u8)、[DASH 清单](https://storage.googleapis.com/cpe-sample-media/content/ads/for_bigger_blazes/for_bigger_blazes_m4s_master.mpd)。

## 本地回归检查

`RssMediaTrackSelectionInstrumentedTest` 通过真实 DownloadHelper 与 HLS 下载器选择 3840×2160 视频，其清单码率刻意低于 320×180 变体；备用音轨排在默认音轨之前。随后停止本地服务器，离线导出 MP4，核对 4K 尺寸、音频存在及每个压缩视频样本的 SHA-256，验证原样封装。4K 素材为 FFmpeg 生成的六秒黑屏，init 与视频分片约 27 KB，无第三方内容。

使用 `tools/rss-media/generate-quality-fixtures.ps1` 重建新增素材。测试执行结果以本轮实际 Android instrumentation 输出为准。
