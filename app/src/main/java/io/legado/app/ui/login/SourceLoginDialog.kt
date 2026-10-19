package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import androidx.core.view.setPadding
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.SwitchCompat
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
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.databinding.ItemSelectorSingleBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.jsSource.JsSourceConfig
import io.legado.app.model.jsSource.JsSourceEngine
import io.legado.app.ui.about.AppLogDialog
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
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import io.legado.app.data.entities.rule.RowUi.Type
import io.legado.app.ui.widget.text.TextInputLayout
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.indexOf
import io.legado.app.utils.setSelectionSafely

class SourceLoginDialog : BaseDialogFragment(R.layout.dialog_login, true),
    SourceLoginJsExtensions.Callback {

    private val binding by viewBinding(DialogLoginBinding::bind)
    private val viewModel by activityViewModels<SourceLoginViewModel>()
    private var lastClickTime: Long = 0
    private var oKToClose = false
    private var rowUis: List<RowUi>? = null
    private var rowUiName = arrayListOf<String>()
    private var hasChange = false
    private var loginUrl: String? = null
    private var isLoginUiV2 = false
    private var v2State: Any? = linkedMapOf<String, Any?>()
    private var v2Ui: SourceLoginV2Ui? = null
    private val v2Form = linkedMapOf<String, String>()
    private var v2Errors: Map<String, String> = emptyMap()
    private var v2GlobalError: String? = null
    private val v2TextInputs = linkedMapOf<String, TextInputLayout>()
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

    @SuppressLint("SetTextI18n")
    private fun handleUpUiData(data: Map<String, Any?>?) {
        hasChange = true
        if (data == null) {
            val newLoginInfo: MutableMap<String, String> = mutableMapOf()
            rowUis?.forEachIndexed { index, rowUi ->
                val default = rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = default ?: ""
                        newLoginInfo[rowUi.name] = value
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = rowUi.viewName ?: rowUi.name
                            }
                            Type.toggle -> {
                                val char = default ?: run{
                                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                newLoginInfo[rowUi.name] = char
                                val name =  rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val chars = rowUi.chars?.filterNotNull() ?: listOf("chars","is null")
                        val index = chars.indexOf(default)
                        newLoginInfo[rowUi.name] = default ?: run{
                            chars.getOrNull(0) ?: ""
                        }
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)?.setSelectionSafely(index)
                    }
                }
            }
            viewModel.loginInfo = newLoginInfo
            return
        }
        val loginInfo = viewModel.loginInfo
        data.forEach { (key, value) ->
            val value = value?.toString()
            val index = rowUiName.indexOf(key)
            if (index != -1) {
                val rowUi = rowUis?.getOrNull(index) ?: return@forEach
                val value = value ?: rowUi.default
                when (val rowView = binding.root.findViewById<View>(index + 1000)) {
                    is TextInputLayout -> {
                        val value = value ?: ""
                        loginInfo[rowUi.name] = value
                        rowView.editText?.setText(value)
                    }

                    is TextView -> {
                        when (rowUi.type) {
                            Type.button -> {
                                rowView.text = value ?: rowUi.viewName ?: key
                            }

                            Type.toggle -> {
                                val char = value ?: run{
                                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                                    chars.getOrNull(0) ?: ""
                                }
                                loginInfo[rowUi.name] = char
                                val name =  rowUi.viewName ?: rowUi.name
                                val left = rowUi.style?.layout_justifySelf != "right"
                                rowView.text = if (left) char + name else name + char
                            }
                        }
                    }

                    is LinearLayout -> {
                        val items = rowUi.chars?.filterNotNull() ?: listOf("chars","is null")
                        val index = items.indexOf(value)
                        rowView.findViewById<AppCompatSpinner>(R.id.sp_type)?.setSelectionSafely(index)
                    }
                }
            } else {
                loginInfo[key] = value ?: ""
            }
        }
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

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun rowUiBuilder(source: BaseSource, rowUis: List<RowUi>?, deltaUp: Boolean) {
        val loginInfo = viewModel.loginInfo
        if (!deltaUp) {
            binding.flexbox.removeAllViews()
            rowUiName.clear()
        }
        rowUis?.forEachIndexed { index, rowUi ->
            val type = rowUi.type
            val name = rowUi.name
            val viewName = rowUi.viewName
            val action = rowUi.action
            val default = rowUi.default
            var insertIndex = -1
            if (deltaUp) {
                val oldIndex = rowUiName.indexOf(name, index)
                if (oldIndex == index) {
                    binding.flexbox.getChildAt(oldIndex)?.let {
                        it.id = index + 1000
                        return@forEachIndexed
                    }
                } else if (oldIndex >= 0) {
                    binding.flexbox.getChildAt(oldIndex)?.let {
                        binding.flexbox.removeView(it)
                        rowUiName.removeAt(oldIndex)
                        binding.flexbox.addView(it, index)
                        rowUiName.add(index, name)
                        it.id = index + 1000
                        return@forEachIndexed
                    }
                }
                if (oldIndex == -2) {
                    rowUiName.add(name)
                } else {
                    rowUiName.add(index, name)
                    insertIndex = index
                }
            } else {
                rowUiName.add(name)
            }
            when (type) {
                Type.text -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    val editText = it.editText
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "center" -> editText.gravity = Gravity.CENTER
                            "flex_end" -> editText.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textInputLayout.hint = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.textInputLayout.hint = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.textInputLayout.hint = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textInputLayout.hint = "null"
                            } else {
                                it.textInputLayout.hint = n
                            }
                        }.onError{ _ ->
                            it.textInputLayout.hint = "err"
                        }
                    }
                    editText.setText(loginInfo[name] ?: default)
                    action?.let { jsStr ->
                        val watcher = object : TextWatcher {
                            private var content: String? = null
                            private val runnable = Runnable {
                                handleButtonClick(source, jsStr, name, false)
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                                content = s.toString()
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                val reContent = s.toString()
                                if (reContent != content) {
                                    handler.removeCallbacks(runnable)
                                    handler.postDelayed(runnable, 600)
                                }
                            }
                        }
                        editText.addTextChangedListener(watcher)
                    }
                }

                Type.password -> ItemSourceEditBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    val editText = it.editText
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "center" -> editText.gravity = Gravity.CENTER
                            "flex_end" -> editText.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textInputLayout.hint = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.textInputLayout.hint = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.textInputLayout.hint = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textInputLayout.hint = "null"
                            } else {
                                it.textInputLayout.hint = n
                            }
                        }.onError{ _ ->
                            it.textInputLayout.hint = "err"
                        }
                    }
                    editText.inputType =
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                    editText.setText(loginInfo[name] ?: default)
                    action?.let { jsStr ->
                        val watcher = object : TextWatcher {
                            private var content: String? = null
                            private val runnable = Runnable {
                                handleButtonClick(source, jsStr, name, false)
                            }
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                                content = s.toString()
                            }

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(s: Editable?) {
                                val reContent = s.toString()
                                if (reContent != content) {
                                    handler.removeCallbacks(runnable)
                                    handler.postDelayed(runnable, 600)
                                }
                            }
                        }
                        editText.addTextChangedListener(watcher)
                    }
                }

                Type.select -> ItemSelectorSingleBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    if (viewName == null) {
                        it.spName.text = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        it.spName.text = viewName.substring(1, viewName.length - 1)
                    } else {
                        it.spName.text = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.spName.text = "null"
                            } else {
                                it.spName.text = n
                            }
                        }.onError{ _ ->
                            it.spName.text = "err"
                        }
                    }
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars","is null")
                    val adapter = ArrayAdapter(
                        requireContext(),
                        R.layout.item_text_common,
                        chars
                    )
                    adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                    val selector = it.spType
                    selector.adapter = adapter
                    val infoV = loginInfo[name]
                    val char = if (infoV.isNullOrEmpty()) {
                        hasChange = true
                        default ?: chars[0]
                    } else {
                        infoV
                    }
                    loginInfo[name] = char
                    val i = chars.indexOf(char)
                    selector.setSelectionSafely(i)
                    selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        var isInitializing = true
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (isInitializing) { //忽略初始化选择
                                isInitializing = false
                                return
                            }
                            hasChange = true
                            loginInfo[name] = chars[position]
                            if (action != null) {
                                handleButtonClick(source, action, name, false)
                            }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {
                        }
                    }
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> selector.gravity = Gravity.START
                            "flex_end" -> selector.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                }

                Type.button -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> it.textView.gravity = Gravity.START
                            "flex_end" -> it.textView.gravity = Gravity.END
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    if (viewName == null) {
                        it.textView.text = name
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        val n = viewName.substring(1, viewName.length - 1)
                        rowUi.viewName = n
                        it.textView.text = n
                    } else {
                        it.textView.text = name
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textView.text = "null"
                            } else {
                                rowUi.viewName = n //在回调handleUIDataUpdate用
                                it.textView.text = n
                            }
                        }.onError{ _ ->
                            it.textView.text = "err"
                        }
                    }
                    it.textView.setPadding(16.dpToPx())
                    var downTime = 0L
                    it.root.setOnClickListener { //无障碍点击
                        handleButtonClick(source, action, name, false)
                    }
                    it.root.setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                view.isSelected = true
                                downTime = System.currentTimeMillis()
                            }
                            MotionEvent.ACTION_UP -> {
                                view.isSelected = false
                                val upTime = System.currentTimeMillis()
                                if (upTime - lastClickTime < 200) {
                                    return@setOnTouchListener true
                                }
                                lastClickTime = upTime
                                handleButtonClick(source, action, name, upTime > downTime + 666)
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                view.isSelected = false
                            }
                        }
                        return@setOnTouchListener true
                    }
                }

                Type.toggle -> ItemFilletTextBinding.inflate(
                    layoutInflater,
                    binding.root,
                    false
                ).let {
                    var newName = name
                    var left = true
                    binding.flexbox.addView(it.root, insertIndex)
                    rowUi.style().apply {
                        when (this.layout_justifySelf) {
                            "flex_start" -> it.textView.gravity = Gravity.START
                            "flex_end" -> it.textView.gravity = Gravity.END
                            "right" -> left = false
                        }
                        apply(it.root)
                    }
                    it.root.id = index + 1000
                    val chars = rowUi.chars?.filterNotNull() ?: listOf("chars is null")
                    val infoV = loginInfo[name]
                    var char = if (infoV.isNullOrEmpty()) {
                        hasChange = true
                        default ?: chars[0]
                    } else {
                        infoV
                    }
                    loginInfo[name] = char
                    if (viewName == null) {
                        it.textView.text = if (left) char + name else name + char
                    } else if (viewName.length in 3..19 && viewName.first() == '\'' && viewName.last() == '\'') {
                        val n = viewName.substring(1, viewName.length - 1)
                        rowUi.viewName = n
                        newName = n
                        it.textView.text = if (left) char + n else n + char
                    } else {
                        it.textView.text = if (left) char + name else name + char
                        execute {
                            evalUiJs(viewName)
                        }.onSuccess { n ->
                            if (n.isNullOrEmpty()) {
                                it.textView.text = char + "null"
                            } else {
                                rowUi.viewName = n //存放新名字，在回调handleUIDataUpdate时用
                                newName = n //下面切换时用
                                it.textView.text = if (left) char + n else n + char
                            }
                        }.onError{ _ ->
                            it.textView.text = char + "err"
                        }
                    }
                    it.textView.setPadding(16.dpToPx())
                    var downTime = 0L
                    it.root.setOnClickListener { _ ->
                        val currentIndex = chars.indexOf(char)
                        val nextIndex = (currentIndex + 1) % chars.size
                        char = chars.getOrNull(nextIndex) ?: ""
                        hasChange = true
                        loginInfo[name] = char
                        it.textView.text = if (left) char + newName else newName + char
                        handleButtonClick(source, action, name, false)
                    }
                    it.root.setOnTouchListener { view, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                view.isSelected = true
                                downTime = System.currentTimeMillis()
                            }
                            MotionEvent.ACTION_UP -> {
                                view.isSelected = false
                                val upTime = System.currentTimeMillis()
                                if (upTime - lastClickTime < 200) {
                                    return@setOnTouchListener true
                                }
                                lastClickTime = upTime
                                val currentIndex = chars.indexOf(char)
                                val nextIndex = (currentIndex + 1) % chars.size
                                char = chars.getOrNull(nextIndex) ?: ""
                                hasChange = true
                                loginInfo[name] = char
                                it.textView.text = if (left) char + newName else newName + char
                                handleButtonClick(source, action, name, upTime > downTime + 666)
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                view.isSelected = false
                            }
                        }
                        return@setOnTouchListener true
                    }
                }
            }
        }
        if (deltaUp && rowUis != null) {
            val newRowUiSize = rowUis.size
            for (i in binding.flexbox.childCount - 1 downTo newRowUiSize) {
                binding.flexbox.removeViewAt(i)
            }
            if (rowUiName.size > newRowUiSize) {
                rowUiName.subList(newRowUiSize, rowUiName.size).clear()
            }
        }
    }

    private fun buttonUi(source: BaseSource, rowUis: List<RowUi>?) {
        rowUiBuilder(source, rowUis, false)
        binding.toolBar.setOnMenuItemClickListener { item ->
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

    @SuppressLint("SetTextI18n")
    private fun renderV2LoginUi(ui: SourceLoginV2Ui) {
        v2CountdownTimers.values.forEach(CountDownTimer::cancel)
        v2CountdownTimers.clear()
        v2TextInputs.clear()
        binding.flexbox.removeAllViews()
        v2GlobalError?.takeIf { it.isNotBlank() }?.let { message ->
            binding.flexbox.addView(TextView(requireContext()).apply {
                text = message
                setTextColor(Color.RED)
            })
        }
        ui.rows.forEach { row ->
            when (row.type) {
                "label" -> {
                    val label = TextView(requireContext()).apply { text = row.label }
                    binding.flexbox.addView(label)
                }

                "text", "password" -> {
                    val key = row.key
                    val editor = ItemSourceEditBinding.inflate(layoutInflater, binding.root, false)
                    val initial = if (key == null) "" else {
                        row.value ?: v2Form[key] ?: viewModel.loginInfo[key].orEmpty()
                    }
                    if (key != null) v2Form[key] = initial
                    editor.textInputLayout.hint = row.label
                    row.hint?.let { editor.textInputLayout.placeholderText = it }
                    editor.textInputLayout.error = key?.let(v2Errors::get)
                    if (row.type == "password") {
                        editor.editText.inputType =
                            InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                    }
                    editor.editText.setText(initial)
                    editor.editText.addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            if (key != null) v2Form[key] = s?.toString().orEmpty()
                            editor.textInputLayout.error = null
                        }
                    })
                    if (key != null) v2TextInputs[key] = editor.textInputLayout
                    row.style?.let { style ->
                        when (style.layout_justifySelf) {
                            "center" -> editor.editText.gravity = Gravity.CENTER
                            "flex_end" -> editor.editText.gravity = Gravity.END
                        }
                        style.apply(editor.root)
                    }
                    binding.flexbox.addView(editor.root)
                }

                "select" -> {
                    val key = row.key
                    val selector = ItemSelectorSingleBinding.inflate(layoutInflater, binding.root, false)
                    selector.spName.text = row.label
                    selector.spName.error = key?.let(v2Errors::get)
                    val options = row.options.orEmpty()
                    val initial = if (key == null) "" else {
                        row.value ?: v2Form[key] ?: viewModel.loginInfo[key].orEmpty()
                    }
                    if (key != null) v2Form[key] = initial
                    selector.spType.adapter = ArrayAdapter(
                        requireContext(),
                        R.layout.item_text_common,
                        options
                    ).apply { setDropDownViewResource(R.layout.item_spinner_dropdown) }
                    val initialIndex = options.indexOf(initial)
                    if (initialIndex >= 0) {
                        selector.spType.setSelectionSafely(initialIndex)
                    } else {
                        selector.spType.setSelection(-1)
                    }
                    selector.spType.isEnabled = options.isNotEmpty()
                    selector.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        private var initializing = true
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            if (initializing) {
                                initializing = false
                                return
                            }
                            if (key != null) v2Form[key] = options[position]
                            selector.spName.error = null
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    }
                    row.style?.apply(selector.root)
                    binding.flexbox.addView(selector.root)
                }

                "toggle" -> {
                    val key = row.key
                    val toggle = SwitchCompat(requireContext())
                    val initial = row.value ?: key?.let(v2Form::get)
                        ?: key?.let(viewModel.loginInfo::get) ?: "false"
                    if (key != null) v2Form[key] = initial
                    toggle.text = row.label
                    toggle.isChecked = initial == "true"
                    toggle.setError(key?.let(v2Errors::get))
                    toggle.setOnCheckedChangeListener { button, checked ->
                        if (key != null) v2Form[key] = checked.toString()
                        button.error = null
                        row.action?.takeIf { it.isNotBlank() }?.let { action ->
                            scheduleV2Action(key ?: action, action, immediate = true)
                        }
                    }
                    row.style?.apply(toggle)
                    binding.flexbox.addView(toggle)
                }

                "button" -> {
                    val button = ItemFilletTextBinding.inflate(layoutInflater, binding.root, false)
                    button.textView.text = row.label
                    button.root.isEnabled = !row.action.isNullOrBlank()
                    button.root.setOnClickListener {
                        val seconds = row.countdown?.takeIf { it > 0 } ?: 0
                        val action = row.action ?: return@setOnClickListener
                        if (seconds > 0) {
                            v2CountdownEndsAt[action] = SystemClock.elapsedRealtime() + seconds * 1_000L
                            updateV2Countdown(action, row.label, button.textView)
                        }
                        performV2Action(action)
                    }
                    row.style?.apply(button.root)
                    binding.flexbox.addView(button.root)
                    row.action?.let { action -> updateV2Countdown(action, row.label, button.textView) }
                }
            }
        }
    }

    private fun scheduleV2Action(key: String, action: String?, immediate: Boolean = false) {
        if (action.isNullOrBlank()) return
        val callback = Runnable { performV2Action(action) }
        v2ActionRunnables.remove(key)?.let(handler::removeCallbacks)
        v2ActionRunnables[key] = callback
        if (immediate) callback.run() else handler.postDelayed(callback, 600)
    }

    private fun updateV2Countdown(action: String, label: String, textView: TextView) {
        val endAt = v2CountdownEndsAt[action] ?: return
        val remaining = endAt - SystemClock.elapsedRealtime()
        if (remaining <= 0) {
            v2CountdownEndsAt.remove(action)
            textView.text = label
            textView.isEnabled = true
            return
        }
        textView.isEnabled = false
        textView.text = "$label (${(remaining + 999) / 1_000})"
        val timer = object : CountDownTimer(remaining, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                textView.text = "$label (${(millisUntilFinished + 999) / 1_000})"
            }

            override fun onFinish() {
                v2CountdownEndsAt.remove(action)
                textView.text = label
                textView.isEnabled = true
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

        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getString(R.string.login_source, source.getTag())
        binding.toolBar.inflateMenu(R.menu.source_login)
        binding.toolBar.menu.applyTint(requireContext())

        if (loginUiStr == JsSourceConfig.LOGIN_UI_V2_MARKER) {
            isLoginUiV2 = true
            buttonUi(source, null)
            binding.toolBar.menu.findItem(R.id.menu_ok)?.isVisible = false
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
        val loginData = hashMapOf<String, String>()
        rowUis?.forEachIndexed { index, rowUi ->
            when (rowUi.type) {
                Type.text, Type.password -> {
                    val rowView = binding.root.findViewById<View>(index + 1000)
                    ItemSourceEditBinding.bind(rowView).editText.text.let {
                        loginData[rowUi.name] = it?.toString() ?: rowUi.default ?: "" //没文本的时候存空字符串,而不是删除loginInfo
                    }
                }
            }
        }
        return viewModel.loginInfo.toMutableMap().apply { putAll(loginData) }
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
