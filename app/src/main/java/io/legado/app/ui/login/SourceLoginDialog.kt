package io.legado.app.ui.login

import android.content.DialogInterface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.databinding.DialogLoginBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.jsSource.JsSourceConfig
import io.legado.app.model.jsSource.JsSourceEngine
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.GSON
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.openUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.text.lastIndexOf
import kotlin.text.startsWith
import kotlin.text.substring
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.utils.buildMainHandler

class SourceLoginDialog : BaseDialogFragment(R.layout.dialog_login, true),
    SourceLoginJsExtensions.Callback {

    private val binding by viewBinding(DialogLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var lastClickTime: Long = 0
    private lateinit var toolbarView: Toolbar
    private var oKToClose = false
    private var rowUis: List<RowUi>? = null
    private val legacyRows = mutableStateListOf<SourceLoginField>()
    private val legacyValues = mutableStateMapOf<String, String>()
    private val legacyResolvedLabels = mutableStateMapOf<String, String>()
    private var hasChange = false
    private var loginUrl: String? = null
    private var isLoginUiV2 by mutableStateOf(false)
    private var v2State: Any? = linkedMapOf<String, Any?>()
    private var v2Ui by mutableStateOf<SourceLoginV2Ui?>(null)
    private val v2Form = mutableStateMapOf<String, String>()
    private val v2EphemeralValues = mutableStateMapOf<String, String>()
    private var v2Errors by mutableStateOf<Map<String, String>>(emptyMap())
    private var v2GlobalError by mutableStateOf<String?>(null)
    private val v2CountdownLabels = mutableStateMapOf<String, String>()
    private val v2CountdownActive = mutableStateMapOf<String, Boolean>()
    private val v2ActionRunnables = linkedMapOf<String, Runnable>()
    private val v2CountdownEndsAt = linkedMapOf<String, Long>()
    private val v2CountdownTimers = linkedMapOf<String, CountDownTimer>()
    private val sourceLoginJsExtensions by lazy {
        SourceLoginJsExtensions(
            activity as AppCompatActivity,
            viewModel.source,
            viewModel.bookType,
            this
        )
    }

    private var initHandler = false
    private val handler by lazy {
        initHandler = true
        buildMainHandler()
    }

    override fun upUiData(data: Map<String, Any?>?) {
        try {
            activity?.runOnUiThread { // 在主线程中更新 UI
                handleUpUiData(data)
            }
        } catch (e: Exception) {
            AppLog.put("upLoginData Error: " + e.localizedMessage, e)
        }
    }

    override fun reUiView(deltaUp: Boolean) {
        activity?.runOnUiThread {
            handleReUiView(deltaUp)
        }
    }

    private fun handleReUiView(deltaUp: Boolean) {
        val source = viewModel.source ?: return
        val loginUiStr = source.loginUi ?: return
        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            hasChange = true
            lifecycleScope.launch(Main) {
                withContext(IO) {
                    val loginUiJson = evalUiJs(codeStr)
                    rowUis = loginUi(loginUiJson)
                }
                rowUiBuilder(source, rowUis, deltaUp)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            rowUiBuilder(source, rowUis, deltaUp)
        }
    }

    private fun handleUpUiData(data: Map<String, Any?>?) {
        hasChange = true
        val rows = rowUis.orEmpty()
        val loginInfo = viewModel.loginInfo
        if (data == null) {
            val newLoginInfo = linkedMapOf<String, String>()
            legacyValues.clear()
            rows.forEachIndexed { index, row ->
                val options = row.chars?.filterNotNull().orEmpty()
                when (row.type) {
                    RowUi.Type.text, RowUi.Type.password -> {
                        val value = row.default.orEmpty()
                        legacyValues[row.name] = value
                        newLoginInfo[row.name] = value
                    }
                    RowUi.Type.select, RowUi.Type.toggle -> {
                        val value = row.default?.takeIf(String::isNotEmpty) ?: options.firstOrNull().orEmpty()
                        legacyValues[row.name] = value
                        newLoginInfo[row.name] = value
                        if (row.type == RowUi.Type.toggle && index < legacyRows.size) {
                            legacyRows[index] = legacyRows[index].copy(label = resolvedRowLabel(row))
                        }
                    }
                    RowUi.Type.button -> if (index < legacyRows.size) {
                        legacyRows[index] = legacyRows[index].copy(label = resolvedRowLabel(row))
                    }
                }
            }
            viewModel.loginInfo = newLoginInfo
            return
        }
        data.forEach { (key, rawValue) ->
            val rowIndex = rows.indexOfFirst { it.name == key }
            if (rowIndex < 0) {
                rawValue?.let { loginInfo[key] = it.toString() }
                return@forEach
            }
            val row = rows[rowIndex]
            val value = rawValue?.toString() ?: row.default
            when (row.type) {
                RowUi.Type.text, RowUi.Type.password -> {
                    val fieldValue = value.orEmpty()
                    legacyValues[key] = fieldValue
                    loginInfo[key] = fieldValue
                }
                RowUi.Type.select, RowUi.Type.toggle -> {
                    val options = row.chars?.filterNotNull().orEmpty()
                    val selected = value?.takeIf(String::isNotEmpty) ?: options.firstOrNull().orEmpty()
                    legacyValues[key] = selected
                    loginInfo[key] = selected
                }
                RowUi.Type.button -> {
                    if (rowIndex < legacyRows.size) {
                        legacyRows[rowIndex] = legacyRows[rowIndex].copy(
                            label = value?.takeIf(String::isNotEmpty) ?: resolvedRowLabel(row)
                        )
                    }
                }
            }
        }
    }

    private fun resolvedRowLabel(row: RowUi): String {
        val viewName = row.viewName ?: return row.name
        return quotedViewName(viewName) ?: legacyResolvedLabels[row.name] ?: row.name
    }

    private fun quotedViewName(viewName: String): String? {
        return viewName.takeIf {
            it.length in 3..19 && it.first() == '\'' && it.last() == '\''
        }?.substring(1, viewName.lastIndex)
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    suspend fun evalUiJs(jsStr: String): String? {
        val source = viewModel.source ?: return null
        val loginJS = loginUrl ?: ""
        val result = rowUis?.let {
            getLoginData(it)
        } ?: viewModel.loginInfo.toMutableMap()
        return try {
            runScriptWithContext {
                source.evalJS("$loginJS\n$jsStr") {
                    put("result", result)
                    put("book", viewModel.book)
                    put("chapter", viewModel.chapter)
                }.toString()
            }
        } catch (e: Exception) {
            AppLog.put(source.getTag() + " loginUi err:" + (e.localizedMessage ?: e.toString()), e)
            null
        }
    }

    fun loginUi(json: String?): List<RowUi>? {
        return GSON.fromJsonArray<RowUi>(json).onFailure {
            AppLog.put("loginUi json parse err:" + it.localizedMessage, it)
        }.getOrNull()
    }

    private fun rowUiBuilder(source: BaseSource, rowUis: List<RowUi>?, deltaUp: Boolean) {
        if (rowUis == null) {
            legacyRows.clear()
            return
        }
        val nextRows = rowUis.map { row ->
            val options = row.chars?.filterNotNull().orEmpty()
            val viewName = row.viewName
            val literalLabel = viewName?.let(::quotedViewName)
            val label = literalLabel ?: legacyResolvedLabels[row.name] ?: row.name
            val value = when (row.type) {
                RowUi.Type.select, RowUi.Type.toggle -> {
                    val saved = legacyValues[row.name] ?: viewModel.loginInfo[row.name]
                    val initial = saved?.takeIf(String::isNotEmpty)
                        ?: row.default?.takeIf(String::isNotEmpty)
                        ?: options.firstOrNull().orEmpty()
                    if (saved.isNullOrEmpty()) hasChange = true
                    viewModel.loginInfo[row.name] = initial
                    initial
                }
                else -> legacyValues[row.name] ?: viewModel.loginInfo[row.name] ?: row.default.orEmpty()
            }
            legacyValues[row.name] = value
            SourceLoginField(
                key = row.name,
                label = label,
                type = row.type,
                action = row.action,
                options = options,
                style = row.style(),
                isV2 = false
            )
        }
        legacyRows.clear()
        legacyRows.addAll(nextRows)

        rowUis.forEachIndexed { index, row ->
            val viewName = row.viewName ?: return@forEachIndexed
            if (quotedViewName(viewName) != null) return@forEachIndexed
            lifecycleScope.launch(Main) {
                val result = runCatching { withContext(IO) { evalUiJs(viewName) } }
                val label = result.getOrNull()?.takeIf(String::isNotEmpty)
                    ?: if (result.isFailure) "err" else "null"
                if (legacyRows.getOrNull(index)?.key == row.name) {
                    legacyResolvedLabels[row.name] = label
                    legacyRows[index] = legacyRows[index].copy(label = label)
                }
            }
        }
    }

    private fun buttonUi(source: BaseSource, rowUis: List<RowUi>?) {
        rowUiBuilder(source, rowUis, false)
        toolbarView.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_ok -> {
                    oKToClose = true
                    login(source)
                }

                R.id.menu_show_login_header -> alert {
                    setTitle(R.string.login_header)
                    source.getLoginHeader()?.let { loginHeader ->
                        setMessage(loginHeader)
                        positiveButton(R.string.copy_text) {
                            appCtx.sendToClip(loginHeader)
                        }
                    }
                }

                R.id.menu_del_login_header -> source.removeLoginHeader()
                R.id.menu_log -> showDialogFragment<AppLogDialog>()
            }
            return@setOnMenuItemClickListener true
        }
    }

    private suspend fun loadV2LoginUi(): SourceLoginV2Ui {
        val source = viewModel.source as? BookSource
            ?: throw IllegalStateException("V2 loginUi 仅支持单文件书源")
        val json = JsSourceEngine(source, currentCoroutineContext()).callFunctionWithBindings(
            name = "loginUi",
            args = listOf("state" to GSON.toJson(v2State)),
            bindings = listOf(
                "java" to sourceLoginJsExtensions,
                "book" to viewModel.book,
                "chapter" to viewModel.chapter
            ),
            jsonArgumentNames = setOf("state")
        )
        return SourceLoginV2Ui.parse(json)
    }

    private fun renderV2LoginUi(ui: SourceLoginV2Ui) {
        v2CountdownTimers.values.forEach(CountDownTimer::cancel)
        v2CountdownTimers.clear()
        v2CountdownActive.clear()
        v2EphemeralValues.clear()
        v2Ui = ui
        ui.rows.forEach { row ->
            val key = row.key ?: return@forEach
            v2Form[key] = row.value ?: v2Form[key] ?: viewModel.loginInfo[key].orEmpty()
        }
        ui.rows.mapNotNull { it.action?.takeIf(String::isNotBlank) }.distinct().forEach { action ->
            updateV2Countdown(action)
        }
    }

    private fun currentFields(): List<SourceLoginField> {
        if (!isLoginUiV2) return legacyRows.toList()
        return v2Ui?.rows.orEmpty().mapIndexed { index, row ->
            val key = row.key ?: "#v2-$index"
            SourceLoginField(
                key = key,
                label = row.label,
                type = row.type,
                action = row.action,
                options = row.options.orEmpty(),
                hint = row.hint,
                error = row.key?.let(v2Errors::get),
                style = row.style,
                countdownLabel = row.action?.let(v2CountdownLabels::get),
                enabled = if (row.type == "button") {
                    !row.action.isNullOrBlank() && v2CountdownActive[row.action] != true
                } else true,
                booleanToggle = row.type == "toggle",
                isV2 = true
            )
        }
    }

    private fun currentValues(): Map<String, String> {
        if (!isLoginUiV2) return legacyValues
        return buildMap {
            putAll(v2Form)
            putAll(v2EphemeralValues)
        }
    }

    private fun onLoginFieldValueChange(field: SourceLoginField, value: String) {
        if (field.isV2) {
            if (v2Ui?.rows?.any { it.key == field.key } == true) {
                v2Form[field.key] = value
                v2Errors = v2Errors - field.key
            } else {
                v2EphemeralValues[field.key] = value
            }
            return
        }
        legacyValues[field.key] = value
        field.action?.let { action ->
            val key = "${field.key}:$action"
            val callback = Runnable {
                val source = viewModel.source ?: return@Runnable
                handleButtonClick(source, action, field.key, false)
            }
            v2ActionRunnables.remove(key)?.let(handler::removeCallbacks)
            v2ActionRunnables[key] = callback
            handler.postDelayed(callback, 600)
        }
    }

    private fun onLoginFieldSelect(field: SourceLoginField, value: String) {
        if (field.isV2) {
            if (v2Ui?.rows?.any { it.key == field.key } == true) {
                v2Form[field.key] = value
                v2Errors = v2Errors - field.key
            }
        } else {
            legacyValues[field.key] = value
            viewModel.loginInfo[field.key] = value
            hasChange = true
            field.action?.let { action ->
                viewModel.source?.let { source -> handleButtonClick(source, action, field.key, false) }
            }
        }
    }

    private fun onLoginFieldToggle(field: SourceLoginField, value: String) {
        if (field.isV2) {
            if (v2Ui?.rows?.any { it.key == field.key } == true) {
                v2Form[field.key] = value
                v2Errors = v2Errors - field.key
            } else {
                v2EphemeralValues[field.key] = value
            }
            scheduleV2Action(field.key, field.action, immediate = true)
        }
    }

    private fun onLoginFieldAction(field: SourceLoginField, isLongClick: Boolean) {
        if (field.isV2) {
            val action = field.action ?: return
            if (field.type == "button") {
                val seconds = v2Ui?.rows?.firstOrNull { it.action == action }?.countdown?.takeIf { it > 0 } ?: 0
                if (seconds > 0) {
                    v2CountdownEndsAt[action] = SystemClock.elapsedRealtime() + seconds * 1_000L
                    updateV2Countdown(action)
                }
                performV2Action(action)
            }
            return
        }
        val source = viewModel.source ?: return
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 200) return
        lastClickTime = now
        if (field.type == RowUi.Type.toggle) {
            val options = field.options
            if (options.isNotEmpty()) {
                val currentIndex = options.indexOf(legacyValues[field.key])
                val nextValue = options[(currentIndex + 1).mod(options.size)]
                legacyValues[field.key] = nextValue
                viewModel.loginInfo[field.key] = nextValue
                hasChange = true
            }
        }
        handleButtonClick(source, field.action, field.key, isLongClick)
    }

    private fun scheduleV2Action(key: String, action: String?, immediate: Boolean = false) {
        if (action.isNullOrBlank()) return
        val callback = Runnable { performV2Action(action) }
        v2ActionRunnables.remove(key)?.let(handler::removeCallbacks)
        v2ActionRunnables[key] = callback
        if (immediate) callback.run() else handler.postDelayed(callback, 600)
    }

    private fun updateV2Countdown(action: String) {
        val label = v2Ui?.rows?.firstOrNull { it.action == action }?.label ?: return
        val endAt = v2CountdownEndsAt[action]
        if (endAt == null) {
            v2CountdownLabels[action] = label
            v2CountdownActive[action] = false
            return
        }
        val remaining = endAt - SystemClock.elapsedRealtime()
        if (remaining <= 0) {
            v2CountdownEndsAt.remove(action)
            v2CountdownLabels[action] = label
            v2CountdownActive[action] = false
            v2CountdownTimers.remove(action)
            return
        }
        v2CountdownActive[action] = true
        v2CountdownLabels[action] = "$label (${(remaining + 999) / 1_000})"
        val timer = object : CountDownTimer(remaining, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                v2CountdownLabels[action] = "$label (${(millisUntilFinished + 999) / 1_000})"
            }

            override fun onFinish() {
                v2CountdownEndsAt.remove(action)
                v2CountdownLabels[action] = label
                v2CountdownActive[action] = false
                v2CountdownTimers.remove(action)
            }
        }.start()
        v2CountdownTimers.put(action, timer)?.cancel()
    }
    private fun performV2Action(action: String?) {
        if (action.isNullOrBlank()) return
        val source = viewModel.source as? BookSource ?: return
        val state = GSON.toJson(v2State)
        val form = GSON.toJson(v2Form.toMap())
        lifecycleScope.launch(Main) {
            try {
                val result = withContext(IO) {
                    val raw = JsSourceEngine(source, currentCoroutineContext()).callFunctionWithBindings(
                        name = "loginAction",
                        args = listOf("action" to action, "state" to state, "form" to form),
                        bindings = listOf(
                            "java" to sourceLoginJsExtensions,
                            "book" to viewModel.book,
                            "chapter" to viewModel.chapter
                        ),
                        jsonArgumentNames = setOf("state", "form")
                    )
                    parseV2ActionResult(raw)
                }
                v2State = result.state ?: v2State
                v2Errors = result.errors
                v2GlobalError = result.globalError
                result.loginInfo?.let { loginInfo ->
                    val saved = withContext(IO) {
                        if (loginInfo.isEmpty()) {
                            source.removeLoginInfo()
                            true
                        } else {
                            source.putLoginInfo(GSON.toJson(loginInfo))
                        }
                    }
                    if (!saved) throw IllegalStateException("登录信息保存失败")
                    viewModel.loginInfo = loginInfo.toMutableMap()
                    context?.toastOnUi(R.string.success)
                }
                if (result.close) {
                    oKToClose = true
                    dismiss()
                } else {
                    if (result.hasState) v2Ui = withContext(IO) { loadV2LoginUi() }
                    v2Ui?.let(::renderV2LoginUi)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.put("单文件书源 loginAction 执行失败", e)
                v2GlobalError = e.localizedMessage ?: "loginAction 执行失败"
                v2Ui?.let(::renderV2LoginUi)
            }
        }
    }

    private fun parseV2ActionResult(raw: String?): V2ActionResult {
        if (raw.isNullOrBlank()) return V2ActionResult()
        val result = runCatching { GSON.fromJson(raw, JsonObject::class.java) }
            .getOrNull() ?: throw IllegalStateException("loginAction 必须返回 JSON 对象")
        val stateElement = result.get("state")?.takeUnless { it.isJsonNull }
        val state = stateElement?.let { GSON.fromJson<Any>(it, Any::class.java) }
        val errors = linkedMapOf<String, String>()
        var globalError: String? = null
        when (val error = result.get("error")?.takeUnless { it.isJsonNull }) {
            is JsonObject -> error.entrySet().forEach { (key, value) ->
                errors[key] = runCatching { value.asString }.getOrElse { value.toString() }
            }
            null -> Unit
            else -> globalError = runCatching { error.asString }.getOrNull() ?: error.toString()
        }
        val loginElement = result.get("login")?.takeUnless { it.isJsonNull }
        val loginInfo = when {
            loginElement == null -> null
            loginElement.isJsonObject -> loginElement.asJsonObject.entrySet().associate { (key, value) ->
                key to if (value.isJsonNull) "" else runCatching { value.asString }
                    .getOrElse { value.toString() }
            }
            loginElement.isJsonPrimitive && loginElement.asJsonPrimitive.isBoolean && loginElement.asBoolean ->
                v2Form.toMap()
            loginElement.isJsonPrimitive && loginElement.asJsonPrimitive.isString &&
                    loginElement.asString.equals("true", ignoreCase = true) -> v2Form.toMap()
            else -> null
        }
        val close = result.get("close")?.let { runCatching { it.asBoolean }.getOrDefault(false) } ?: false
        return V2ActionResult(
            state = state,
            hasState = stateElement != null,
            errors = errors,
            globalError = globalError,
            loginInfo = loginInfo,
            close = close
        )
    }

    private data class V2ActionResult(
        val state: Any? = null,
        val hasState: Boolean = false,
        val errors: Map<String, String> = emptyMap(),
        val globalError: String? = null,
        val loginInfo: Map<String, String>? = null,
        val close: Boolean = false
    )

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val source = viewModel.source ?: return
        loginUrl = source.getLoginJs()
        val loginUiStr = source.loginUi ?: return

        toolbarView = Toolbar(requireContext()).apply {
            setBackgroundColor(this@SourceLoginDialog.primaryColor)
            popupTheme = R.style.AppTheme_PopupOverlay
            setTitleTextAppearance(context, R.style.ToolbarTitle)
            title = this@SourceLoginDialog.getString(R.string.login_source, source.getTag())
            elevation = 5.dpToPx().toFloat()
            inflateMenu(R.menu.source_login)
            menu.applyTint(requireContext())
        }
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeView.setContent {
            LegadoTheme {
                SourceLoginContent(
                    toolbar = toolbarView,
                    fields = currentFields(),
                    values = currentValues(),
                    globalError = if (isLoginUiV2) v2GlobalError else null,
                    onValueChange = ::onLoginFieldValueChange,
                    onSelect = ::onLoginFieldSelect,
                    onToggle = ::onLoginFieldToggle,
                    onAction = ::onLoginFieldAction
                )
            }
        }

        if (loginUiStr == JsSourceConfig.LOGIN_UI_V2_MARKER) {
            isLoginUiV2 = true
            buttonUi(source, null)
            toolbarView.menu.findItem(R.id.menu_ok)?.isVisible = false
            lifecycleScope.launch(Main) {
                try {
                    v2Ui = withContext(IO) { loadV2LoginUi() }
                    v2Ui?.let(::renderV2LoginUi)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.put("单文件书源 loginUi 初始化失败", e)
                    context?.toastOnUi(e.localizedMessage ?: "loginUi 初始化失败")
                }
            }
            return
        }

        val codeStr = loginUiStr.let {
            when {
                it.startsWith("@js:") -> it.substring(4)
                it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                else -> null
            }
        }
        if (codeStr != null) {
            lifecycleScope.launch(Main) {
                withContext(IO) {
                    val loginUiJson = evalUiJs(codeStr)
                    rowUis = loginUi(loginUiJson)
                }
                buttonUi(source, rowUis)
            }
        } else {
            rowUis = loginUi(loginUiStr)
            buttonUi(source, rowUis)
        }
    }
    private fun handleButtonClick(source: BaseSource, action: String?, name: String, isLongClick: Boolean) {
        lifecycleScope.launch(IO) {
            if (action.isAbsUrl()) {
                context?.openUrl(action!!)
            } else if (action != null) {
                // JavaScript
                val buttonFunctionJS = action
                val loginJS = loginUrl ?: return@launch
                kotlin.runCatching {
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", getLoginData(rowUis))
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", isLongClick)
                        }
                    }
                }.onFailure { e ->
                    ensureActive()
                    AppLog.put("LoginUI Button $name JavaScript error", e)
                }
            }
        }
    }

    private fun getLoginData(rowUis: List<RowUi>?): MutableMap<String, String> {
        val inputValues = linkedMapOf<String, String>()
        rowUis?.forEach { row ->
            when (row.type) {
                RowUi.Type.text, RowUi.Type.password -> {
                    inputValues[row.name] = legacyValues[row.name] ?: row.default.orEmpty()
                }
            }
        }
        return viewModel.loginInfo.toMutableMap().apply { putAll(inputValues) }
    }
    private fun login(source: BaseSource) {
        lifecycleScope.launch(IO) {
            val loginData = getLoginData(rowUis)
            if (loginData.isEmpty()) {
                source.removeLoginInfo()
                withContext(Main) {
                    dismiss()
                }
            } else if (source.putLoginInfo(GSON.toJson(loginData))) {
                try {
                    val buttonFunctionJS = "if (typeof login=='function'){ login.apply(this); } else { throw('Function login not implements!!!') }"
                    val loginJS = loginUrl ?: return@launch
                    runScriptWithContext {
                        source.evalJS("$loginJS\n$buttonFunctionJS") {
                            put("java", sourceLoginJsExtensions)
                            put("result", loginData)
                            put("book", viewModel.book)
                            put("chapter", viewModel.chapter)
                            put("isLongClick", false)
                        }
                    }
                    context?.toastOnUi(R.string.success)
                    withContext(Main) {
                        dismiss()
                    }
                } catch (e: Exception) {
                    AppLog.put("登录出错\n${e.localizedMessage}", e)
                    context?.toastOnUi("登录出错\n${e.localizedMessage}")
                    e.printOnDebug()
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!isLoginUiV2 && !oKToClose && hasChange) {
            val loginInfo = viewModel.loginInfo
            if (loginInfo.isEmpty()) {
                viewModel.source?.removeLoginInfo()
            } else {
                viewModel.source?.putLoginInfo(GSON.toJson(loginInfo))
            }
        }
        if (initHandler) {
            handler.removeCallbacksAndMessages(null)
        }
        v2CountdownTimers.values.forEach(CountDownTimer::cancel)
        v2CountdownTimers.clear()
        v2ActionRunnables.clear()
        super.onDismiss(dialog)
        activity?.finish()
    }

}
