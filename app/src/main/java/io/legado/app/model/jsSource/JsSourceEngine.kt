package io.legado.app.model.jsSource

import com.script.ScriptBindings
import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.help.JsExtensions
import io.legado.app.help.CacheManager
import io.legado.app.help.http.CookieStore
import io.legado.app.help.source.getShareScope
import io.legado.app.utils.GSON
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import kotlin.coroutines.CoroutineContext

/**
 * 单文件JS运行时按 T@869eb25511f48e2748ee3c3dede3cb1a9a60e143 的同步函数协议执行，
 * 适配本项目 Mozilla Rhino 与现有 JsExtensions。每次函数调用都使用独立 scope。
 * 协议来源：skybbk1001/legadoT，GPL-3.0；此处按本地宿主接口重新实现。
 */
class JsSourceEngine(
    private val source: BookSource,
    private val coroutineContext: CoroutineContext
) : JsExtensions {

    override fun getSource(): BaseSource = source

    override fun getTag(): String = source.bookSourceName

    fun callFunction(name: String, args: List<Pair<String, Any?>>): String? =
        call(name, args, emptyList(), emptySet(), required = true)

    /** Calls a function with extra host bindings and selected JSON arguments parsed into JS values. */
    fun callFunctionWithBindings(
        name: String,
        args: List<Pair<String, Any?>>,
        bindings: List<Pair<String, Any?>>,
        jsonArgumentNames: Set<String> = emptySet()
    ): String? = call(name, args, bindings, jsonArgumentNames, required = true)

    fun callFunctionIfExists(name: String, args: List<Pair<String, Any?>>): String? =
        call(name, args, emptyList(), emptySet(), required = false)

    private fun call(
        name: String,
        args: List<Pair<String, Any?>>,
        bindings: List<Pair<String, Any?>>,
        jsonArgumentNames: Set<String>,
        required: Boolean
    ): String? {
        require(name.matches(IDENTIFIER)) { "非法JS函数名: $name" }
        args.forEach { (key, _) -> require(key.matches(IDENTIFIER)) { "非法JS参数名: $key" } }
        bindings.forEach { (key, _) -> require(key.matches(IDENTIFIER)) { "非法JS绑定名: $key" } }
        require(jsonArgumentNames.all { selected -> args.any { it.first == selected } }) {
            "JSON 参数必须存在于函数实参中"
        }
        val mainJs = source.mainJs?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("mainJs 为空，不是单文件JS书源")
        val scope = buildScope(mainJs, args + bindings)
        val functionExists = RhinoScriptEngine.eval(
            "typeof $name === 'function'",
            scope,
            coroutineContext
        ) == true
        if (!functionExists) {
            if (required) throw IllegalStateException("JS源缺少函数 $name")
            return null
        }
        val callExpression = "$name(${args.joinToString(", ") { (argName, _) ->
            if (argName in jsonArgumentNames) "JSON.parse(String($argName))" else argName
        }})"
        val result = RhinoScriptEngine.eval(callExpression, scope, coroutineContext)
        return normalizeJsResult(result, scope, coroutineContext)
    }

    private fun buildScope(
        mainJs: String,
        args: List<Pair<String, Any?>>
    ): ScriptBindings {
        val bindings = buildScriptBindings { scope ->
            scope["java"] = this
            scope["source"] = source
            scope["baseUrl"] = source.getKey()
            scope["cookie"] = CookieStore
            scope["cache"] = CacheManager
            args.forEach { (name, value) -> scope[name] = value }
        }
        val sharedScope = source.getShareScope(coroutineContext)
        if (sharedScope != null) {
            bindings.prototype = sharedScope
        } else {
            RhinoScriptEngine.getRuntimeScope(bindings)
        }
        RhinoScriptEngine.eval(mainJs, bindings, coroutineContext)
        return bindings
    }

    companion object {
        private val IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
        private const val RESULT_PROPERTY = "__legadoJsSourceResult__"

        /** 对象和数组由 Rhino 自己 JSON.stringify，字符串则保持原样。 */
        fun normalizeJsResult(
            result: Any?,
            scope: Scriptable,
            coroutineContext: CoroutineContext
        ): String? {
            var value = result
            if (value is Wrapper) value = value.unwrap()
            return when {
                value == null || value is Undefined -> null
                value is String -> value
                value is CharSequence -> value.toString()
                value is Scriptable -> {
                    ScriptableObject.putProperty(scope, RESULT_PROPERTY, value)
                    try {
                        RhinoScriptEngine.eval(
                            "JSON.stringify($RESULT_PROPERTY)",
                            scope,
                            coroutineContext
                        ) as? String
                    } finally {
                        ScriptableObject.deleteProperty(scope, RESULT_PROPERTY)
                    }
                }

                else -> GSON.toJson(value)
            }
        }
    }
}
