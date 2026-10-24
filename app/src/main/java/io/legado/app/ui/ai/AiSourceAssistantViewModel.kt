package io.legado.app.ui.ai

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.ai.AiChatOptions
import io.legado.app.ai.AiConfig
import io.legado.app.ai.AiError
import io.legado.app.ai.AiSourceOutcome
import io.legado.app.ai.DebugInput
import io.legado.app.ai.GenerateInput
import io.legado.app.ai.OkHttpPageFetcher
import io.legado.app.ai.PageFetcher
import io.legado.app.ai.ValidationResult
import io.legado.app.ai.batch.BatchInputParser
import io.legado.app.ai.batch.BatchProgress
import io.legado.app.ai.batch.BatchSourceGenerator
import io.legado.app.ai.batch.BatchTask
import io.legado.app.ai.client.AiChatClient
import io.legado.app.ai.client.ChatMessage
import io.legado.app.ai.client.OpenAiClient
import io.legado.app.ai.client.aiHttpClient
import io.legado.app.ai.engine.SourceDebugEngine
import io.legado.app.ai.engine.SourceGenEngine
import io.legado.app.ai.engine.DebugEvidenceBuilder
import io.legado.app.ai.source.BookSourceValidator
import io.legado.app.ai.source.RulePreviewBuilder
import io.legado.app.ai.source.SourceJsonExtractor
import io.legado.app.base.BaseViewModel
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.BookSourceImport
import io.legado.app.model.Debug
import io.legado.app.model.DebugRunKind
import io.legado.app.model.DebugRunResult
import io.legado.app.model.DebugRunSnapshot
import io.legado.app.model.DebugRunner
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import java.util.UUID

/**
 * AI 书源助手的核心 ViewModel。
 *
 * 把内核层的 [SourceGenEngine] / [SourceDebugEngine] / [OpenAiClient] 接到 Android 生命周期上：
 * - 通过 [AiConfig.snapshot] 每次拿最新配置（引擎/客户端不得持有配置引用，见 §7）；
 * - 通过 `BaseViewModel.execute` 跑协程，结果以 LiveData 回传 UI；
 * - 模型请求使用隔离的 [aiHttpClient]；网页抓取沿用 [okHttpClient] 派生客户端。
 */
class AiSourceAssistantViewModel(application: Application) : BaseViewModel(application) {

    private val chatClient: AiChatClient = OpenAiClient(aiHttpClient)
    private val pageFetcher: PageFetcher = OkHttpPageFetcher(okHttpClient)
    private val genEngine = SourceGenEngine(chatClient, pageFetcher)
    private val debugEngine = SourceDebugEngine(chatClient)
    private val batchGenerator = BatchSourceGenerator(genEngine)

    private val _outcome = MutableLiveData<AiSourceOutcome>()
    val outcome: LiveData<AiSourceOutcome> get() = _outcome

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> get() = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _config = MutableLiveData<AiChatOptions>()
    val config: LiveData<AiChatOptions> get() = _config

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> get() = _message

    private val _debugTrialSnapshot = MutableLiveData<DebugRunSnapshot?>()
    val debugTrialSnapshot: LiveData<DebugRunSnapshot?> get() = _debugTrialSnapshot

    private val _debugTrialStatus = MutableLiveData<String?>()
    val debugTrialStatus: LiveData<String?> get() = _debugTrialStatus

    private val debugTrialOwnerId = "ai-draft:${UUID.randomUUID()}"

    /* ==================== 批量生成（Phase 2） ==================== */

    private val _batchProgress = MutableLiveData(BatchProgress.EMPTY)
    val batchProgress: LiveData<BatchProgress> get() = _batchProgress

    private val _batchRunning = MutableLiveData(false)
    val batchRunning: LiveData<Boolean> get() = _batchRunning

    private var batchCoroutine: Coroutine<*>? = null

    fun loadConfig() {
        _config.postValue(AiConfig.snapshot())
    }

    fun generate(intent: String, url: String, html: String) {
        _loading.postValue(true)
        _error.postValue(null)
        val options = AiConfig.snapshot()
        execute {
            genEngine.generate(options, GenerateInput(url = url, html = html, intent = intent))
        }.onSuccess { result ->
            _loading.postValue(false)
            _outcome.postValue(result)
        }.onError { e ->
            _loading.postValue(false)
            _error.postValue(mapError(e))
        }
    }

