package io.legado.app.ai.prompt

/**
 * **读者助手全部 prompt 文本的唯一存放地**（与 [PromptTemplates] 同级、同约定）。
 *
 * 拆成两个文件而不是往 [PromptTemplates] 里塞：书源助手面向"开发者调规则"，
 * 读者助手面向"读者读小说"，两套 system prompt 的语气、约束、输出格式毫无交集，
 * 混在一起会让任何一次改动都要担心影响另一条链路。
 *
 * 硬约定：
 * - `PromptBuilder` / Engine / UI 中**禁止**出现内联 prompt 字符串字面量；
 * - prompt 中**严禁**出现 apiKey、endpoint，以及用户未主动选中的章节正文或元信息。
 */
object ReaderPromptTemplates {

    /** 选区问答的 system prompt。 */
    const val SYSTEM_SELECTION_QA = """你是一位耐心的阅读伴读。
用户会主动选中一小段原文，并针对这段文字提出问题。

硬性要求：
1. **只能**依据用户选中的文字作答，不要使用对作品的先验记忆。
2. 选中文字不足以回答时，直接说明"选中内容里没有足够信息"，不要猜测或补写上下文。
3. 回答用简体中文，控制在 300 字以内，用 Markdown 组织（可用小标题、列表、引用）。
4. 引用原文时用 > 引用块，不要复述整段文字。
5. 不要剧透，不要脑补选区前后的剧情。
6. 只回答问题本身，不要输出"根据您提供的片段"这类客套开场白。"""

    /**
     * 选中文本赏析的 system prompt。
     *
     * 与问答的关键差别：这里**没有检索**，只有用户主动选中的一小段文字，
     * 因此明确允许结合通用文学常识，但禁止脑补上下文剧情。
     */
    const val SYSTEM_EXPLAIN = """你是一位懂文学的阅读伴读，擅长把一段文字讲透。
用户会选中正在读的一小段原文，请你为他做赏析。

硬性要求：
1. 只针对**给出的这段文字**展开，不要脑补它前后的剧情，也不要剧透。
2. 依次覆盖三点，用 Markdown 小标题分隔：
   - **文意**：这段在说什么（含生僻字词、典故、文言的白话解释）
   - **写法**：用了什么手法，好在哪里
   - **回味**：一句话点出它的情绪或意味
3. 用简体中文，全文控制在 350 字以内，语气平实，不要堆砌形容词。
4. 若这段文字过短或没有实质内容，如实说明，不要硬凑。"""

    /* ---------------- user prompt 片段 ---------------- */

    const val USER_QUESTION_LABEL = "【我的问题】"
    const val USER_SELECTION_LABEL = "【我选中的原文】"

    const val USER_SELECTION_QA_TAIL = "请只依据我选中的原文回答问题。"
    const val USER_EXPLAIN_TAIL = "请赏析这段文字。"

    /** 无内容时的占位说明，避免 prompt 出现空标签块（与 [PromptTemplates.PLACEHOLDER_NONE] 一致）。 */
    const val PLACEHOLDER_NONE = "（未提供）"

    /** 选区问答完整 system prompt。 */
    fun systemSelectionQa(): String = SYSTEM_SELECTION_QA

    /** 赏析完整 system prompt。 */
    fun systemExplain(): String = SYSTEM_EXPLAIN
}
