package io.legado.app.ai.tts

import com.google.gson.JsonParser
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiConfigValidator
import io.legado.app.ai.AiError
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.ChatMessage
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.security.MessageDigest

/**
 * Generates a chapter-scoped voice storyboard. It has no Android or persistence dependencies;
 * callers own cancellation and save only the complete returned chapter.
 */
class StoryboardEngine(private val client: AiChatClient) {

    suspend fun create(
        options: AiChatOptions,
        chapterIndex: Int,
        text: String,
        roles: List<BookTtsRole>,
        onChunkProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): RoleTtsStoryboard {
        if (options.endpoint.isBlank() || options.apiKey.isBlank() || options.model.isBlank()) {
            throw AiError.NotConfigured()
        }
        val configIssues = AiConfigValidator.validate(options)
        if (configIssues.hasError()) throw AiError.InvalidConfig(configIssues.firstErrorDetail())
        if (text.isEmpty()) throw AiError.SchemaInvalid("章节正文为空")
        if (roles.isEmpty()) throw AiError.SchemaInvalid("请先创建可供分镜使用的角色")

        val roleEntries = roles.flatMap { role ->
            (listOf(role.name) + role.aliases)
                .map { normalizeName(it) to role.id }
                .filter { it.first.isNotEmpty() }
        }
        if (roleEntries.groupBy { it.first }.any { it.value.map { entry -> entry.second }.distinct().size > 1 }) {
            throw AiError.SchemaInvalid("角色名称或别名重复")
        }
        val roleLookup = roleEntries.toMap()
        if (roleLookup.isEmpty()) throw AiError.SchemaInvalid("角色名称为空")

        val chunks = splitIntoChunks(text, MAX_CHUNK_CHARS)
        val assembled = ArrayList<RoleTtsSegment>()
        chunks.forEachIndexed { index, chunk ->
            coroutineContext.ensureActive()
            val reply = client.chat(
                options,
                listOf(
                    ChatMessage.system(buildSystemPrompt(roles)),
                    ChatMessage.user(buildUserPrompt(chunk.text))
                )
            ).firstContent()
            if (reply.isBlank()) throw AiError.EmptyResponse()
            val localSegments = parseAndValidate(reply, chunk.text, roleLookup)
            assembled += localSegments.map { segment ->
                segment.copy(start = segment.start + chunk.start, endExclusive = segment.endExclusive + chunk.start)
            }
            onChunkProgress(index + 1, chunks.size)
        }

        validateCoverage(text, assembled)
        return RoleTtsStoryboard(
            chapterIndex = chapterIndex,
            contentHash = sha256(text),
            requestFingerprint = requestFingerprint(options, roles),
            promptVersion = PROMPT_VERSION,
            segments = assembled
        )
    }

    private fun parseAndValidate(
        raw: String,
        source: String,
        roleLookup: Map<String, String>
    ): List<RoleTtsSegment> {
        val json = extractJson(raw)
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw AiError.JsonMalformed(cause = e)
        }
        val elements = root.getAsJsonArray("segments")
            ?: throw AiError.SchemaInvalid("分镜缺少 segments 数组")
        if (elements.size() == 0) throw AiError.SchemaInvalid("分镜没有覆盖正文")