    fun runDebugTrial(sourceText: String, key: String) {
        _loading.postValue(true)
        _error.postValue(null)
        _debugTrialSnapshot.postValue(null)
        _debugTrialStatus.postValue(context.getString(R.string.ai_debug_trial_running))
        execute {
            val source = try {
                BookSourceImport.parse(sourceText, currentCoroutineContext())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                throw IllegalArgumentException(
                    context.getString(R.string.ai_debug_invalid_source_json),
                    failure
                )
            }
            if (source.bookSourceUrl.isBlank()) {
                throw IllegalArgumentException(context.getString(R.string.ai_debug_source_url_required))
            }
            DebugRunner.run(
                ownerId = debugTrialOwnerId,
                kind = DebugRunKind.DRAFT_BOOK,
                sourceUrl = source.bookSourceUrl,
                onEvent = { event ->
                    _debugTrialStatus.postValue(
                        context.getString(R.string.ai_debug_trial_event_count, event.sequence.toInt())
                    )
                }
            ) {
                Debug.startDebug(source, key)
            }
        }.onSuccess { result ->
            _loading.postValue(false)
            when (result) {
                is DebugRunResult.Busy -> {
                    _debugTrialStatus.postValue(context.getString(R.string.ai_debug_runner_busy))
                    _error.postValue(context.getString(R.string.ai_debug_runner_busy))
                }

                is DebugRunResult.Completed -> {
                    val snapshot = result.snapshot
                    _debugTrialSnapshot.postValue(snapshot)
                    _debugTrialStatus.postValue(
                        if (snapshot.terminalState == "failed") {
                            context.getString(
                                R.string.ai_debug_trial_failed,
                                snapshot.error.orEmpty()
                            )
                        } else {
                            context.getString(
                                R.string.ai_debug_trial_complete,
                                snapshot.events.size
                            )
                        }
                    )
                }
            }
        }.onError { e ->
            _loading.postValue(false)
            if (e !is CancellationException) {
                _debugTrialStatus.postValue(context.getString(R.string.ai_debug_trial_failed, e.localizedMessage.orEmpty()))
                _error.postValue(mapError(e))
            }
        }
    }

    fun debug(
        sourceText: String,
        symptom: String,
        manualSampleHtml: String,
        includeLogs: Boolean,
        includeHtml: Boolean
    ) {
        _loading.postValue(true)
        _error.postValue(null)
        val options = AiConfig.snapshot()
        val evidence = DebugEvidenceBuilder.build(
            snapshot = _debugTrialSnapshot.value,
            includeLogs = includeLogs,
            includeHtml = includeHtml,
            manualSampleHtml = manualSampleHtml
        )
        execute {
            val source = BookSourceImport.parse(sourceText, currentCoroutineContext())
            debugEngine.debug(
                options,
                DebugInput(
                    sourceJson = GSON.toJson(source),
                    symptom = symptom,
                    sampleHtml = evidence.sampleHtml,
                    debugLogs = evidence.logs,
                    maxHtmlChars = minOf(options.htmlMaxChars, DebugEvidenceBuilder.MAX_HTML_CHARS)
                )
            )
        }.onSuccess { result ->
            _loading.postValue(false)
            _outcome.postValue(result)
        }.onError { e ->
            _loading.postValue(false)
            _error.postValue(mapError(e))
        }
    }

    fun previewDebugEvidence(
        includeLogs: Boolean,
        includeHtml: Boolean,
        manualSampleHtml: String
    ): String {
        val options = AiConfig.snapshot()
        val evidence = DebugEvidenceBuilder.build(
            snapshot = _debugTrialSnapshot.value,
            includeLogs = includeLogs,
            includeHtml = includeHtml,
            manualSampleHtml = manualSampleHtml
        )
        return evidence.previewText(minOf(options.htmlMaxChars, DebugEvidenceBuilder.MAX_HTML_CHARS))
    }

