package io.legado.app.ai.retrieval

/**
 * 面向中英混排小说正文的极简分词器。
 *
 * 设计取舍（架构文档 §4.2）：**不引入任何分词库**。
 * 理由是工程要塞进 APK 的中文词典动辄数 MB，而检索场景只需要「能对齐关键词」，
 * 并不需要语言学意义上的正确切分。因此采用业界成熟的降级方案：
 *
 * - 英文 / 数字：按空白与标点切分，整体小写
 * - 中文：**bigram（二元切分）** —— "天下无双" → ["天下","下无","无双"]
 *
 * bigram 的召回率显著高于单字，且天然处理未登录词（人名、地名、生造词），
 * 对本场景（用户拿书里的原词提问）足够好。
 */
object Tokenizer {

    /**
     * 归一化：小写化，并把所有非「字母/数字/CJK」字符统一折叠为单个空格。
     *
     * 归一化后的串同时用于分词与「原样子串匹配」，保证两者口径一致。
     */
    fun normalize(text: String): String {
        if (text.isEmpty()) return ""
        val sb = StringBuilder(text.length)
        var lastWasSpace = true
        for (ch in text) {
            if (isWordChar(ch)) {
                sb.append(ch.lowercaseChar())
                lastWasSpace = false
            } else if (!lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
            }
        }
        // 末尾可能残留一个分隔空格
        if (sb.isNotEmpty() && sb.last() == ' ') {
            sb.setLength(sb.length - 1)
        }
        return sb.toString()
    }

    /**
     * 切词。返回值**保留出现顺序且允许重复** —— 词频与词序（邻近度）都是打分要用的信号。
     *
     * @return 形如 `["天下","下无","无双","chapter","3"]`
     */
    fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val tokens = ArrayList<String>()
        val run = StringBuilder()
        var runIsCjk = false

        fun flushRun() {
            if (run.isEmpty()) return
            if (runIsCjk) {
                appendCjkGrams(run, tokens)
            } else {
                tokens.add(run.toString())
            }
            run.setLength(0)
        }

        for (raw in text) {
            if (!isWordChar(raw)) {
                flushRun()
                continue
            }
            val ch = raw.lowercaseChar()
            val cjk = isCjk(ch)
            if (run.isNotEmpty() && cjk != runIsCjk) {
                flushRun()
            }
            runIsCjk = cjk
            run.append(ch)
        }
        flushRun()
        return tokens
    }

    /**
     * 切词并去重，保持首次出现顺序。
     * 用于「查询词集合」——查询里重复写同一个词不应放大权重。
     */
    fun distinctTokens(text: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (token in tokenize(text)) {
            seen.add(token)
        }
        return seen.toList()
    }

    /** 把一段连续 CJK 文本按 bigram 追加进 [out]；长度为 1 时退化为单字。 */
    private fun appendCjkGrams(run: CharSequence, out: MutableList<String>) {
        if (run.length == 1) {
            out.add(run.toString())
            return
        }
        for (i in 0 until run.length - 1) {
            out.add(run.substring(i, i + 2))
        }
    }

    /** 字母、数字或 CJK 视为构词字符，其余（标点、空白、emoji 等）一律视为分隔符。 */
    private fun isWordChar(ch: Char): Boolean = ch.isLetterOrDigit()

    /**
     * 是否属于中日韩表意文字区。
     *
     * 覆盖：CJK 统一表意文字（含扩展 A）、兼容表意文字、假名。
     * 不含全角标点 —— 它们本就会被 [isWordChar] 挡掉。
     */
    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3040..0x30FF ||   // 平假名 / 片假名
                code in 0x3400..0x4DBF ||  // CJK 扩展 A
                code in 0x4E00..0x9FFF ||  // CJK 统一表意文字
                code in 0xF900..0xFAFF     // CJK 兼容表意文字
    }
}
