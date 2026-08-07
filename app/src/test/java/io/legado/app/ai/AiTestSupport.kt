package io.legado.app.ai

/**
 * `io.legado.app.ai` 测试的共用夹具与断言工具。
 *
 * 抽出来的理由很实际：书源 JSON 样本要在 Engine / Validator / Preview 三处反复用，
 * 各写一份迟早会漂移，到时候「校验器测试通过、引擎测试失败」会非常难查。
 */
internal object AiTestFixtures {

    /** 一份零问题的最小可用书源。 */
    const val VALID_SOURCE_JSON = """
        {
          "bookSourceUrl": "https://example.com",
          "bookSourceName": "示例书源",
          "bookSourceType": 0,
          "searchUrl": "https://example.com/s?q={{key}}",
          "ruleSearch": {
            "bookList": ".book-list li",
            "name": "a@text",
            "bookUrl": "a@href"
          },
          "ruleToc": {
            "chapterList": ".chapter-list li",
            "chapterName": "a@text",
            "chapterUrl": "a@href"
          },
          "ruleContent": {
            "content": "#content@html"
          }
        }
    """

    /** 缺主键的书源：语法合法但校验必然 ERROR，用于验证自动修复触发条件二。 */
    const val SOURCE_JSON_MISSING_URL = """
        {
          "bookSourceName": "示例书源",
          "searchUrl": "https://example.com/s?q={{key}}",
          "ruleSearch": { "bookList": ".list li", "name": "a@text", "bookUrl": "a@href" }
        }
    """

    /** 一份可用的配置快照。 */
    fun options(
        autoRepair: Boolean = AiChatOptions.DEFAULT_AUTO_REPAIR,
        apiKey: String = "sk-unit-test-key-0123",
        timeoutSec: Int = AiChatOptions.DEFAULT_TIMEOUT_SEC
    ): AiChatOptions = AiChatOptions(
        endpoint = "https://api.example.com/v1",
        apiKey = apiKey,
        model = "gpt-4o-mini",
        timeoutSec = timeoutSec,
        autoRepair = autoRepair
    )

    /** 把 JSON 包进 markdown 围栏，模拟模型最常见的"不听话"输出。 */
    fun fenced(json: String, prefix: String = ""): String =
        "$prefix\n```json\n${json.trimIndent()}\n```\n"
}

/**
 * 断言 [block] 抛出 [T]，并把异常返回供进一步断言。
 *
 * 不用 `Assert.fail()` + try/catch 的写法：`fail()` 抛的 `AssertionError`
 * 会被宽泛的 catch 吞掉，导致"没抛异常"的用例反而通过 —— 这是个经典陷阱。
 */
internal inline fun <reified T : Throwable> assertThrowsOf(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError(
            "期望 ${T::class.java.simpleName}，实际抛出 ${e::class.java.simpleName}：${e.message}"
        )
    }
    throw AssertionError("期望 ${T::class.java.simpleName}，但没有抛出任何异常")
}