        var cursor = 0
        return elements.map { element ->
            val obj = try {
                element.asJsonObject
            } catch (e: Exception) {
                throw AiError.SchemaInvalid("分镜条目必须是对象")
            }
            val start = parseOffset(obj.get("start")) ?: throw AiError.SchemaInvalid("分镜 start 必须是整数")
            val end = parseOffset(obj.get("end")) ?: throw AiError.SchemaInvalid("分镜 end 必须是整数")
            val kind = obj.get("kind")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val speaker = obj.get("speaker")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
            val roleId = roleLookup[normalizeName(speaker)]
                ?: throw AiError.SchemaInvalid("分镜角色未绑定：$speaker")
            if (start != cursor || end <= start || end > source.length) {
                throw AiError.SchemaInvalid("分镜区间存在空缺、重叠或越界")
            }
            if (splitsSurrogatePair(source, start) || splitsSurrogatePair(source, end)) {
                throw AiError.SchemaInvalid("分镜区间拆开了 UTF-16 代理项")
            }
            if (kind !in ALLOWED_KINDS) throw AiError.SchemaInvalid("未知分镜类型：$kind")
            cursor = end
            RoleTtsSegment(start, end, kind, roleId)
        }.also {
            if (cursor != source.length) throw AiError.SchemaInvalid("分镜未覆盖完整正文")
        }
    }

    private fun validateCoverage(source: String, segments: List<RoleTtsSegment>) {
        if (segments.isEmpty()) throw AiError.SchemaInvalid("分镜没有覆盖正文")
        var cursor = 0
        for (segment in segments) {
            if (segment.start != cursor || segment.endExclusive <= segment.start ||
                segment.endExclusive > source.length || splitsSurrogatePair(source, segment.start) ||
                splitsSurrogatePair(source, segment.endExclusive)
            ) {
                throw AiError.SchemaInvalid("合并后的分镜区间不连续")
            }
            cursor = segment.endExclusive
        }
        if (cursor != source.length) throw AiError.SchemaInvalid("分镜未覆盖完整正文")
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        val unfenced = if (trimmed.startsWith("```")) {
            trimmed.substringAfter('\n', "").substringBeforeLast("```").trim()
        } else trimmed
        val start = unfenced.indexOf('{')
        val end = unfenced.lastIndexOf('}')
        if (start < 0 || end < start) throw AiError.JsonNotFound()
        return unfenced.substring(start, end + 1)
    }

    companion object {
        const val PROMPT_VERSION = "role-storyboard-v1"
        const val MAX_CHUNK_CHARS = 1400
        private val ALLOWED_KINDS = setOf("narration", "dialogue", "thought")

        fun splitIntoChunks(text: String, maxChars: Int = MAX_CHUNK_CHARS): List<RoleTtsChunk> {
            require(maxChars >= 2)
            if (text.isEmpty()) return emptyList()
            val chunks = ArrayList<RoleTtsChunk>()
            var start = 0
            while (start < text.length) {
                var end = minOf(start + maxChars, text.length)
                if (end < text.length && splitsSurrogatePair(text, end)) end--
                if (end < text.length) {
                    val minEnd = start + (maxChars * 2 / 3)
                    val boundary = (end downTo maxOf(start + 1, minEnd))
                        .firstOrNull { text[it - 1] in CHUNK_BOUNDARIES }
                    if (boundary != null) end = boundary
                }
                if (end <= start) end = nextCodePointBoundary(text, start)
                chunks += RoleTtsChunk(start, end, text.substring(start, end))
                start = end
            }
            return chunks
        }

        fun requestFingerprint(
            options: AiChatOptions,
            roles: List<BookTtsRole> = emptyList()
        ): String = sha256(
            listOf(
                options.endpoint.trim().trimEnd('/'),
                options.model.trim(),
                options.temperature.toString(),
                options.maxTokens.toString(),
                PROMPT_VERSION,
                roles.joinToString("\u0001") { role ->
                    listOf(role.id, normalizeName(role.name), role.aliases.map(::normalizeName).sorted().joinToString("\u0002"))
                        .joinToString("\u0002")
                }
            ).joinToString("\u0000")
        )

        fun audioCacheKey(
            storyboardFingerprint: String,
            contentHash: String,
            start: Int,
            endExclusive: Int,
            providerConfigFingerprint: String,
            voiceId: String,
            rate: Float
        ): String = sha256(
            listOf(
                storyboardFingerprint, contentHash, start.toString(), endExclusive.toString(),
                providerConfigFingerprint, voiceId, rate.toString()
            ).joinToString("\u0000")
        )

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        private fun buildSystemPrompt(roles: List<BookTtsRole>): String = buildString {
            append("你是有声书分镜器。只输出 JSON 对象，不要 Markdown。\n")
            append("把给定正文按朗读角色划分，必须逐字保留所有内容，不能改写、删减、重复或添加文字。\n")
            append("segments 必须按顺序无缝覆盖正文；start/end 是从 0 开始的 UTF-16 字符偏移，end 不包含在区间内。\n")
            append("每项格式：{\"start\":整数,\"end\":整数,\"kind\":\"narration|dialogue|thought\",\"speaker\":角色名称或别名}。\n")
            append("speaker 必须从这些已配置角色的名称/别名中选择；叙述使用旁白角色。角色：")
            append(roles.joinToString("；") { role ->
                "${role.name}（别名：${role.aliases.joinToString("、").ifBlank { "无" }}）"
            })
        }

        private fun buildUserPrompt(text: String): String =
            "按 system 规则为下面这段章节正文生成分镜。正文开始：\n$text\n正文结束。"

        private fun normalizeName(value: String): String = value.trim().lowercase()

        private fun parseOffset(value: com.google.gson.JsonElement?): Int? = runCatching {
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
            value.asBigDecimal.intValueExact()
        }.getOrNull()

        private fun splitsSurrogatePair(text: String, offset: Int): Boolean =
            offset in 1 until text.length && Character.isHighSurrogate(text[offset - 1]) &&
                Character.isLowSurrogate(text[offset])

        private fun nextCodePointBoundary(text: String, offset: Int): Int =
            if (offset + 1 < text.length && Character.isHighSurrogate(text[offset]) &&
                Character.isLowSurrogate(text[offset + 1])) offset + 2 else offset + 1

        private const val CHUNK_BOUNDARIES = "。！？；.!?;\n"
    }
}
