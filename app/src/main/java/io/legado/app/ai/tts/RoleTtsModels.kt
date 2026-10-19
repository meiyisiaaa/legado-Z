package io.legado.app.ai.tts

/**
 * A validated chapter storyboard. Offsets are UTF-16 indexes into [contentHash]'s source text,
 * with inclusive start and exclusive end semantics.
 */
data class RoleTtsStoryboard(
    val chapterIndex: Int,
    val contentHash: String,
    val requestFingerprint: String,
    val promptVersion: String,
    val segments: List<RoleTtsSegment>
)

data class RoleTtsSegment(
    val start: Int,
    val endExclusive: Int,
    val kind: String,
    val speakerId: String
)

/** Persistent, book-local character identity and user-managed voice binding. */
data class BookTtsRole(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val binding: RoleTtsVoiceBinding? = null
)

/** The IDs deliberately reference existing Android engines and HttpTTS rows. */
data class RoleTtsVoiceBinding(
    val provider: Provider,
    val engineId: String? = null,
    val voiceName: String? = null,
    val httpTtsId: Long? = null
) {
    enum class Provider { SYSTEM, HTTP }
}

data class RoleTtsRolesConfig(
    val roles: List<BookTtsRole> = emptyList(),
    val roleIdAliases: Map<String, String> = emptyMap()
)

data class RoleTtsChunk(
    val start: Int,
    val endExclusive: Int,
    val text: String
)
