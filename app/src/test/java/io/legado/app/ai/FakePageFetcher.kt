package io.legado.app.ai

/**
 * 测试用的网页抓取器。
 *
 * 存在的核心价值是 [callCount]：隐私红线「用户已经粘贴了 HTML 就绝不再联网抓取」
 * 只能靠「断言抓取器一次都没被调用」来守住。
 *
 * @param html 抓取成功时返回的内容
 * @param error 非空时每次抓取都抛出它
 */
class FakePageFetcher(
    private val html: String = "",
    private val error: Throwable? = null
) : PageFetcher {

    private val recordedUrls = mutableListOf<String>()

    /** 依次记录的抓取地址。 */
    val urls: List<String> get() = recordedUrls

    /** 抓取次数；隐私断言的核心指标。 */
    val callCount: Int get() = recordedUrls.size

    override suspend fun fetch(url: String): String {
        recordedUrls.add(url)
        error?.let { throw it }
        return html
    }
}
