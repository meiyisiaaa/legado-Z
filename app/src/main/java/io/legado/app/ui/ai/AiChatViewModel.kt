package io.legado.app.ui.ai

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiError
import io.legado.app.ai.ChatTurn
import io.legado.app.ai.Citation
import io.legado.app.ai.ExplainInput
import io.legado.app.ai.ExplainOutcome
import io.legado.app.ai.QaInput
import io.legado.app.ai.QaOutcome
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.ai.engine.ExplainEngine
import io.legado.app.ai.engine.ReaderQaEngine
import io.legado.app.ai.retrieval.BookRetriever
import io.legado.app.ai.retrieval.RetrievalRequest
import io.legado.app.base.BaseViewModel
import io.legado.app.help.ai.CachedBookTextProvider
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.ReadBook

/** 对话流中一条消息的角色。 */
enum class AiChatRole {
    /** 用户提问 / 用户选中的原文 */
    USER,

    /** 模型回答，按 Markdown 渲染 */
    ASSISTANT,

    /** 系统提示（零命中、免责声明等），不参与上下文回喂 */
    NOTICE,

    /** 出处条目，可点击跳转到对应章节 */
    CITATION
}

/**
 * 对话流的一条列表项。
 *
 * 出处**单独成项**而不是塞进回答的附属视图：这样点击跳转可以直接复用
 * RecyclerView 既有的条目点击链路，不用在 adapter 里动态 addView，
 * 也不用为「一条回答有几个出处」写一套自定义容器。
 */
data class AiChatItem(
    val id: Long,
    val role: AiChatRole,
    val text: String,
    /** 附加信息行（检索统计 / 截断提示 / 历史省略提示），为空则不展示 */
    val footer: String = "",
    /** 仅 [AiChatRole.CITATION] 有值 */
    val citation: Citation? = null
)

/**
 * 阅读器 AI 助手（问答 + 赏析）的 ViewModel。
 *
 * 职责边界：
 * - **检索在这里发起**（[BookRetriever] + [CachedBookTextProvider]），
 *   拿到结果后才交给 [ReaderQaEngine]；
 * - 零命中时**直接返回提示、不调引擎、不发请求** —— 引擎里的
 *   [AiError.RetrievalEmpty] 只是最后一道保险，正常路径根本走不到；
 * - 配置每次现取（[AiConfig.snapshot] / [AiConfig.readerSnapshot]），不缓存；
 * - 网络客户端由 [okHttpClient] 派生（§7.2 禁止 new OkHttpClient）。
 */
class AiChatViewModel(application: Application) : BaseViewModel(application) {

    private val chatClient: AiChatClient = OpenAiClient(okHttpClient)
    private val qaEngine = ReaderQaEngine(chatClient)
    private val explainEngine = ExplainEngine(chatClient)

    /** 多轮上下文，由旧到新；只存问答文本，不存片段。 */
    private val history = mutableListOf<ChatTurn>()

    private val chatItems = mutableListOf<AiChatItem>()
    private var nextItemId = 1L
    private var running: Coroutine<*>? = null

    /** 最近一次提问，供「扩大到全书重试」复用。 */
    private var lastQuestion: String = ""

    private val _items = MutableLiveData<List<AiChatItem>>(emptyList())
    val items: LiveData<List<AiChatItem>> get() = _items

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> get() = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    /** 是否展示「扩大到全书重试」按钮（决策 A1 的二次操作）。 */
    private val _canExpand = MutableLiveData(false)
    val canExpand: LiveData<Boolean> get() = _canExpand

    /** 当前检索范围的说明文案。 */
    private val _scopeText = MutableLiveData(
        application.getString(R.string.ai_qa_scope_window)
    )
    val scopeText: LiveData<String> get() = _scopeText

    /* ==================== 问答 ==================== */

    /**
     * 提问。
     *
     * @param fullBook true 表示放弃窗口限制、扫描全书（用户点了「扩大到全书重试」）
     */
    fun ask(rawQuestion: String, fullBook: Boolean = false) {
        val question = rawQuestion.trim()
        if (question.isEmpty()) {
            _error.postValue(context.getString(R.string.ai_err_question_empty))
            return
        }
        if (!guardAvailable()) return

        lastQuestion = question
        appendItem(AiChatRole.USER, question)
        beginRun()
        _scopeText.postValue(
            context.getString(
                if (fullBook) R.string.ai_qa_scope_full else R.string.ai_qa_scope_window
            )
        )

        val options = AiConfig.snapshot()
        val reader = AiConfig.readerSnapshot()
        val bookName = ReadBook.book?.name.orEmpty()
        val historySnapshot = history.toList()

        running = execute {
            val provider = CachedBookTextProvider.current() ?: return@execute QaStep.BookNotReady
            val retrieval = BookRetriever(provider).retrieve(
                RetrievalRequest(
                    query = question,
                    topK = reader.retrievalTopK,
                    maxCharsPerFragment = reader.retrievalMaxChars,
                    maxTotalChars = reader.maxTotalChars(),
                    windowRadius = if (fullBook) FULL_BOOK_RADIUS
                    else RetrievalRequest.DEFAULT_WINDOW_RADIUS
                )
            )
            if (retrieval.isEmpty) {
                return@execute QaStep.NoHit(retrieval.stats.cachedChapters == 0)
            }
            QaStep.Answered(
                qaEngine.ask(
                    options,
                    QaInput(
                        bookName = bookName,
                        question = question,
                        history = historySnapshot,
                        retrieval = retrieval
                    )
                )
            )
        }.onSuccess { step ->
            _loading.postValue(false)
            when (step) {
                QaStep.BookNotReady ->
                    appendItem(AiChatRole.NOTICE, context.getString(R.string.ai_book_not_ready))

                is QaStep.NoHit -> onNoHit(step.noCachedChapter, fullBook)
                is QaStep.Answered -> onAnswered(question, step.outcome)
            }
        }.onError { e ->
            _loading.postValue(false)
            _error.postValue(mapError(e))
        }
    }

