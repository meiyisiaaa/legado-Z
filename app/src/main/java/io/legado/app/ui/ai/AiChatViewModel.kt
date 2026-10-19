package io.legado.app.ui.ai

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiError
import io.legado.app.ai.ExplainInput
import io.legado.app.ai.ExplainOutcome
import io.legado.app.ai.SelectionQaPair
import io.legado.app.ai.SelectionQaSession
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.ai.engine.ExplainEngine
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.PreferKey
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AiChatRole { USER, ASSISTANT }
enum class AiChatStatus { COMPLETE, RUNNING, FAILED, CANCELLED }

data class AiChatItem(
    val id: Long,
    val role: AiChatRole,
    val text: String,
    val footer: String = "",
    val replyTo: Long? = null,
    val status: AiChatStatus = AiChatStatus.COMPLETE
)

/** 所有正文与历史仅由当前面板的选区产生，离开后不持久化。 */
class AiChatViewModel(application: Application) : BaseViewModel(application) {
    private val engine = ExplainEngine(OpenAiClient(okHttpClient))
    private val session = SelectionQaSession()
    private val chatItems = mutableListOf<AiChatItem>()
    private var nextItemId = 1L
    private var running: Job? = null
    private var selection = ""
    private var anchor = ""
    private var pending: Long? = null
    private var explainQuestion: Long? = null

    private val _items = MutableLiveData<List<AiChatItem>>(emptyList())
    val items: LiveData<List<AiChatItem>> get() = _items
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> get() = _loading
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error
    private val preferences = application.defaultSharedPreferences
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PreferKey.aiModel || key == PreferKey.aiEndpoint || key == PreferKey.aiApiKey) {
            clear()
        }
    }

    init { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }

    fun bindSelection(anchorKey: String, text: String) {
        if (anchor != anchorKey || selection != text) {
            clear()
            anchor = anchorKey
            selection = text
        }
    }

    fun ask(selection: String, rawQuestion: String): Boolean {
        val question = rawQuestion.trim()
        if (question.isEmpty()) return reject(R.string.ai_err_question_empty)
        if (question.length > SelectionQaSession.MAX_QUESTION_CHARS) return reject(R.string.ai_err_question_too_long)
        if (selection.isBlank()) return reject(R.string.ai_err_selection_empty)
        if (!guardAvailable()) return false
        if (this.selection != selection) bindSelection(anchor, selection)
        runQuestion(question, explain = false)
        return true
    }

    fun explain(selection: String) {
        if (selection.isBlank()) { reject(R.string.ai_err_selection_empty); return }
        if (!guardAvailable()) return
        if (this.selection != selection) bindSelection(anchor, selection)
        runQuestion(selection, explain = true)
    }

    /** 失败或停止的轮次重用同一问题，不重复添加用户消息。 */
    fun retry() {
        if (_loading.value == true || !guardAvailable()) return
        val item = chatItems.lastOrNull { it.role == AiChatRole.USER } ?: return
        if (item.status != AiChatStatus.FAILED && item.status != AiChatStatus.CANCELLED) return
        runQuestion(item.text, explainQuestion == item.id, item.id)
    }

    private fun runQuestion(question: String, explain: Boolean, retryId: Long? = null) {
        stop()
        val options = AiConfig.snapshot()
        val sessionKey = listOf(anchor, selection, options.endpoint, options.model).joinToString("\u0000")
        if (session.bind(sessionKey)) {
            chatItems.clear()
        }
        val questionId = retryId?.takeIf { id -> chatItems.any { it.id == id } } ?: nextItemId++.also { id ->
            chatItems.add(AiChatItem(id, AiChatRole.USER, question))
        }
        if (explain) explainQuestion = questionId
        updateQuestion(questionId, AiChatStatus.RUNNING)
        pending = questionId
        val token = session.begin()
        val history = session.history
        val input = ExplainInput(selection, AiConfig.readerSnapshot().explainMaxChars)
        _error.value = null
        _loading.value = true
        running = viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    if (explain) engine.explain(options, input)
                    else engine.ask(options, input, question, history)
                }
                if (!session.isCurrent(token)) return@launch
                // 赏析也属于一次成功问答，后续追问能够引用这次回答。
                session.complete(token, SelectionQaPair(if (explain) "赏析所选原文" else question, outcome.answerMarkdown))
                updateQuestion(questionId, AiChatStatus.COMPLETE)
                chatItems.add(AiChatItem(nextItemId++, AiChatRole.ASSISTANT, outcome.answerMarkdown,
                    footer(outcome), replyTo = questionId))
                publish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (session.isCurrent(token)) {
                    updateQuestion(questionId, AiChatStatus.FAILED, context.getString(R.string.ai_qa_failed_retry))
                    _error.value = if (e is AiError) context.getString(e.messageRes)
                        else e.localizedMessage ?: context.getString(R.string.ai_err_unknown)
                }
            } finally {
                if (session.isCurrent(token)) {
                    pending = null
                    running = null
                    _loading.value = false
                }
            }
        }
    }

    fun stop() {
        session.cancel()
        running?.cancel()
        running = null
        pending?.let { updateQuestion(it, AiChatStatus.CANCELLED, context.getString(R.string.ai_qa_cancelled_retry)) }
        pending = null
        _loading.value = false
    }

    fun clear() {
        stop()
        session.clear()
        chatItems.clear()
        explainQuestion = null
        _error.value = null
        publish()
    }

    fun clearError() { _error.value = null }

    private fun updateQuestion(id: Long, status: AiChatStatus, footer: String = "") {
        val index = chatItems.indexOfFirst { it.id == id }
        if (index >= 0) chatItems[index] = chatItems[index].copy(status = status, footer = footer)
        publish()
    }

    private fun publish() { _items.value = chatItems.toList() }
    private fun footer(outcome: ExplainOutcome) = if (outcome.truncated)
        context.getString(R.string.ai_explain_truncated, outcome.sentChars) else ""
    private fun reject(resource: Int): Boolean { _error.value = context.getString(resource); return false }
    private fun guardAvailable(): Boolean = if (AiConfig.isReaderAssistantAvailable()) true else
        reject(if (AiConfig.isConfigured()) R.string.ai_reader_disabled else R.string.ai_not_configured_tip)

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        stop()
        super.onCleared()
    }
}
