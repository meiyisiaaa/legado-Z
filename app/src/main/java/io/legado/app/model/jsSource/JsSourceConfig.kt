package io.legado.app.model.jsSource

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import kotlin.coroutines.CoroutineContext

/**
 * Extracts the metadata-only `config` object from a single-file JS source.
 * The script runs in a fresh standard-object scope without host bindings. The
 * synchronous function contract follows skybbk1001/legadoT at
 * 869eb25511f48e2748ee3c3dede3cb1a9a60e143 (GPL-3.0), adapted to local Rhino.
 */
object JsSourceConfig {

    private const val CONFIG_TIMEOUT_MILLIS = 10_000L
    private val strippedKeys = setOf(
        "mainJs", "ruleSearch", "ruleExplore", "ruleBookInfo",
        "ruleToc", "ruleContent", "ruleReview"
    )

    suspend fun extract(text: String, coroutineContext: CoroutineContext): BookSource {
        if (text.isBlank()) throw NoStackTraceException("JS源脚本不能为空")
        if (coroutineContext[Job] == null) {
            throw NoStackTraceException("JS源解析需要有效的协程 Job")
        }
        coroutineContext.ensureActive()
        return withTimeoutOrNull(CONFIG_TIMEOUT_MILLIS) {
            val executionContext = currentCoroutineContext() + coroutineContext.minusKey(Job)
            val scope = RhinoScriptEngine.getSafeRuntimeScope()
            try {
                RhinoScriptEngine.eval(text, scope, executionContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw NoStackTraceException("JS源脚本执行失败: ${e.message}").also {
                    it.initCause(e)
                }
            }

            val configValue = RhinoScriptEngine.eval(
                "typeof config === 'undefined' ? undefined : config",
                scope,
                executionContext
            )
            if (configValue !is Scriptable || configValue is NativeArray) {
                throw NoStackTraceException("JS源缺少顶层 config 配置对象")
            }
            val configJson = JsSourceEngine.normalizeJsResult(configValue, scope, executionContext)
                ?: throw NoStackTraceException("JS源 config 配置对象无法序列化")
            val json = runCatching { GSON.fromJson(configJson, JsonObject::class.java) }
                .getOrNull()
                ?: throw NoStackTraceException("JS源 config 必须是 JSON 对象")
            strippedKeys.forEach(json::remove)
            normalizeExploreUrl(json)
            normalizeLoginUi(json)

            val source = runCatching { GSON.fromJson(json, BookSource::class.java) }
                .getOrNull()
                ?: throw NoStackTraceException("JS源 config 字段类型不正确")
            if (source.bookSourceUrl.isBlank()) {
                throw NoStackTraceException("JS源 config.bookSourceUrl 不能为空")
            }
            if (source.bookSourceName.isBlank()) {
                throw NoStackTraceException("JS源 config.bookSourceName 不能为空")
            }

            val required = if (source.bookSourceType == BookSourceType.file) {
                listOf("search", "getBookInfo")
            } else {
                listOf("search", "getChapters", "getContent")
            }
            required.forEach { name -> requireFunction(scope, name, executionContext) }
            if (!source.exploreUrl.isNullOrBlank()) {
                requireFunction(scope, "explore", executionContext)
            }

            val loginUiFunction = isFunction(scope, "loginUi", executionContext)
            if (loginUiFunction) {
                if (!source.loginUi.isNullOrBlank()) {
                    throw NoStackTraceException("loginUi 函数与 config.loginUi 只能二选一")
                }
                requireFunction(scope, "loginAction", executionContext)
                source.loginUi = LOGIN_UI_V2_MARKER
            } else if (!source.loginUi.isNullOrBlank()) {
                requireFunction(scope, "login", executionContext)
            }

            val hasReviewSummary = isFunction(scope, "getReviewSummary", executionContext)
            val hasReviewDetail = isFunction(scope, "getReviewDetail", executionContext)
            if (hasReviewSummary != hasReviewDetail) {
                throw NoStackTraceException(
                    "JS源 getReviewSummary 与 getReviewDetail 必须同时声明"
                )
            }
            source.mainJs = text
            source
        } ?: throw NoStackTraceException("JS源配置解析超时（10秒），已终止执行")
    }

    private fun requireFunction(
        scope: Scriptable,
        name: String,
        coroutineContext: CoroutineContext
    ) {
        if (!isFunction(scope, name, coroutineContext)) {
            throw NoStackTraceException("JS源缺少必备函数 $name")
        }
    }

    private fun isFunction(
        scope: Scriptable,
        name: String,
        coroutineContext: CoroutineContext
    ): Boolean = RhinoScriptEngine.eval(
        "typeof $name === 'function'",
        scope,
        coroutineContext
    ) == true

    private fun normalizeExploreUrl(json: JsonObject) {
        val value = json.get("exploreUrl") ?: return
        if (!value.isJsonArray) return
        val array = value.asJsonArray
        if (array.isEmpty) {
            json.remove("exploreUrl")
            return
        }
        array.forEachIndexed { index, item ->
            val title = runCatching { item.asJsonObject.get("title")?.asString }.getOrNull()
            if (title.isNullOrBlank()) {
                throw NoStackTraceException("exploreUrl 第 ${index + 1} 项缺少 title")
            }
        }
        json.addProperty("exploreUrl", GSON.toJson(array))
    }

    private fun normalizeLoginUi(json: JsonObject) {
        val value = json.get("loginUi") ?: return
        if (!value.isJsonArray) return
        val array: JsonArray = value.asJsonArray
        if (array.isEmpty) {
            json.remove("loginUi")
            return
        }
        array.forEachIndexed { index, item ->
            val name = runCatching { item.asJsonObject.get("name")?.asString }.getOrNull()
            if (name.isNullOrBlank()) {
                throw NoStackTraceException("loginUi 第 ${index + 1} 项缺少 name")
            }
        }
        json.addProperty("loginUi", GSON.toJson(array))
    }

    const val LOGIN_UI_V2_MARKER = """{"version":2}"""
}
