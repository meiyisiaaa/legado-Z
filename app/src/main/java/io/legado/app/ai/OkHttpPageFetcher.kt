package io.legado.app.ai

import io.legado.app.ai.client.awaitAiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 基于 OkHttp 的网页抓取实现。
 *
 * 隐私与合规要点：
 * - **强制 [CookieJar.NO_COOKIES]**：绝不携带用户在书源里登录过的 Cookie 去抓页面，
 *   否则「帮我生成书源」可能顺手把登录态发到第三方站点。
 * - **从传入的 base client 派生**（`newBuilder()`），满足架构文档 §7.2
 *   「禁止直接新建 OkHttpClient」；连接池、DNS、代理设置都继承工程既有配置。
 * - 只抓取用户**显式填写**的地址，不做任何跟随式二次抓取。
 *
 * @param baseClient 工程既有的 OkHttp 实例，由 UI 层注入
 * @param timeoutSec 单次抓取超时秒数
 * @param maxChars 最多保留的 HTML 字符数，防止超大页面撑爆内存
 */
class OkHttpPageFetcher(
    baseClient: OkHttpClient,
    private val timeoutSec: Int = AiChatOptions.DEFAULT_TIMEOUT_SEC,
    private val maxChars: Int = DEFAULT_MAX_CHARS
) : PageFetcher {

    private val client: OkHttpClient = baseClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(true)
        .build()

    override suspend fun fetch(url: String): String {
        val target = url.trim()
        if (target.isBlank()) throw AiError.FetchFailed(EMPTY_URL_DETAIL)
        if (!target.startsWith(SCHEME_HTTP, true) && !target.startsWith(SCHEME_HTTPS, true)) {
            throw AiError.FetchFailed(target)
        }

        val request = runCatching {
            Request.Builder()
                .url(target)
                .header(HEADER_USER_AGENT, DEFAULT_USER_AGENT)
                .header(HEADER_ACCEPT, ACCEPT_HTML)
                .get()
                .build()
        }.getOrElse { throw AiError.FetchFailed(target, it) }

        val timeoutMillis = timeoutSec.coerceAtLeast(1).toLong() * MILLIS_PER_SECOND
        return try {
            withTimeout(timeoutMillis) {
                client.newCall(request).awaitAiResponse().use { response ->
                    if (!response.isSuccessful) {
                        throw AiError.FetchFailed("$target HTTP ${response.code}")
                    }
                    val text = runCatching { response.body.string() }.getOrDefault("")
                    if (text.isBlank()) throw AiError.FetchFailed("$target empty body")
                    text.take(maxChars)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AiError.Timeout("$target ${timeoutSec}s", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e is AiError) throw e
            throw AiError.FetchFailed(target, e)
        }
    }

    companion object {
        /** 单页最多保留 512K 字符，后续还会被 `HtmlChunker` 进一步压到 prompt 尺寸。 */
        const val DEFAULT_MAX_CHARS = 512 * 1024

        private const val MILLIS_PER_SECOND = 1000L
        private const val SCHEME_HTTP = "http://"
        private const val SCHEME_HTTPS = "https://"
        private const val EMPTY_URL_DETAIL = "empty url"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_ACCEPT = "Accept"
        private const val ACCEPT_HTML =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }
}
