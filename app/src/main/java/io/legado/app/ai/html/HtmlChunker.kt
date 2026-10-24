package io.legado.app.ai.html

import io.legado.app.ai.AiChatOptions
import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import kotlin.math.ceil

/**
 * 把整页 HTML 压成「可以塞进 prompt 的干净片段」。
 *
 * 设计要点（架构文档 Q2）：
 * 1. **按字符长度截断**，不按 token —— 本地做 token 化既不准也不值当，
 *    真正的成本护栏由 [AiChatOptions.maxTokens] 与端点侧共同兜底。
 * 2. 截断后**必须二次 Jsoup 解析**：裸切会切出 `<div class="boo` 这种半截标签，
 *    模型会顺着残缺结构瞎猜选择器。二次解析由 Jsoup 补全闭合标签，输出合法片段。
 * 3. [chunk] 的返回值**长度硬保证** `<= maxChars`：补闭合标签会让长度回涨，
 *    因此采用「切 → 修复 → 超了就缩小切点重来」的收敛循环，
 *    连续收敛失败时退化为纯文本（无标签，必然不会再被撑大）。
 *
 * 零 Android 依赖，可直接在 JVM 单测中运行（Jsoup 1.16.2 是纯 Java 库）。
 */
object HtmlChunker {

    /** 默认片段上限，与 [AiChatOptions.DEFAULT_HTML_MAX_CHARS] 同源。 */
    const val DEFAULT_MAX_CHARS = AiChatOptions.DEFAULT_HTML_MAX_CHARS

    /** 再小就没有解析价值了，低于此值一律抬到此值。 */
    const val MIN_MAX_CHARS = 200

    /** 对生成规则毫无帮助、却极占 token 的噪音节点。 */
    private const val NOISE_SELECTOR =
        "script, style, noscript, svg, canvas, template, iframe, link, meta"

    private const val ATTR_STYLE = "style"
    private const val ATTR_EVENT_PREFIX = "on"

    /** 每轮收敛把切点缩小到 85%。 */
    private const val SHRINK_FACTOR = 0.85

    /** 最多尝试 4 轮收敛，之后直接退化为纯文本。 */
    private const val MAX_REPAIR_ATTEMPTS = 4

    /** 粗略 token 估算：非 CJK 字符按 4 字符 ≈ 1 token。 */
    private const val ASCII_CHARS_PER_TOKEN = 4.0

    private val HORIZONTAL_SPACE = Regex("[ \\t\\x0B\\u000C\\u00A0]+")
    private val BLANK_LINES = Regex("(\\r?\\n[ \\t]*){2,}")

    /**
     * 清洗并截断 HTML。
     *
     * @param html 原始 HTML，可以是整页也可以是片段；空白输入返回空串
     * @param maxChars 片段字符上限，小于 [MIN_MAX_CHARS] 时按 [MIN_MAX_CHARS] 处理
     * @return 结构合法（标签成对）且长度不超过 `maxChars` 的 HTML 片段
     */
    fun chunk(html: String, maxChars: Int = DEFAULT_MAX_CHARS): String {
        if (html.isBlank()) return ""
        val limit = maxChars.coerceAtLeast(MIN_MAX_CHARS)
        val cleaned = clean(html)
        if (cleaned.isEmpty()) return ""
        if (cleaned.length <= limit) return cleaned

        var cut = limit
        var attempt = 0
        while (attempt < MAX_REPAIR_ATTEMPTS && cut >= MIN_MAX_CHARS) {
            val repaired = repair(cleaned.take(cut))
            if (repaired.length <= limit) return repaired
            cut = (cut * SHRINK_FACTOR).toInt()
            attempt++
        }
        // 收敛失败的兜底：纯文本没有标签，截断后不会再被闭合标签撑大。
        return plainText(cleaned).take(limit)
    }

    /**
     * 粗略估算一段文本的 token 数，仅用于 UI 预告成本，**不参与任何限流决策**。
     *
     * CJK 字符按 1 字 ≈ 1 token，其余按 [ASCII_CHARS_PER_TOKEN] 字符 ≈ 1 token。
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        text.forEach { ch ->
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + ceil(other / ASCII_CHARS_PER_TOKEN).toInt()
    }

    /** 估算一组消息文本的总 token 数。 */
    fun estimateTokens(texts: Collection<String>): Int = texts.sumOf { estimateTokens(it) }

    /* ---------------- 内部实现 ---------------- */

    /** 去噪 + 去噪音属性 + 压空白，产出紧凑但结构完整的 body HTML。 */
    private fun clean(html: String): String {
        val doc = Jsoup.parse(html)
        doc.outputSettings().prettyPrint(false)
        doc.select(NOISE_SELECTOR).remove()
        removeComments(doc)
        stripNoisyAttributes(doc)
        val raw = doc.body()?.html() ?: doc.html()
        return compressWhitespace(raw)
    }

    /** 二次解析：Jsoup 会自动补全被截断的标签，保证输出片段结构合法。 */
    private fun repair(fragment: String): String {
        val doc = Jsoup.parseBodyFragment(fragment)
        doc.outputSettings().prettyPrint(false)
        return compressWhitespace(doc.body()?.html().orEmpty())
    }

    /** 退化路径：只保留可见文本。 */
    private fun plainText(fragment: String): String {
        val doc = Jsoup.parseBodyFragment(fragment)
        return compressWhitespace(doc.text())
    }

    private fun removeComments(doc: Document) {
        doc.getAllElements().forEach { element ->
            element.childNodes().toList().forEach { node ->
                if (node is Comment) node.remove()
            }
        }
    }

    /**
     * 删除 `style` 与所有 `on*` 事件属性。
     *
     * 这两类属性对「推断选择器」零帮助，却经常占掉整页一半以上的字符，
     * 删掉等价于免费扩大有效上下文窗口。`class` / `id` / `href` 必须保留。
     */
    private fun stripNoisyAttributes(doc: Document) {
        doc.getAllElements().forEach { element ->
            element.attributes().asList().forEach { attribute ->
                val key = attribute.key.lowercase()
                if (key == ATTR_STYLE || key.startsWith(ATTR_EVENT_PREFIX)) {
                    element.removeAttr(attribute.key)
                }
            }
        }
    }

    private fun compressWhitespace(raw: String): String = raw
        .replace(HORIZONTAL_SPACE, " ")
        .replace(BLANK_LINES, "\n")
        .trim()

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x2E80..0x9FFF ||
                code in 0xF900..0xFAFF ||
                code in 0xFE30..0xFE4F ||
                code in 0xFF00..0xFFEF
    }
}
