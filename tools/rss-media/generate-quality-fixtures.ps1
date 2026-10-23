$ErrorActionPreference = 'Stop'
$qualityRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../app/src/androidTest/assets/rss-media-fixture/quality'))
New-Item -ItemType Directory -Path $qualityRoot -ErrorAction SilentlyContinue | Out-Null
Push-Location $qualityRoot
try {
    & ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=black:size=3840x2160:rate=15' -t 6 -an -c:v libx264 -threads 2 -preset ultrafast -profile:v baseline -pix_fmt yuv420p -crf 35 -g 90 -keyint_min 90 -sc_threshold 0 -hls_time 6 -hls_list_size 0 -hls_segment_type fmp4 -hls_fmp4_init_filename init.mp4 -hls_segment_filename 'segment%02d.m4s' '4k.m3u8'
    if ($LASTEXITCODE -ne 0) { throw '4K quality fixture generation failed' }
} finally { Pop-Location }
