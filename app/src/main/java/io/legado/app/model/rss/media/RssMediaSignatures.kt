package io.legado.app.model.rss.media

import java.io.IOException

internal class InvalidRssMediaContentException : IOException("响应为登录页、错误页或不支持的媒体格式")
internal class UnsupportedRssMediaException(val userMessage: String) : IOException(userMessage)

/** Reject login/error pages even when the URL or Content-Type claims to be media. */
internal object RssMediaSignatures {
    fun responseMime(bytes: ByteArray, contentType: String?, kind: String): String {
        val prefix = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
        if (Regex("#EXT-X-(?:SESSION-)?KEY:[^\\r\\n]*METHOD=SAMPLE-AES(?:-CTR)?(?:,|\\s|$)")
                .containsMatchIn(prefix)) throw UnsupportedRssMediaException("SAMPLE-AES 加密媒体暂不支持保存")
        return when {
            prefix.startsWith("#EXTM3U") -> "application/x-mpegurl"
            Regex("<MPD(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(prefix) -> "application/dash+xml"
            mime in setOf("application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/x-mpegurl", "audio/mpegurl", "application/dash+xml") -> mime!!
            mime == "text/html" || Regex("^<(?:!doctype\\s+html|html|form|body)(?:\\s|>)", RegexOption.IGNORE_CASE)
                .containsMatchIn(prefix) -> throw InvalidRssMediaContentException()
            else -> mimeType(bytes, kind) ?: mime?.takeIf { it.startsWith("$kind/") }
                ?: throw InvalidRssMediaContentException()
        }
    }

    fun requireMimeType(bytes: ByteArray, kind: String): String =
        mimeType(bytes, kind) ?: throw InvalidRssMediaContentException()

    fun mimeType(bytes: ByteArray, kind: String): String? {
        fun ascii(offset: Int, value: String) = bytes.size >= offset + value.length &&
            value.indices.all { bytes[offset + it].toInt() and 0xff == value[it].code }
        fun magic(vararg values: Int) = bytes.size >= values.size &&
            values.indices.all { bytes[it].toInt() and 0xff == values[it] }
        if (kind == "image") return when {
            magic(0xff, 0xd8, 0xff) -> "image/jpeg"
            magic(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "image/png"
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            ascii(0, "BM") -> "image/bmp"
            ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")) -> "image/avif"
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1")) -> "image/heif"
            else -> null
        }
        return when {
            ascii(4, "ftyp") -> "video/mp4"
            magic(0x1a, 0x45, 0xdf, 0xa3) -> "video/webm"
            ascii(0, "FLV") -> "video/x-flv"
            ascii(0, "OggS") -> "video/ogg"
            ascii(0, "RIFF") && ascii(8, "AVI ") -> "video/x-msvideo"
            bytes.size > 376 && bytes[0] == 0x47.toByte() && bytes[188] == 0x47.toByte() &&
                bytes[376] == 0x47.toByte() -> "video/mp2t"
            else -> null
        }
    }
}