    /** 「扩大到全书重试」：用同一个问题、去掉窗口限制再跑一次。 */
    fun expandToFullBook() {
        if (lastQuestion.isBlank()) return
        ask(lastQuestion, fullBook = true)
    }

    private fun onNoHit(noCachedChapter: Boolean, fullBook: Boolean) {
        val tip = when {
            noCachedChapter -> R.string.ai_no_cached_chapter
            fullBook -> R.string.ai_err_retrieval_empty
            else -> R.string.ai_qa_no_hit_window
        }
        appendItem(AiChatRole.NOTICE, context.getString(tip))
        // 已经是全书范围了就没有"再扩大"可言
        _canExpand.postValue(!fullBook && !noCachedChapter)
    }

    private fun onAnswered(question: String, outcome: QaOutcome) {
        history.add(ChatTurn(question, outcome.answerMarkdown))
        while (history.size > MAX_KEPT_HISTORY) {
            history.removeAt(0)
        }
        appendItem(AiChatRole.ASSISTANT, outcome.answerMarkdown, buildQaFooter(outcome))
        outcome.citations.forEach { citation ->
            appendItem(
                role = AiChatRole.CITATION,
                text = formatCitation(citation),
                citation = citation
            )
        }
    }

    /** 检索统计 + 成本 + 裁剪提示，拼成回答下方的一行说明。 */
    private fun buildQaFooter(outcome: QaOutcome): String {
        val stats = outcome.stats
        val parts = mutableListOf(
            context.getString(
                R.string.ai_qa_stats,
                stats.scannedChapters,
                stats.totalChapters,
                stats.cachedChapters,
                stats.elapsedMs
            ),
            context.getString(R.string.ai_qa_prompt_chars, outcome.promptChars)
        )
        if (stats.budgetExceeded) {
            parts.add(context.getString(R.string.ai_qa_budget_exceeded))
        }
        if (outcome.droppedHistoryTurns > 0) {
            parts.add(
                context.getString(R.string.ai_qa_history_dropped, outcome.droppedHistoryTurns)
            )
        }
        return parts.joinToString("\n")
    }

    private fun formatCitation(citation: Citation): String {
        val title = citation.chapterTitle.trim().ifEmpty {
            context.getString(R.string.ai_empty_result)
        }
        // 章号对用户 +1：内部 0 起始，读者眼里是第 1 章
        return "↪ ${citation.chapterIndex + 1}. $title"
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
        val bookName = ReadBook.book?.name.orEmpty()
        val chapterTitle = ReadBook.curTextChapter?.title.orEmpty()

        running = execute {
            explainEngine.explain(
                options,
                ExplainInput(
                    bookName = bookName,
                    chapterTitle = chapterTitle,
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
        history.clear()
        chatItems.clear()
        lastQuestion = ""
        _canExpand.postValue(false)
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
        _canExpand.postValue(false)
        _loading.postValue(true)
    }

    private fun appendItem(
        role: AiChatRole,
        text: String,
        footer: String = "",
        citation: Citation? = null
    ) {
        chatItems.add(
            AiChatItem(
                id = nextItemId++,
                role = role,
                text = text,
                footer = footer,
                citation = citation
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

    /**
     * 一次问答在后台线程跑完之后的三种结局。
     *
     * 用密封类而不是「抛异常 + catch 分支」来表达"书没就绪""没命中"：
     * 它们都是**正常业务结果**，不是错误；混进异常通道会让
     * 「真出错了」和「本地就是没有」在 UI 上长得一模一样。
     */
    private sealed interface QaStep {
        object BookNotReady : QaStep
        data class NoHit(val noCachedChapter: Boolean) : QaStep
        data class Answered(val outcome: QaOutcome) : QaStep
    }

    companion object {
        /** 传给 [RetrievalRequest.windowRadius] 表示"不限范围"。 */
        private const val FULL_BOOK_RADIUS = 0

        /**
         * 本地保留的历史轮数上限。
         *
         * 比引擎的 `MAX_HISTORY_TURNS` 多留几轮：引擎按预算决定**这一轮**带几条，
         * 而这里是会话级的留存，留宽一点可以让预算宽松时多用上一些上下文。
         */
        private const val MAX_KEPT_HISTORY = 12
    }
}
