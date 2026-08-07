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
 * - prompt 中**严禁**出现 apiKey、endpoint，以及任何**未被检索命中**的章节正文。
 */
object ReaderPromptTemplates {

    /**
     * 检索问答的 system prompt。
     *
     * 三条硬约束都是为了对抗大模型在 RAG 场景下最常见的两种失败：
     * 「拿训练数据里的同名小说糊弄」和「片段里没有就自由发挥」。
     */
    const val SYSTEM_QA = """你是一位耐心的读书伙伴，正在陪用户读一本小说。
用户会给你若干段**从这本书里检索出来的原文片段**，以及一个问题。

硬性要求：
1. **只能**依据给出的原文片段作答，绝不使用你对同名作品的任何先验记忆。
2. 片段里找不到答案时，直接说"给出的片段里没有提到"，并简要说明片段讲了什么，
   不要猜测、不要编造情节、不要用"可能""也许"来搪塞。
3. 回答用简体中文，控制在 300 字以内，用 Markdown 组织（可用小标题、列表、引用）。
4. 引用原文时用 > 引用块，并在句末标注片段编号，如 `（片段 2）`。
5. 不要复述整段片段，只摘取与问题直接相关的部分。
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

    const val USER_BOOK_LABEL = "【书名】"
    const val USER_CHAPTER_LABEL = "【所在章节】"
    const val USER_FRAGMENTS_LABEL = "【原文片段】"
    const val USER_HISTORY_LABEL = "【此前的对话】"
    const val USER_QUESTION_LABEL = "【我的问题】"
    const val USER_SELECTION_LABEL = "【我选中的原文】"

    const val USER_QA_TAIL = "请只依据上面的原文片段回答我的问题。"
    const val USER_EXPLAIN_TAIL = "请赏析这段文字。"

    /** 历史轮次的行前缀。 */
    const val HISTORY_QUESTION_PREFIX = "问："
    const val HISTORY_ANSWER_PREFIX = "答："

    /** 片段区块的表头模板：`片段 1｜第 12 章 · 归途`。 */
    const val FRAGMENT_HEADER_FORMAT = "片段 %d｜第 %d 章 · %s"

    /** 章节标题缺失时的占位。 */
    const val UNTITLED_CHAPTER = "（无标题）"

    /** 无内容时的占位说明，避免 prompt 出现空标签块（与 [PromptTemplates.PLACEHOLDER_NONE] 一致）。 */
    const val PLACEHOLDER_NONE = "（未提供）"

    /** 问答完整 system prompt。 */
    fun systemQa(): String = SYSTEM_QA

    /** 赏析完整 system prompt。 */
    fun systemExplain(): String = SYSTEM_EXPLAIN
}
