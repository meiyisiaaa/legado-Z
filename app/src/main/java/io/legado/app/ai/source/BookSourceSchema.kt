package io.legado.app.ai.source

/**
 * 书源 JSON 的字段 schema 常量。
 *
 * **刻意不 import `io.legado.app.data.entities.BookSource`**：那是 Room + `@Parcelize` 实体，
 * 引入会把 `android.os.Parcelable` / `TextUtils` 拖进 JVM 单测。
 * 防漂移由 `BookSourceSchemaCoverageTest` 用反射保证 —— 上游 legado 一旦新增字段，
 * 测试立刻红灯提醒同步此处。
 *
 * 本文件同时是 `PromptTemplates.RULE_FIELD_CHEATSHEET` 的**唯一数据源**，
 * 避免 prompt 里的字段说明与校验白名单各写一份而漂移。
 */
object BookSourceSchema {

    /* ---------------- 顶层字段 ---------------- */

    const val BOOK_SOURCE_URL = "bookSourceUrl"
    const val BOOK_SOURCE_NAME = "bookSourceName"
    const val BOOK_SOURCE_GROUP = "bookSourceGroup"
    const val BOOK_SOURCE_TYPE = "bookSourceType"
    const val SEARCH_URL = "searchUrl"
    const val EXPLORE_URL = "exploreUrl"
    const val RULE_SEARCH = "ruleSearch"
    const val RULE_EXPLORE = "ruleExplore"
    const val RULE_BOOK_INFO = "ruleBookInfo"
    const val RULE_TOC = "ruleToc"
    const val RULE_CONTENT = "ruleContent"
    const val RULE_REVIEW = "ruleReview"

    /** 预览分组中"基础信息"这一组的标识（非书源字段名）。 */
    const val PREVIEW_GROUP_BASE = "base"

    /** `BookSource` 全部顶层字段白名单，出现白名单外的字段会被 Gson 静默丢弃（→ WARNING）。 */
    val TOP_LEVEL_FIELDS: Set<String> = linkedSetOf(
        BOOK_SOURCE_URL,
        BOOK_SOURCE_NAME,
        BOOK_SOURCE_GROUP,
        BOOK_SOURCE_TYPE,
        "bookUrlPattern",
        "customOrder",
        "enabled",
        "enabledExplore",
        "jsLib",
        "enabledCookieJar",
        "concurrentRate",
        "header",
        "loginUrl",
        "loginUi",
        "loginCheckJs",
        "coverDecodeJs",
        "bookSourceComment",
        "variableComment",
        "lastUpdateTime",
        "respondTime",
        "weight",
        EXPLORE_URL,
        "exploreScreen",
        RULE_EXPLORE,
        SEARCH_URL,
        RULE_SEARCH,
        RULE_BOOK_INFO,
        RULE_TOC,
        RULE_CONTENT,
        RULE_REVIEW,
        "eventListener",
        "customButton"
    )

    /** 缺失即 ERROR 的字段。 */
    val REQUIRED_FIELDS: Set<String> = linkedSetOf(
        BOOK_SOURCE_URL,
        BOOK_SOURCE_NAME
    )

    /** 规则子对象名 → 该对象允许的字段集合。 */
    val RULE_OBJECT_FIELDS: Map<String, Set<String>> = linkedMapOf(
        RULE_SEARCH to linkedSetOf(
            "checkKeyWord",
            "bookList",
            "name",
            "author",
            "intro",
            "kind",
            "lastChapter",
            "updateTime",
            "bookUrl",
            "coverUrl",
            "wordCount"
        ),
        RULE_EXPLORE to linkedSetOf(
            "bookList",
            "name",
            "author",
            "intro",
            "kind",
            "lastChapter",
            "updateTime",
            "bookUrl",
            "coverUrl",
            "wordCount"
        ),
        RULE_BOOK_INFO to linkedSetOf(
            "init",
            "name",
            "author",
            "intro",
            "kind",
            "lastChapter",
            "updateTime",
            "coverUrl",
            "tocUrl",
            "wordCount",
            "canReName",
            "downloadUrls"
        ),
        RULE_TOC to linkedSetOf(
            "preUpdateJs",
            "chapterList",
            "chapterName",
            "chapterUrl",
            "formatJs",
            "isVolume",
            "isVip",
            "isPay",
            "updateTime",
            "nextTocUrl"
        ),
        RULE_CONTENT to linkedSetOf(
            "content",
            "subContent",
            "title",
            "nextContentUrl",
            "webJs",
            "sourceRegex",
            "replaceRegex",
            "imageStyle",
            "imageDecode",
            "payAction",
            "callBackJs"
        ),
        RULE_REVIEW to linkedSetOf(
            "reviewUrl",
            "avatarRule",
            "contentRule",
            "postTimeRule",
            "reviewQuoteUrl",
            "voteUpUrl",
            "voteDownUrl",
            "postReviewUrl",
            "postQuoteUrl",
            "deleteUrl"
        )
    )

    /** 三大核心规则：全缺时给 WARNING（纯发现页书源合法存在，不能硬阻断）。 */
    val CORE_RULE_FIELDS: Set<String> = linkedSetOf(RULE_SEARCH, RULE_TOC, RULE_CONTENT)

    /** 各规则对象中"最关键"的子字段，缺失给 WARNING。 */
    val CRITICAL_RULE_ENTRIES: Map<String, Set<String>> = linkedMapOf(
        RULE_SEARCH to linkedSetOf("bookList", "name", "bookUrl"),
        RULE_EXPLORE to linkedSetOf("bookList", "name", "bookUrl"),
        RULE_TOC to linkedSetOf("chapterList", "chapterName", "chapterUrl"),
        RULE_CONTENT to linkedSetOf("content")
    )

    /** `bookSourceType` 的合法取值：0 文本 / 1 音频 / 2 图片 / 3 文件 / 4 视频。 */
    val BOOK_SOURCE_TYPES: IntRange = 0..4

    /** 预览分组的顺序（同时也是 UI 展示顺序）。 */
    val PREVIEW_GROUPS: Set<String> = linkedSetOf(
        PREVIEW_GROUP_BASE,
        RULE_SEARCH,
        RULE_EXPLORE,
        RULE_BOOK_INFO,
        RULE_TOC,
        RULE_CONTENT
    )

    /** 供 prompt cheatsheet 使用的字段中文说明（只覆盖需要 LLM 理解的关键字段）。 */
    val FIELD_DESCRIPTIONS: Map<String, String> = linkedMapOf(
        BOOK_SOURCE_URL to "书源地址，必须是 http/https 绝对地址，同时作为主键",
        BOOK_SOURCE_NAME to "书源名称，必填",
        BOOK_SOURCE_GROUP to "分组，可空",
        BOOK_SOURCE_TYPE to "类型整数：0 文本 / 1 音频 / 2 图片 / 3 文件 / 4 视频",
        SEARCH_URL to "搜索地址，支持 {{key}} 关键词与 {{page}} 页码占位符",
        EXPLORE_URL to "发现地址，多个用换行分隔，格式为 分类名::地址",
        RULE_SEARCH to "搜索结果列表规则",
        RULE_EXPLORE to "发现结果列表规则",
        RULE_BOOK_INFO to "书籍详情页规则",
        RULE_TOC to "目录页规则",
        RULE_CONTENT to "正文页规则"
    )
}
