package io.legado.app.ai.source

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.ai.IssueLevel
import io.legado.app.ai.ValidationIssue
import io.legado.app.ai.ValidationResult

/**
 * 书源 JSON 的 schema 校验器。
 *
 * 契约（架构文档 Q6）：
 * - **只吃 [JsonObject]**：字符串 → JsonObject 的抠取与语法校验属于 [SourceJsonExtractor] 的职责，
 *   这里拿到的一定是语法合法的对象，因此本类不产生任何「语法类」问题。
 * - 两级严重度：[IssueLevel.ERROR] 阻断一键导入；[IssueLevel.WARNING] 只提示。
 *   判据是「导入后书源还能不能用」——
 *   缺 `bookSourceUrl` 连主键都没有，必错；缺 `ruleToc.chapterName` 只是目录名难看，给警告。
 * - 白名单外的字段一律 WARNING，因为 Gson 会**静默丢弃**它们，
 *   用户必须被告知「你看到的 JSON 和导入后的书源不一致」。
 *
 * 零 Android 运行时依赖（`R.string.*` 只是编译期 int 常量），完全可 JVM 单测。
 */
object BookSourceValidator {

    /** 问题 detail 中回显原值的最大长度，防止把整段 JSON 灌进错误提示。 */
    const val MAX_DETAIL_CHARS = 120

    /** 「三大核心规则全缺」这条问题挂靠的虚拟字段名。 */
    const val FIELD_RULES = "rules"

    private const val SCHEME_HTTP = "http://"
    private const val SCHEME_HTTPS = "https://"

