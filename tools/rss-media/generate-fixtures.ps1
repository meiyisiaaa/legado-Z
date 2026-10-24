$ErrorActionPreference = 'Stop'
$fixtureRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../app/src/androidTest/assets/rss-media-fixture'))
New-Item -ItemType Directory -Path $fixtureRoot -ErrorAction SilentlyContinue | Out-Null
Push-Location $fixtureRoot
try {
    & ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=320x180:rate=15' -f lavfi -i 'sine=frequency=440:sample_rate=44100' -t 6 -c:v libx264 -preset veryfast -profile:v baseline -pix_fmt yuv420p -g 30 -keyint_min 30 -sc_threshold 0 -c:a aac -b:a 64k -b:v 220k -movflags +faststart sample.mp4
    if ($LASTEXITCODE -ne 0) { throw 'MP4 generation failed' }
    & ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=0x183F38:size=480x320' -frames:v 1 image.png
    if ($LASTEXITCODE -ne 0) { throw 'PNG generation failed' }
    foreach ($folder in @('hls', 'fmp4', 'aes', 'dash', 'split-video', 'split-audio')) {
        New-Item -ItemType Directory -Path $folder -ErrorAction SilentlyContinue | Out-Null
    }
    & ffmpeg -hide_banner -loglevel error -y -i sample.mp4 -c copy -hls_time 2 -hls_list_size 0 -hls_segment_filename 'hls/segment%02d.ts' hls/index.m3u8
    if ($LASTEXITCODE -ne 0) { throw 'TS HLS generation failed' }
    & ffmpeg -hide_banner -loglevel error -y -i sample.mp4 -c copy -hls_time 2 -hls_list_size 0 -hls_segment_type fmp4 -hls_segment_filename 'fmp4/segment%02d.m4s' fmp4/index.m3u8
    if ($LASTEXITCODE -ne 0) { throw 'fMP4 HLS generation failed' }
    [IO.File]::WriteAllBytes((Join-Path $fixtureRoot 'aes/key.bin'), [byte[]](0..15))
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'key-info.txt'), "key.bin`naes/key.bin`n000102030405060708090a0b0c0d0e0f`n")
    & ffmpeg -hide_banner -loglevel error -y -i sample.mp4 -c copy -hls_time 2 -hls_list_size 0 -hls_key_info_file key-info.txt -hls_segment_filename 'aes/segment%02d.ts' aes/index.m3u8
    if ($LASTEXITCODE -ne 0) { throw 'AES-128 HLS generation failed' }
    Remove-Item -LiteralPath (Join-Path $fixtureRoot 'key-info.txt')
    & ffmpeg -hide_banner -loglevel error -y -i sample.mp4 -c copy -map 0:v -an -hls_time 2 -hls_list_size 0 -hls_segment_type fmp4 -hls_segment_filename 'split-video/segment%02d.m4s' split-video/index.m3u8
    if ($LASTEXITCODE -ne 0) { throw 'Video HLS generation failed' }
    & ffmpeg -hide_banner -loglevel error -y -i sample.mp4 -c copy -map 0:a -vn -hls_time 2 -hls_list_size 0 -hls_segment_type fmp4 -hls_segment_filename 'split-audio/segment%02d.m4s' split-audio/index.m3u8
    if ($LASTEXITCODE -ne 0) { throw 'Audio HLS generation failed' }
    & ffmpeg -hide_banner -loglevel error -y -i sample.mp4 -map 0:v -map 0:a -c copy -seg_duration 2 -use_template 1 -use_timeline 1 -adaptation_sets 'id=0,streams=v id=1,streams=a' -f dash dash/index.mpd
    if ($LASTEXITCODE -ne 0) { throw 'DASH generation failed' }
} finally { Pop-Location }
