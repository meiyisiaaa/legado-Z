package io.legado.app.help.tts

import android.util.AtomicFile
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.tts.BookTtsRole
import io.legado.app.ai.tts.RoleTtsSegment
import io.legado.app.ai.tts.RoleTtsStoryboard
import io.legado.app.ai.tts.StoryboardEngine
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import java.io.File

/** Persists only complete validated chapter storyboards and hashes; audio files are keyed separately. */
class RoleTtsCacheManager(book: Book) {
    private val bookDirectory = File(BookHelp.cachePath, book.getFolderName())
    private val storyboardDirectory = File(bookDirectory, "roleTts/storyboards")
    private val audioDirectory = File(bookDirectory, "roleTts/audio")

    fun loadStoryboard(
        chapterIndex: Int,
        text: String,
        options: AiChatOptions,
        roles: List<BookTtsRole>
    ): RoleTtsStoryboard? {
        val file = storyboardFile(chapterIndex, text, options, roles)
        val parsed = runCatching {
            file.openRead().bufferedReader().use { GSON.fromJsonObject<RoleTtsStoryboard>(it.readText()).getOrNull() }
        }.getOrNull() ?: return null
        if (parsed.chapterIndex != chapterIndex || parsed.contentHash != StoryboardEngine.sha256(text) ||
            parsed.promptVersion != StoryboardEngine.PROMPT_VERSION ||
            parsed.requestFingerprint != StoryboardEngine.requestFingerprint(options, roles)
        ) return null
        return parsed.takeIf { validCoverage(text, it.segments) }
    }

    fun saveStoryboard(
        storyboard: RoleTtsStoryboard,
        text: String,
        options: AiChatOptions,
        roles: List<BookTtsRole>
    ) {
        require(storyboard.chapterIndex >= 0)
        require(storyboard.contentHash == StoryboardEngine.sha256(text))
        require(storyboard.promptVersion == StoryboardEngine.PROMPT_VERSION)
        require(storyboard.requestFingerprint == StoryboardEngine.requestFingerprint(options, roles))
        require(validCoverage(text, storyboard.segments))
        if (!storyboardDirectory.exists() && !storyboardDirectory.mkdirs()) {
            throw IllegalStateException("无法创建分镜缓存目录")
        }
        val atomicFile = storyboardFile(storyboard.chapterIndex, text, options, roles)
        val output = atomicFile.startWrite()
        try {
            output.write(GSON.toJson(storyboard).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    fun audioFile(key: String, extension: String): File = File(audioDirectory, "$key.$extension")

    fun temporaryAudioFile(key: String, extension: String): File {
        if (!audioDirectory.exists() && !audioDirectory.mkdirs()) {
            throw IllegalStateException("无法创建语音缓存目录")
        }
        return File(audioDirectory, "$key.part.$extension").apply { if (exists()) delete() }
    }

    fun commitAudioFile(key: String, extension: String) {
        val temporary = File(audioDirectory, "$key.part.$extension")
        val target = audioFile(key, extension)
        if (target.exists() && !target.delete()) throw IllegalStateException("无法替换语音缓存")
        if (!temporary.renameTo(target)) throw IllegalStateException("无法提交语音缓存")
    }

    private fun storyboardFile(
        chapterIndex: Int,
        text: String,
        options: AiChatOptions,
        roles: List<BookTtsRole>
    ): AtomicFile {
        val key = StoryboardEngine.sha256(
            "$chapterIndex\u0000${StoryboardEngine.sha256(text)}\u0000${StoryboardEngine.requestFingerprint(options, roles)}"
        )
        return AtomicFile(File(storyboardDirectory, "$key.json"))
    }

    private fun validCoverage(text: String, segments: List<RoleTtsSegment>): Boolean {
        if (segments.isEmpty()) return false
        var cursor = 0
        for (segment in segments) {
            if (segment.start != cursor || segment.endExclusive <= segment.start ||
                segment.endExclusive > text.length || splitsPair(text, segment.start) || splitsPair(text, segment.endExclusive)
            ) return false
            cursor = segment.endExclusive
        }
        return cursor == text.length
    }

    private fun splitsPair(text: String, offset: Int): Boolean =
        offset in 1 until text.length && Character.isHighSurrogate(text[offset - 1]) &&
            Character.isLowSurrogate(text[offset])
}