    /**
     * 校验一份书源 JSON。
     *
     * 无论问题多严重都会走完全部检查项并返回完整清单，
     * 不做「遇到第一个 ERROR 就短路」——用户需要一次看到所有毛病。
     */
    fun validate(json: JsonObject): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        validateBaseFields(json, issues)
        validateUnknownTopLevelFields(json, issues)
        validateRuleObjects(json, issues)
        validateCoreRulePresence(json, issues)
        return ValidationResult(issues)
    }

    /* ---------------- 基础字段 ---------------- */

    private fun validateBaseFields(json: JsonObject, issues: MutableList<ValidationIssue>) {
        val url = json.readString(BookSourceSchema.BOOK_SOURCE_URL)
        when {
            url.isBlank() -> issues.add(
                ValidationIssue(
                    field = BookSourceSchema.BOOK_SOURCE_URL,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_source_url_missing
                )
            )

            !url.startsWith(SCHEME_HTTP, ignoreCase = true) &&
                    !url.startsWith(SCHEME_HTTPS, ignoreCase = true) -> issues.add(
                ValidationIssue(
                    field = BookSourceSchema.BOOK_SOURCE_URL,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_source_url_invalid,
                    detail = url.clip()
                )
            )
        }

        if (json.readString(BookSourceSchema.BOOK_SOURCE_NAME).isBlank()) {
            issues.add(
                ValidationIssue(
                    field = BookSourceSchema.BOOK_SOURCE_NAME,
                    level = IssueLevel.ERROR,
                    messageRes = R.string.ai_issue_source_name_missing
                )
            )
        }

        val typeElement = json.get(BookSourceSchema.BOOK_SOURCE_TYPE)
        if (typeElement != null && !typeElement.isJsonNull) {
            val type = typeElement.readIntOrNull()
            if (type == null || type !in BookSourceSchema.BOOK_SOURCE_TYPES) {
                issues.add(
                    ValidationIssue(
                        field = BookSourceSchema.BOOK_SOURCE_TYPE,
                        level = IssueLevel.ERROR,
                        messageRes = R.string.ai_issue_source_type_invalid,
                        detail = typeElement.toString().clip()
                    )
                )
            }
        }

        // 有搜索规则却没有搜索地址：书源仍可导入（发现页可用），故只给 WARNING。
        val hasSearchRule = json.get(BookSourceSchema.RULE_SEARCH)?.isJsonObject == true
        if (hasSearchRule && json.readString(BookSourceSchema.SEARCH_URL).isBlank()) {
            issues.add(
                ValidationIssue(
                    field = BookSourceSchema.SEARCH_URL,
                    level = IssueLevel.WARNING,
                    messageRes = R.string.ai_issue_search_url_missing
                )
            )
        }
    }

    /* ---------------- 顶层未知字段 ---------------- */

    private fun validateUnknownTopLevelFields(
        json: JsonObject,
        issues: MutableList<ValidationIssue>
    ) {
        json.keySet().forEach { key ->
            if (key !in BookSourceSchema.TOP_LEVEL_FIELDS) {
                issues.add(
                    ValidationIssue(
                        field = key,
                        level = IssueLevel.WARNING,
                        messageRes = R.string.ai_issue_unknown_field,
                        detail = key.clip()
                    )
                )
            }
        }
    }

    /* ---------------- 规则子对象 ---------------- */

    private fun validateRuleObjects(json: JsonObject, issues: MutableList<ValidationIssue>) {
        BookSourceSchema.RULE_OBJECT_FIELDS.forEach { (ruleName, allowedFields) ->
            val element = json.get(ruleName) ?: return@forEach
            if (element.isJsonNull) return@forEach

            if (!element.isJsonObject) {
                issues.add(
                    ValidationIssue(
                        field = ruleName,
                        level = IssueLevel.ERROR,
                        messageRes = R.string.ai_issue_rule_not_object,
                        detail = element.toString().clip()
                    )
                )
                return@forEach
            }

            val ruleObject = element.asJsonObject
            ruleObject.entrySet().forEach { (key, value) ->
                val path = "$ruleName.$key"
                if (key !in allowedFields) {
                    issues.add(
                        ValidationIssue(
                            field = path,
                            level = IssueLevel.WARNING,
                            messageRes = R.string.ai_issue_unknown_rule_field,
                            detail = key.clip()
                        )
                    )
                } else if (!value.isJsonNull && !value.isJsonPrimitive) {
                    issues.add(
                        ValidationIssue(
                            field = path,
                            level = IssueLevel.WARNING,
                            messageRes = R.string.ai_issue_rule_value_not_primitive,
                            detail = value.toString().clip()
                        )
                    )
                }
            }

            BookSourceSchema.CRITICAL_RULE_ENTRIES[ruleName]?.forEach { critical ->
                if (ruleObject.readString(critical).isBlank()) {
                    issues.add(
                        ValidationIssue(
                            field = "$ruleName.$critical",
                            level = IssueLevel.WARNING,
                            messageRes = R.string.ai_issue_critical_rule_missing,
                            detail = critical
                        )
                    )
                }
            }
        }
    }

    /* ---------------- 核心规则存在性 ---------------- */

    private fun validateCoreRulePresence(
        json: JsonObject,
        issues: MutableList<ValidationIssue>
    ) {
        val hasAnyCoreRule = BookSourceSchema.CORE_RULE_FIELDS.any {
            json.get(it)?.isJsonObject == true
        }
        if (!hasAnyCoreRule) {
            issues.add(
                ValidationIssue(
                    field = FIELD_RULES,
                    level = IssueLevel.WARNING,
                    messageRes = R.string.ai_issue_core_rules_missing
                )
            )
        }
    }

    /* ---------------- 小工具 ---------------- */

    /** 读取字符串字段；缺失 / null / 非原始类型一律返回空串。 */
    private fun JsonObject.readString(name: String): String {
        val element = get(name) ?: return ""
        if (element.isJsonNull || !element.isJsonPrimitive) return ""
        return runCatching { element.asString }.getOrDefault("").trim()
    }

    /** 读取整数字段；`"2"` 这类字符串数字也接受，真读不出来才返回 null。 */
    private fun JsonElement.readIntOrNull(): Int? {
        if (!isJsonPrimitive) return null
        return runCatching { asInt }.getOrNull()
    }

    private fun String.clip(): String =
        if (length <= MAX_DETAIL_CHARS) this else take(MAX_DETAIL_CHARS) + "…"
}
