package io.legado.app.ui.ai

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiError
import io.legado.app.ai.ExplainInput
import io.legado.app.ai.ExplainOutcome
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.ai.engine.ExplainEngine
import io.legado.app.base.BaseViewModel
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.okHttpClient

/** 对话流中一条消息的角色。 */
enum class AiChatRole {
    /** 用户提问 / 用户选中的原文 */
    USER,

    /** 模型回答，按 Markdown 渲染 */
    ASSISTANT
}

/**
 * 对话流的一条列表项。
 *
 * 选区问答和赏析共用同一条消息模型。
 */
data class AiChatItem(
    val id: Long,
    val role: AiChatRole,
    val text: String,
    /** 附加信息行（目前用于截断提示），为空则不展示 */
    val footer: String = ""
)

/**
 * 阅读器选区 AI 助手（问答 + 赏析）的 ViewModel。
 *
 * 职责边界：
 * - 书籍正文只来自用户主动选中的文本，不读取缓存章节；
 * - 配置每次现取（[AiConfig.snapshot] / [AiConfig.readerSnapshot]），不缓存；
 * - 网络客户端由 [okHttpClient] 派生（§7.2 禁止 new OkHttpClient）。
 */
class AiChatViewModel(application: Application) : BaseViewModel(application) {

    private val chatClient: AiChatClient = OpenAiClient(okHttpClient)
    private val explainEngine = ExplainEngine(chatClient)

    private val chatItems = mutableListOf<AiChatItem>()
    private var nextItemId = 1L
    private var running: Coroutine<*>? = null

    private val _items = MutableLiveData<List<AiChatItem>>(emptyList())
    val items: LiveData<List<AiChatItem>> get() = _items

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> get() = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    /** 只依据用户主动选中的文本回答问题。 */
    fun ask(selection: String, rawQuestion: String) {
        val text = selection.trim()
        val question = rawQuestion.trim()
        if (question.isEmpty()) {
            _error.postValue(context.getString(R.string.ai_err_question_empty))
            return
        }
        if (text.isEmpty()) {
            _error.postValue(context.getString(R.string.ai_err_selection_empty))
            return
        }
        if (!guardAvailable()) return

        appendItem(AiChatRole.USER, question)
        beginRun()

        val options = AiConfig.snapshot()
        val reader = AiConfig.readerSnapshot()

        running = execute {
            explainEngine.ask(
                options,
                ExplainInput(selection = text, maxChars = reader.explainMaxChars),
                question
            )
        }.onSuccess { outcome ->
            _loading.postValue(false)
            appendItem(AiChatRole.ASSISTANT, outcome.answerMarkdown, buildExplainFooter(outcome))
        }.onError { e ->
            _loading.postValue(false)
            _error.postValue(mapError(e))
        }
    }

    /* ==================== 赏析 ==================== */

    /**
     * 赏析用户选中的一段原文。
     *
     * 隐私：只送出 [selection]，所在章节的其余内容一个字都不上传。
     */
    fun explain(selection: String) {
        val text = selection.trim()
        if (text.isEmpty()) {
            _error.postValue(context.getString(R.string.ai_err_selection_empty))
            return
        }
        if (!guardAvailable()) return

        appendItem(AiChatRole.USER, text)
        beginRun()

        val options = AiConfig.snapshot()
        val reader = AiConfig.readerSnapshot()
        running = execute {
            explainEngine.explain(
                options,
                ExplainInput(
                    selection = text,
                    maxChars = reader.explainMaxChars
                )
            )
        }.onSuccess { outcome ->
            _loading.postValue(false)
            appendItem(AiChatRole.ASSISTANT, outcome.answerMarkdown, buildExplainFooter(outcome))
        }.onError { e ->
            _loading.postValue(false)
            _error.postValue(mapError(e))
        }
    }

    private fun buildExplainFooter(outcome: ExplainOutcome): String =
        if (outcome.truncated) {
            context.getString(R.string.ai_explain_truncated, outcome.sentChars)
        } else {
            ""
        }

    /* ==================== 通用 ==================== */

    /** 中止当前请求。用户点「停止」或关闭面板时调用。 */
    fun stop() {
        running?.cancel()
        running = null
        _loading.postValue(false)
    }

    /** 清空对话与上下文。 */
    fun clear() {
        stop()
        chatItems.clear()
        _items.postValue(emptyList())
    }

    fun clearError() {
        _error.postValue(null)
    }

    override fun onCleared() {
        super.onCleared()
        running?.cancel()
    }

    /** 总开关 + 三要素配置的统一守卫，不通过就提示并返回 false。 */
    private fun guardAvailable(): Boolean {
        if (!AiConfig.isReaderAssistantAvailable()) {
            val tip = if (AiConfig.isConfigured()) {
                R.string.ai_reader_disabled
            } else {
                R.string.ai_not_configured_tip
            }
            _error.postValue(context.getString(tip))
            return false
        }
        return true
    }

    private fun beginRun() {
        running?.cancel()
        _error.postValue(null)
        _loading.postValue(true)
    }

    private fun appendItem(
        role: AiChatRole,
        text: String,
        footer: String = ""
    ) {
        chatItems.add(
            AiChatItem(
                id = nextItemId++,
                role = role,
                text = text,
                footer = footer
            )
        )
        _items.postValue(chatItems.toList())
    }

    private fun mapError(e: Throwable): String {
        return if (e is AiError) {
            context.getString(e.messageRes)
        } else {
            e.localizedMessage ?: e.message ?: context.getString(R.string.ai_err_unknown)
        }
    }

}
