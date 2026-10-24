package io.legado.app.ai

/**
 * 网页抓取器抽象。
 *
 * 隐私红线（架构文档 Q5）：**只有生成模式**可能需要抓取用户显式填写的页面地址；
 * 调试模式 [io.legado.app.ai.engine.SourceDebugEngine] 的构造函数里
 * **不允许出现本接口**，从签名上杜绝「调试时偷偷联网」。
 *
 * 抽象出来的另一个理由是可测试性：`FakePageFetcher` 实现本接口后，
 * `SourceGenEngine` 的单测可以断言「有 html 就绝不发起抓取」。
 */
interface PageFetcher {

    /**
     * 抓取网页并返回 HTML 文本。
     *
     * 实现方必须保证：抛出的异常全部是 [AiError] 子类（典型为 [AiError.FetchFailed]），
     * 协程取消异常原样上抛。
     *
     * @param url http/https 绝对地址
     */
    suspend fun fetch(url: String): String
}
