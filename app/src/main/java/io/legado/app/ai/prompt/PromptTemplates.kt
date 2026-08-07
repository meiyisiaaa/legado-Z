package io.legado.app.ai.prompt

import io.legado.app.ai.source.BookSourceSchema

/**
 * **全部 prompt 文本的唯一存放地**。
 *
 * 硬约定（架构文档 §7.4）：
 * - `PromptBuilder` / Engine / UI 中**禁止**出现内联 prompt 字符串字面量。
 * - [RULE_FIELD_CHEATSHEET] 由 [BookSourceSchema] 常量拼接生成，而非手抄，避免二者漂移。
 * - prompt 中**严禁**出现 apiKey、endpoint、用户书架/正文内容（`PromptBuilderTest` 断言）。
 */
object PromptTemplates {

    /** 生成模式的 system prompt 主体（不含 cheatsheet，由 [systemGenerate] 拼装）。 */
    const val SYSTEM_GENERATE = """你是「阅读」(legado) 开源阅读器的书源规则专家。
用户会给你一段网页 HTML 片段和一句自然语言意图，你要产出一份可直接导入「阅读」的书源 JSON。

硬性要求：
1. 只输出一个 JSON 对象，不要输出任何解释、前后缀文字或 markdown 代码围栏。
2. JSON 必须是合法的标准 JSON：双引号、无尾随逗号、无注释。
3. 只使用下方「字段速查」中列出的字段名，不要臆造字段。
4. bookSourceUrl 填网站根地址（http/https 绝对地址），bookSourceName 填网站名称。
5. 规则表达式优先使用 CSS 选择器语法（如 `.book-list li@a@text`），
   取属性用 `@href` / `@src` / `@text` / `@html`，列表规则写在 bookList / chapterList 上。
6. 拿不准的字段直接省略，不要填空字符串占位，更不要编造不存在的选择器。"""

    /** 调试模式的 system prompt 主体。 */
    const val SYSTEM_DEBUG = """你是「阅读」(legado) 开源阅读器的书源规则调试专家。
用户会给你一份现有书源 JSON、一段样例 HTML 片段以及故障症状，你要定位问题并给出修正后的书源 JSON。

硬性要求：
1. 先输出一段简明的中文诊断建议（不超过 10 行，逐条说明哪个字段为什么不对）。
2. 然后另起一行输出修正后的**完整**书源 JSON，用 ```json 代码围栏包裹。
3. JSON 必须合法：双引号、无尾随逗号、无注释；只使用下方「字段速查」列出的字段名。
4. 保留原书源中你未改动的字段，不要凭空删减。
5. 若样例 HTML 不足以判断某条规则，请在建议中说明，并保持该字段原样。"""

    /** 自动修复重试的 system prompt（temperature 会被强制拉到 0）。 */
    const val SYSTEM_REPAIR = """你是一个严格的 JSON 修复器。
用户会给你一段"本应是书源 JSON 但格式有问题"的文本，以及一份问题清单。
你只需要输出修复后的合法 JSON 对象本身：
- 不要输出任何解释、道歉、前后缀文字。
- 不要输出 markdown 代码围栏。
- 不要新增问题清单之外的字段改动。"""

    /* ---------------- user prompt 片段 ---------------- */

    const val USER_INTENT_LABEL = "【我的意图】"
    const val USER_PAGE_URL_LABEL = "【页面地址】"
    const val USER_HTML_LABEL = "【网页 HTML 片段】"
    const val USER_SOURCE_JSON_LABEL = "【现有书源 JSON】"
    const val USER_SYMPTOM_LABEL = "【故障症状】"
    const val USER_SAMPLE_HTML_LABEL = "【样例 HTML 片段】"
    const val USER_RAW_REPLY_LABEL = "【待修复文本】"
    const val USER_ISSUES_LABEL = "【问题清单】"

    const val USER_GENERATE_TAIL = "请据此产出书源 JSON。"
    const val USER_DEBUG_TAIL = "请先给出诊断建议，再给出修正后的完整书源 JSON。"
    const val USER_REPAIR_TAIL = "请输出修复后的合法 JSON 对象。"

    const val CHEATSHEET_TITLE = "【字段速查】"
    const val CHEATSHEET_TOP_LEVEL_TITLE = "顶层字段："
    const val CHEATSHEET_RULE_TITLE = "规则子对象可用字段："

    /** 无样例 HTML / 无意图时的占位说明，避免 prompt 出现空标签块。 */
    const val PLACEHOLDER_NONE = "（未提供）"

    /**
     * 由 [BookSourceSchema] 拼接生成的字段速查表。
     *
     * 用 `val` 而非 `const val`：内容需要在运行期从 schema 常量派生，
     * 这是 §7.4「与 BookSourceSchema 同源引用」优先于「const val」的必然结果。
     */
    val RULE_FIELD_CHEATSHEET: String = buildCheatsheet()

    /** 生成模式完整 system prompt。 */
    fun systemGenerate(): String = SYSTEM_GENERATE + "\n\n" + RULE_FIELD_CHEATSHEET

    /** 调试模式完整 system prompt。 */
    fun systemDebug(): String = SYSTEM_DEBUG + "\n\n" + RULE_FIELD_CHEATSHEET

    /** 修复模式完整 system prompt（不带 cheatsheet，省 token）。 */
    fun systemRepair(): String = SYSTEM_REPAIR

    private fun buildCheatsheet(): String = buildString {
        append(CHEATSHEET_TITLE).append('\n')
        append(CHEATSHEET_TOP_LEVEL_TITLE).append('\n')
        BookSourceSchema.TOP_LEVEL_FIELDS.forEach { field ->
            append("- ").append(field)
            BookSourceSchema.FIELD_DESCRIPTIONS[field]?.let { append("：").append(it) }
            append('\n')
        }
        append('\n').append(CHEATSHEET_RULE_TITLE).append('\n')
        BookSourceSchema.RULE_OBJECT_FIELDS.forEach { (ruleName, fields) ->
            append("- ").append(ruleName).append(": ")
                .append(fields.joinToString(", "))
                .append('\n')
        }
    }
}
