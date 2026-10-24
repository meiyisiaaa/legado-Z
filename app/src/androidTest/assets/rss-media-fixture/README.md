# RSS media fixture

These six-second H.264/AAC test patterns and the PNG were generated with FFmpeg's
`testsrc2`, `sine`, and `color` filters. They contain no third-party artwork or
account data. Regenerate with `tools/rss-media/generate-fixtures.ps1`.

Fixtures cover progressive MP4, MPEG-TS HLS, fMP4 HLS, separate HLS audio/video,
AES-128 HLS, and DASH. `aes/key.bin` is a deliberately public, deterministic test
key containing bytes 0 through 15. It is never used outside these test fixtures.

The instrumentation server provides the fixture over HTTP, including byte ranges,
controlled authentication, redirects, and a dynamic player page. Media requests
are recorded so offline export can be distinguished from a second network load.
