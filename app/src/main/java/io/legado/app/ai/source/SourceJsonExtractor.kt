package io.legado.app.ai.source

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.ai.AiError
import io.legado.app.utils.INITIAL_GSON

/**
 * 从 LLM 的自由文本回复里把书源 JSON 抠出来。
 *
 * 为什么需要它：即便 system prompt 反复强调「只输出 JSON」，真实模型仍会输出
 * markdown 围栏、前置寒暄、后置解释。把「抠取」与「校验」分成两个组件，
 * 使得 [BookSourceValidator] 可以只吃 [JsonObject]（架构文档 Q6 的契约）。
 *
 * 抠取策略（按可靠性排序）：
 * 1. ```json 围栏内的内容；
 * 2. 全文中所有**括号配对**的 `{...}` 片段（带字符串/转义状态机，不会被 JSON 值里的花括号骗到）。
 *
 * 候选可能有多个（例如建议正文里举例的小对象），因此优先选择包含更多书源 schema
 * 字段的对象，再用顶层字段数打破平局，避免被解释正文里的无关 JSON 抢走。
 */
object SourceJsonExtractor {

    private val FENCE_REGEX = Regex("```[ \\t]*(?:json|JSON)?[ \\t]*\\r?\\n?([\\s\\S]*?)```")

    /**
     * 抠取并返回书源 JSON 对象。
     *
     * @throws AiError.JsonNotFound 全文找不到任何形似 JSON 对象的片段
     * @throws AiError.JsonMalformed 找到了片段但没有一个能解析成合法 JSON 对象
     */
    fun extract(rawReply: String): JsonObject {
        if (rawReply.isBlank()) throw AiError.JsonNotFound()

        val candidates = collectCandidates(rawReply)
        if (candidates.isEmpty()) throw AiError.JsonNotFound()

        val parsed = candidates.mapNotNull { parseObjectOrNull(it) }
        if (parsed.isEmpty()) {
            throw AiError.JsonMalformed()
        }
        return parsed.maxWithOrNull(
            compareBy<JsonObject> { json ->
                BookSourceSchema.TOP_LEVEL_FIELDS.count(json::has)
            }.thenBy { json -> !BookSourceValidator.validate(json).hasError() }
                .thenBy { it.size() }
        ) ?: parsed.first()
    }

    /** 抠不出来时返回 null 的宽松版本，供「重新校验」等非致命路径使用。 */
    fun extractOrNull(rawReply: String): JsonObject? =
        runCatching { extract(rawReply) }.getOrNull()

    /**
     * 把对象格式化成便于阅读与手工修改的 JSON 文本。
     *
     * 这里用 [INITIAL_GSON] 而不是 `io.legado.app.utils.GSON`：后者注册了
     * `ExploreRule` / `SearchRule` 等 `@Parcelize` 实体的反序列化器，会把
     * `android.os.Parcelable` 拖进纯 JVM 单测；[INITIAL_GSON] 同属工程既有 Gson 家族，
     * 已带 `setPrettyPrinting()` + `disableHtmlEscaping()`，且零 Android 依赖。
     * 即便如此仍做了兜底：任何异常都退回 Gson 自带的紧凑输出，绝不让格式化失败毁掉整次生成。
     */
    fun format(json: JsonObject): String =
        runCatching { INITIAL_GSON.toJson(json) }.getOrElse { json.toString() }

    /* ---------------- 内部实现 ---------------- */

    private fun collectCandidates(rawReply: String): List<String> {
        val candidates = linkedSetOf<String>()
        FENCE_REGEX.findAll(rawReply).forEach { match ->
            val fenced = match.groupValues[1].trim()
            if (fenced.isNotEmpty()) {
                candidates.add(fenced)
                candidates.addAll(scanBalancedObjects(fenced))
            }
        }
        candidates.addAll(scanBalancedObjects(rawReply))
        return candidates.toList()
    }

    private fun parseObjectOrNull(text: String): JsonObject? = runCatching {
        val element = JsonParser.parseString(text)
        if (element.isJsonObject) element.asJsonObject else null
    }.getOrNull()

    /** 扫描出所有顶层括号配对的 `{...}` 片段。 */
    private fun scanBalancedObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            if (text[index] != '{') {
                index++
                continue
            }
            val end = findMatchingBrace(text, index)
            if (end < 0) break
            result.add(text.substring(index, end + 1))
            index = end + 1
        }
        return result
    }

    /**
     * 从 [start]（必须指向 `{`）开始找配对的 `}`。
     *
     * 带字符串与转义状态机：`{"name": "a}b"}` 这种值里含花括号的情况不会误判。
     *
     * @return 配对右括号的下标；没配上返回 -1
     */
    private fun findMatchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var index = start
        while (index < text.length) {
            val ch = text[index]
            when {
                escaped -> escaped = false
                inString && ch == '\\' -> escaped = true
                ch == '"' -> inString = !inString
                inString -> Unit
                ch == '{' -> depth++
                ch == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }

}
