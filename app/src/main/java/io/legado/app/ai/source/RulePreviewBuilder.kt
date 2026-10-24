package io.legado.app.ai.source

import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.ai.RulePreview
import io.legado.app.ai.RulePreviewEntry
import io.legado.app.ai.RulePreviewGroup

/**
 * 把书源 JSON 摊平成 UI 可直接渲染的分组预览。
 *
 * 目的：用户不该被迫读一大坨 JSON 才知道 AI 生成了什么。
 * 分组顺序严格跟随 [BookSourceSchema.PREVIEW_GROUPS]，组内字段顺序跟随
 * [BookSourceSchema.RULE_OBJECT_FIELDS]，保证每次生成的预览布局稳定、可对比。
 *
 * 只吃 [JsonObject]（与 [BookSourceValidator] 同契约），零 Android 运行时依赖。
 */
object RulePreviewBuilder {

    /** 单个字段值在预览里最多展示的字符数。 */
    const val MAX_VALUE_CHARS = 160

    private const val ELLIPSIS = "…"

    /** 预览分组标识 → 标题资源 id。 */
    private val GROUP_TITLES: Map<String, Int> = linkedMapOf(
        BookSourceSchema.PREVIEW_GROUP_BASE to R.string.ai_preview_group_base,
        BookSourceSchema.RULE_SEARCH to R.string.ai_preview_group_search,
        BookSourceSchema.RULE_EXPLORE to R.string.ai_preview_group_explore,
        BookSourceSchema.RULE_BOOK_INFO to R.string.ai_preview_group_book_info,
        BookSourceSchema.RULE_TOC to R.string.ai_preview_group_toc,
        BookSourceSchema.RULE_CONTENT to R.string.ai_preview_group_content
    )

    /** 「基础信息」组展示的顶层字段及其顺序。 */
    private val BASE_FIELDS: Set<String> = linkedSetOf(
        BookSourceSchema.BOOK_SOURCE_URL,
        BookSourceSchema.BOOK_SOURCE_NAME,
        BookSourceSchema.BOOK_SOURCE_GROUP,
        BookSourceSchema.BOOK_SOURCE_TYPE,
        BookSourceSchema.MAIN_JS,
        BookSourceSchema.SEARCH_URL,
        BookSourceSchema.EXPLORE_URL
    )

    /**
     * 构建预览。空组（该规则整体缺失或全是空值）会被丢掉，不占屏幕。
     *
     * @return 至少含一个非空组的 [RulePreview]；全空时返回 [RulePreview.EMPTY]
     */
    fun build(json: JsonObject): RulePreview {
        val groups = BookSourceSchema.PREVIEW_GROUPS.mapNotNull { groupKey ->
            val titleRes = GROUP_TITLES[groupKey] ?: return@mapNotNull null
            val entries = if (groupKey == BookSourceSchema.PREVIEW_GROUP_BASE) {
                buildBaseEntries(json)
            } else {
                buildRuleEntries(json, groupKey)
            }
            if (entries.isEmpty()) null else RulePreviewGroup(titleRes, entries)
        }
        return if (groups.isEmpty()) RulePreview.EMPTY else RulePreview(groups)
    }

    /* ---------------- 内部实现 ---------------- */

    private fun buildBaseEntries(json: JsonObject): List<RulePreviewEntry> =
        BASE_FIELDS.mapNotNull { field ->
            val value = json.displayValue(field)
            if (value.isEmpty()) null else RulePreviewEntry(field, value)
        }

    /**
     * 构建某个规则组的条目。
     *
     * 先按 schema 声明顺序输出已知字段，再把 schema 之外的多余字段追加在后面 ——
     * 这些字段导入时会被丢弃（[BookSourceValidator] 已给出 WARNING），
     * 但预览里必须让用户看见，否则「预览有、导入没有」会变成诡异的 bug 报告。
     */
    private fun buildRuleEntries(json: JsonObject, ruleName: String): List<RulePreviewEntry> {
        val element = json.get(ruleName) ?: return emptyList()
        if (!element.isJsonObject) return emptyList()
        val ruleObject = element.asJsonObject

        val knownFields = BookSourceSchema.RULE_OBJECT_FIELDS[ruleName].orEmpty()
        val entries = mutableListOf<RulePreviewEntry>()

        knownFields.forEach { field ->
            val value = ruleObject.displayValue(field)
            if (value.isNotEmpty()) {
                entries.add(RulePreviewEntry(field, value))
            }
        }
        ruleObject.keySet().forEach { field ->
            if (field in knownFields) return@forEach
            val value = ruleObject.displayValue(field)
            if (value.isNotEmpty()) {
                entries.add(RulePreviewEntry(field, value))
            }
        }
        return entries
    }

    /**
     * 取字段的展示值。
     *
     * - 缺失 / null → 空串（调用方据此跳过该行）
     * - 原始类型 → 原值字符串
     * - 对象 / 数组 → 紧凑 JSON 文本（少见，但不能直接吞掉）
     */
    private fun JsonObject.displayValue(name: String): String {
        val element = get(name) ?: return ""
        if (element.isJsonNull) return ""
        val raw = if (element.isJsonPrimitive) {
            runCatching { element.asString }.getOrDefault(element.toString())
        } else {
            element.toString()
        }
        return raw.flatten()
    }

    /** 折行会撑爆列表项高度，统一压成单行并限长。 */
    private fun String.flatten(): String {
        val single = replace('\r', ' ').replace('\n', ' ').trim()
        if (single.isEmpty()) return ""
        return if (single.length <= MAX_VALUE_CHARS) {
            single
        } else {
            single.take(MAX_VALUE_CHARS) + ELLIPSIS
        }
    }
}