    /**
     * 批量生成。
     *
     * 上限、去重、裁剪都交给 [BatchInputParser]，这里只负责把解析结果接到协程与 LiveData 上。
     * 整批共用**同一份配置快照**：中途去改配置不该影响一批已经开跑的任务。
     */
    fun runBatch(rawUrls: String, intent: String) {
        if (_batchRunning.value == true) return
        val parsed = BatchInputParser.parse(rawUrls)
        if (parsed.isEmpty()) {
            _error.postValue(context.getString(R.string.ai_err_batch_input_empty))
            return
        }
        if (parsed.ignoredCount > 0) {
            _message.postValue(
                context.getString(
                    R.string.ai_batch_limit_exceeded,
                    BatchInputParser.DEFAULT_MAX_TASKS
                )
            )
        }
        _error.postValue(null)
        _batchRunning.postValue(true)
        _batchProgress.postValue(BatchProgress.EMPTY)
        val options = AiConfig.snapshot()
        batchCoroutine = execute {
            batchGenerator.run(options, parsed.urls, intent) { progress ->
                _batchProgress.postValue(progress)
            }
        }.onSuccess { progress ->
            _batchProgress.postValue(progress)
        }.onError { e ->
            _error.postValue(mapError(e))
        }.onFinally {
            _batchRunning.postValue(false)
        }.onCancel {
            // onFinally 在协程被取消时不会执行，取消分支必须单独收口，
            // 否则「停止」之后按钮会永远停在禁用态
            _batchRunning.postValue(false)
        }
    }

    /** 取消批量。已跑完的成功/失败结果保留，未跑的会被编排器标成已取消。 */
    fun cancelBatch() {
        batchCoroutine?.cancel()
        batchCoroutine = null
        _batchRunning.postValue(false)
    }

    /**
     * 把批量结果中所有可导入的书源合并成一个 JSON 数组，交给 `ImportBookSourceDialog`。
     *
     * 合并成数组而不是逐条弹框：20 条就弹 20 次导入框，谁也受不了。
     *
     * @return 没有任何可导入结果时返回 null
     */
    fun mergedBatchJson(): String? {
        val jsons = _batchProgress.value?.importableJsons().orEmpty()
        if (jsons.isEmpty()) return null
        return jsons.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]")
    }

    /** 把某条批量结果搬到结果区，复用单条生成的预览/导入链路。 */
    fun showBatchTask(task: BatchTask) {
        task.outcome?.let { _outcome.postValue(it) }
    }

    override fun onCleared() {
        super.onCleared()
        batchCoroutine?.cancel()
    }

    fun saveConfig(options: AiChatOptions) {
        val result: ValidationResult = AiConfig.save(options)
        if (result.hasError()) {
            val detail = result.issues.firstOrNull()?.let { issue ->
                val base = context.getString(issue.messageRes)
                if (issue.detail.isBlank()) base else "$base：${issue.detail}"
            }.orEmpty()
            _error.postValue(detail.ifBlank { context.getString(R.string.ai_err_invalid_config) })
        } else {
            _config.postValue(AiConfig.snapshot())
            _message.postValue(context.getString(R.string.ai_settings_saved))
        }
    }

    fun testConnection() {
        if (!AiConfig.isConfigured()) {
            _error.postValue(context.getString(R.string.ai_not_configured_tip))
            return
        }
        _loading.postValue(true)
        val options = AiConfig.snapshot()
        execute {
            chatClient.chat(options, listOf(ChatMessage.user("ping")))
        }.onSuccess {
            _loading.postValue(false)
            _message.postValue(context.getString(R.string.ai_ping_ok))
        }.onError { e ->
            _loading.postValue(false)
            _error.postValue(mapError(e))
        }
    }

    fun clearMessage() {
        _message.postValue(null)
    }

    /** 保存用户在结果区编辑后的书源 JSON，并立即重新校验/刷新预览。 */
    fun updateEditedSourceJson(rawJson: String) {
        val json = runCatching {
            JsonParser.parseString(rawJson.trim()).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull()
        if (json == null) {
            _error.postValue(context.getString(R.string.ai_issue_json_malformed))
            return
        }
        val current = _outcome.value ?: return
        _outcome.postValue(
            current.copy(
                rawReply = rawJson,
                sourceJson = SourceJsonExtractor.format(json),
                preview = RulePreviewBuilder.build(json),
                validation = BookSourceValidator.validate(json)
            )
        )
    }

    private fun mapError(e: Throwable): String {
        return if (e is AiError) {
            context.getString(e.messageRes)
        } else {
            e.localizedMessage ?: e.message ?: context.getString(R.string.ai_err_unknown)
        }
    }
}
