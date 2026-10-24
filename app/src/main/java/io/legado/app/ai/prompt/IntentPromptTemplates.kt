package io.legado.app.ai.prompt

import io.legado.app.R

/**
 * 书源生成的「快捷意图」。
 *
 * 存在的理由很实际：绝大多数人不知道该怎么跟模型描述「我要什么规则」，
 * 一句「帮我做个书源」会让模型自由发挥，产出一份哪儿都不对的骨架。
 * 把常见诉求固化成四个可勾选的片段，用户点两下就能得到一段合格的意图描述。
 *
 * @param labelRes 按钮文案资源 id（由 UI 侧解析，内核不碰 Context）
 * @param template 勾选后写进 prompt 的意图片段
 */
enum class SourceIntent(val labelRes: Int, val template: String) {

    SEARCH(
        R.string.ai_intent_search,
        "请重点产出搜索规则：searchUrl（含分页与关键词占位符）以及 ruleSearch 下的 " +
            "bookList / name / author / bookUrl / coverUrl / intro / kind / lastChapter。"
    ),

    TOC(
        R.string.ai_intent_toc,
        "请重点产出目录规则：ruleToc 下的 chapterList / chapterName / chapterUrl，" +
            "若目录分页请一并给出 nextTocUrl。"
    ),

    CONTENT(
        R.string.ai_intent_content,
        "请重点产出正文规则：ruleContent 下的 content，" +
            "并剔除广告与推荐位节点；若正文分页请一并给出 nextContentUrl。"
    ),

    DETAIL(
        R.string.ai_intent_detail,
        "请重点产出详情页规则：ruleBookInfo 下的 name / author / coverUrl / intro / " +
            "kind / lastChapter / tocUrl。"
    );

    companion object {
        /** 固定展示顺序：搜索 → 目录 → 正文 → 详情，与用户配书源的实际步骤一致。 */
        val ALL: List<SourceIntent> = listOf(SEARCH, TOC, CONTENT, DETAIL)
    }
}

/**
 * 把「勾选的意图模板」和「用户自己敲的一句话」合成最终意图文本。
 *
 * 纯字符串拼装，不含任何配置与网络概念，因此可以被 JVM 单测直接覆盖。
 */
object IntentPromptTemplates {

    /**
     * 合成意图。
     *
     * 规则：
     * - 模板按 [SourceIntent.ALL] 的固定顺序排列，与用户勾选先后无关
     *   —— 否则同样的勾选会因点击顺序不同产出不同 prompt，排查问题时无从对比；
     * - 用户手写的内容放在**最后**，让它成为对模板的补充/覆盖说明；
     * - 全空时返回空串，交由 [io.legado.app.ai.GenerateInput] 的既有校验处理。
     *
     * @param selected 用户勾选的模板，允许为空
     * @param extra 用户手写的意图，允许为空
     */
    fun compose(selected: Collection<SourceIntent>, extra: String = ""): String {
        val parts = mutableListOf<String>()
        SourceIntent.ALL.forEach { intent ->
            if (selected.contains(intent)) {
                parts.add(intent.template)
            }
        }
        val custom = extra.trim()
        if (custom.isNotEmpty()) {
            parts.add(custom)
        }
        return parts.joinToString("\n")
    }
}
