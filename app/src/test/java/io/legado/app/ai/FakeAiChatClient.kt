package io.legado.app.ai

import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.client.ChatResponse
import io.legado.app.ai.client.Choice
import io.legado.app.ai.client.Usage

/**
 * 测试用的对话客户端：按顺序吐出预设回复，并录下每次调用的入参。
 *
 * 录参数是关键 —— Engine 的自动修复逻辑必须能被断言
 * 「第二次调用把 temperature 拉到了 0」「第二次调用带上了问题清单」。
 *
 * @param replies 依次返回的回复文本；用完后重复最后一条
 * @param usage 每次调用回传的用量，用于验证多轮用量累加
 * @param error 非空时**每次**调用都直接抛出它
 */
class FakeAiChatClient(
    private val replies: List<String> = listOf(""),
    private val usage: Usage? = null,
    private val error: Throwable? = null
) : AiChatClient {

    private val recordedMessages = mutableListOf<List<ChatMessage>>()
    private val recordedOptions = mutableListOf<AiChatOptions>()
    private var index = 0

    /** 依次记录的消息列表。 */
    val messagesSeen: List<List<ChatMessage>> get() = recordedMessages

    /** 依次记录的配置快照。 */
    val optionsSeen: List<AiChatOptions> get() = recordedOptions

    /** 被调用的次数。 */
    val callCount: Int get() = recordedMessages.size

    override suspend fun chat(
        options: AiChatOptions,
        messages: List<ChatMessage>
    ): ChatResponse {
        recordedOptions.add(options)
        recordedMessages.add(messages)
        error?.let { throw it }

        val content = replies.getOrElse(index) { replies.lastOrNull().orEmpty() }
        index++

        return ChatResponse(
            id = "fake-$index",
            model = options.model,
            choices = listOf(
                Choice(
                    index = 0,
                    message = ChatMessage.assistant(content),
                    finishReason = "stop"
                )
            ),
            usage = usage
        )
    }

    /** 把所有轮次的消息内容拼起来，便于做「绝不含 apiKey」这类全局断言。 */
    fun allContent(): String =
        recordedMessages.flatten().joinToString("\n") { it.content }

    companion object {

        /** 只回一条内容的便捷构造。 */
        fun replying(vararg replies: String, usage: Usage? = null): FakeAiChatClient =
            FakeAiChatClient(replies.toList(), usage)

        /** 每次调用都失败的便捷构造。 */
        fun failing(error: Throwable): FakeAiChatClient =
            FakeAiChatClient(listOf(""), null, error)
    }
}
