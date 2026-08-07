package io.legado.app.ai.client

import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiError
import kotlinx.coroutines.flow.Flow

/**
 * 对话补全客户端抽象。
 *
 * 之所以要抽象出接口而不是直接用 [OpenAiClient]：
 * Engine 层的单测必须能在**零网络**环境下跑通，`FakeAiChatClient` 直接实现本接口即可。
 *
 * 关于角色常量：消息角色统一用 [ChatMessage.ROLE_SYSTEM] / [ChatMessage.ROLE_USER] /
 * [ChatMessage.ROLE_ASSISTANT] 字符串常量表达，**不再另立 `ChatRole` 枚举** ——
 * OpenAI 兼容端点里角色是开放集合（部分端点有 `tool` / `developer`），
 * 用枚举反而会在遇到未知角色时反序列化崩溃。
 */
interface AiChatClient {

    /**
     * 一次性（非流式）对话补全。
     *
     * 实现方必须保证：抛出的异常**全部**是 [AiError] 子类，
     * 唯一例外是协程取消（`CancellationException` 原样上抛）。
     *
     * @param options 配置快照；实现方不得持有其引用，用完即弃
     * @param messages 已由 `PromptBuilder` 组装好的消息列表
     */
    suspend fun chat(options: AiChatOptions, messages: List<ChatMessage>): ChatResponse

    /**
     * 流式对话补全（v1 未实现，接口预留给 v1.1）。
     *
     * 默认实现直接抛 [AiError.StreamNotSupported]，这样新增流式实现时
     * 只需覆写本方法，无需改动任何调用方签名。
     */
    fun chatStream(options: AiChatOptions, messages: List<ChatMessage>): Flow<ChatDelta> =
        throw AiError.StreamNotSupported()
}
