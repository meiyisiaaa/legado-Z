package io.legado.app.ui.main.explore

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.collection.LruCache
import androidx.core.view.children
import androidx.core.view.isVisible
import com.google.android.flexbox.FlexboxLayout
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.databinding.ItemFilletCompleteTextBinding
import io.legado.app.databinding.ItemFilletSelectorSingleBinding
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemFindBookBinding
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.InfoMap
import io.legado.app.utils.activity
import io.legado.app.utils.setSelectionSafely
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Retains the shared discovery InfoMap cache used by source and WebBook helpers. */
class ExploreAdapter private constructor() {
    companion object {
        val exploreInfoMapList = LruCache<String, InfoMap>(99)
    }
}

/**
 * Renders only the legacy dynamic form controls inside a Compose-owned source card.
 * The renderer has no adapter position and owns all view listeners and script jobs.
 */
internal class ExploreKindRenderer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onOpenExplore: (String, String, String?) -> Unit,
    private val onRefresh: (String) -> Unit
) {
    private var root: View? = null
    private var flexbox: FlexboxLayout? = null
    private var renderedSourceUrl: String? = null
    private var renderedKinds: List<ExploreKind>? = null
    private var renderedRevision = Int.MIN_VALUE
    private var renderedActive = false
    private var generation = 0
    private val jobs = ConcurrentHashMap.newKeySet<Job>()
    private var loginCallback: SourceLoginJsExtensions.Callback? = null
    private var loginExtensions: SourceLoginJsExtensions? = null

    fun createView(): View {
        val binding = ItemFindBookBinding.inflate(LayoutInflater.from(context), null, false)
        binding.llTitle.removeFromParent()
        binding.root.background = null
        binding.root.isFocusable = false
        binding.root.isClickable = false
        binding.flexbox.isVisible = true
        root = binding.root
        flexbox = binding.flexbox
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    fun render(sourcePart: BookSourcePart, kinds: List<ExploreKind>, revision: Int, active: Boolean) {
        if (renderedSourceUrl == sourcePart.bookSourceUrl && renderedKinds == kinds &&
            renderedRevision == revision && renderedActive == active
        ) return

        renderedSourceUrl = sourcePart.bookSourceUrl
        renderedKinds = kinds
        renderedRevision = revision
        renderedActive = active
        generation++
        cancelJobs()
        val flexbox = flexbox ?: return
        clearControls(flexbox)
        if (!active || kinds.isEmpty()) return

        val source = appDb.bookSourceDao.getBookSource(sourcePart.bookSourceUrl) ?: return
        val infoMap = ExploreAdapter.exploreInfoMapList[sourcePart.bookSourceUrl]
            ?: InfoMap(sourcePart.bookSourceUrl).also {
                ExploreAdapter.exploreInfoMapList.put(sourcePart.bookSourceUrl, it)
            }
        val currentGeneration = generation
        val callback = object : SourceLoginJsExtensions.Callback {
            override fun upUiData(data: Map<String, Any?>?) = Unit
            override fun reUiView(deltaUp: Boolean) {
                scope.launch { onRefresh(sourcePart.bookSourceUrl) }
            }
        }
        loginCallback = callback
        loginExtensions = SourceLoginJsExtensions(
            root?.activity,
            source,
            callback = callback
        )
        kinds.forEach { kind ->
            when (kind.type) {
                Type.url -> renderUrl(flexbox, sourcePart, source, infoMap, kind, currentGeneration)
                Type.button -> renderButton(flexbox, source, infoMap, kind, currentGeneration)
                Type.text -> renderText(flexbox, source, infoMap, kind, currentGeneration)
                Type.toggle -> renderToggle(flexbox, source, infoMap, kind, currentGeneration)
                Type.select -> renderSelect(flexbox, source, infoMap, kind, currentGeneration)
            }
        }
    }

    fun dispose() {
        generation++
        cancelJobs()
        flexbox?.let(::clearControls)
        loginCallback = null
        loginExtensions = null
        renderedSourceUrl = null
        renderedKinds = null
        root = null
        flexbox = null
    }

    private fun renderUrl(
        flexbox: FlexboxLayout,
        sourcePart: BookSourcePart,
        source: BookSource,
        infoMap: InfoMap,
        kind: ExploreKind,
        currentGeneration: Int
    ) {
        val view = newTextView(flexbox, kind)
        view.text = labelFor(kind)
        evaluateLabel(kind, source, infoMap, currentGeneration) { value -> view.text = value }
        view.setOnClickListener {
            val url = kind.url?.takeIf(String::isNotBlank) ?: return@setOnClickListener
            if (kind.title.startsWith("ERROR:")) {
                it.activity?.showDialogFragment(TextDialog("ERROR", url))
            } else {
                onOpenExplore(sourcePart.bookSourceUrl, kind.title, url)
            }
        }
    }

    private fun renderButton(
        flexbox: FlexboxLayout,
        source: BookSource,
        infoMap: InfoMap,
        kind: ExploreKind,
        currentGeneration: Int
    ) {
        val view = newTextView(flexbox, kind)
        view.text = labelFor(kind)
        evaluateLabel(kind, source, infoMap, currentGeneration) { value -> view.text = value }
        view.setOnClickListener { runAction(kind.action, source, infoMap, kind.title) }
    }

    private fun renderText(
        flexbox: FlexboxLayout,
        source: BookSource,
        infoMap: InfoMap,
        kind: ExploreKind,
        currentGeneration: Int
    ) {
        val view = ItemFilletCompleteTextBinding.inflate(
            LayoutInflater.from(context), flexbox, false
        ).root
        applyStyle(kind, view, inputGravity(kind))
        view.hint = labelFor(kind)
        evaluateLabel(kind, source, infoMap, currentGeneration) { value -> view.hint = value }
        view.setText(infoMap[kind.title])
        var previousText: String? = null
        var actionJob: Job? = null
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                previousText = s?.toString().orEmpty()
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                infoMap[kind.title] = text
                if (kind.action != null && text != previousText) {
                    actionJob?.cancel()
                    actionJob = launchTracked {
                        delay(600)
                        evalButtonClick(kind.action, source, infoMap, kind.title, loginExtensions)
                    }
                }
            }
        }
        view.setTag(R.id.text_watcher, watcher)
        view.addTextChangedListener(watcher)
        flexbox.addView(view)
    }

    private fun renderToggle(
        flexbox: FlexboxLayout,
        source: BookSource,
        infoMap: InfoMap,
        kind: ExploreKind,
        currentGeneration: Int
    ) {
        val view = newTextView(flexbox, kind)
        val chars = kind.chars?.filterNotNull().orEmpty().ifEmpty { listOf("") }
        val left = kind.style().layout_justifySelf != "right"
        var currentChar = infoMap[kind.title]?.takeIf(String::isNotEmpty)
            ?: (kind.default ?: chars.first()).also { infoMap[kind.title] = it }
        var displayName = staticLabel(kind.viewName) ?: kind.title
        fun updateText() {
            view.text = if (left) currentChar + displayName else displayName + currentChar
        }
        applyStyle(kind, view, toggleGravity(kind))
        updateText()
        evaluateViewName(kind.viewName, source, infoMap, currentGeneration) { value ->
            displayName = value
            updateText()
        }
        view.setOnClickListener {
            val nextIndex = (chars.indexOf(currentChar).coerceAtLeast(-1) + 1) % chars.size
            currentChar = chars[nextIndex]
            infoMap[kind.title] = currentChar
            updateText()
            runAction(kind.action, source, infoMap, kind.title)
        }
    }

    private fun renderSelect(
        flexbox: FlexboxLayout,
        source: BookSource,
        infoMap: InfoMap,
        kind: ExploreKind,
        currentGeneration: Int
    ) {
        val binding = ItemFilletSelectorSingleBinding.inflate(
            LayoutInflater.from(context), flexbox, false
        )
        val selector = binding.spType
        val choices = kind.chars?.filterNotNull().orEmpty().ifEmpty { listOf("chars", "is null") }
        val adapter = ArrayAdapter(context, R.layout.item_text_common, choices).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        selector.adapter = adapter
        val selected = infoMap[kind.title]?.takeIf(String::isNotEmpty)
            ?: (kind.default ?: choices.first()).also { infoMap[kind.title] = it }
        selector.setSelectionSafely(choices.indexOf(selected).coerceAtLeast(0))
        var initializing = true
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initializing) {
                    initializing = false
                    return
                }
                choices.getOrNull(position)?.let { value ->
                    infoMap[kind.title] = value
                    runAction(kind.action, source, infoMap, kind.title)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.spName.text = labelFor(kind)
        evaluateLabel(kind, source, infoMap, currentGeneration) { value -> binding.spName.text = value }
        applyStyle(kind, binding.root, textGravity(kind))
        flexbox.addView(binding.root)
    }

    private fun newTextView(flexbox: FlexboxLayout, kind: ExploreKind): TextView {
        val view = ItemFilletTextBinding.inflate(LayoutInflater.from(context), flexbox, false).root
        applyStyle(kind, view, textGravity(kind))
        flexbox.addView(view)
        return view
    }

    private fun applyStyle(kind: ExploreKind, view: View, gravity: Int) {
        if (view is TextView) view.gravity = gravity
        if (view is LinearLayout) view.gravity = gravity
        kind.style().apply(view)
    }

    private fun textGravity(kind: ExploreKind): Int = when (kind.style().layout_justifySelf) {
        "flex_start" -> Gravity.START
        "flex_end" -> Gravity.END
        "center" -> Gravity.CENTER
        else -> Gravity.CENTER
    }

    private fun inputGravity(kind: ExploreKind): Int = when (kind.style().layout_justifySelf) {
        "center" -> Gravity.CENTER
        "flex_end" -> Gravity.END
        else -> Gravity.START
    }

    private fun toggleGravity(kind: ExploreKind): Int = when (kind.style().layout_justifySelf) {
        "flex_start" -> Gravity.START
        "flex_end" -> Gravity.END
        else -> Gravity.CENTER
    }

    private fun labelFor(kind: ExploreKind): String = staticLabel(kind.viewName) ?: kind.title

    private fun staticLabel(value: String?): String? = value
        ?.takeIf { it.length in 3..19 && it.first() == '\'' && it.last() == '\'' }
        ?.let { it.substring(1, it.lastIndex) }

    private fun evaluateLabel(
        kind: ExploreKind,
        source: BookSource,
        infoMap: InfoMap,
        currentGeneration: Int,
        update: (String) -> Unit
    ) {
        evaluateViewName(kind.viewName, source, infoMap, currentGeneration, update)
    }

    private fun evaluateViewName(
        viewName: String?,
        source: BookSource,
        infoMap: InfoMap,
        currentGeneration: Int,
        update: (String) -> Unit
    ) {
        if (viewName.isNullOrBlank() || staticLabel(viewName) != null) return
        launchTracked {
            val value = evalUiJs(viewName, source, infoMap) ?: "null"
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (currentGeneration == generation) update(value)
            }
        }
    }

    private fun runAction(action: String?, source: BookSource, infoMap: InfoMap, name: String) {
        val js = action?.takeIf(String::isNotBlank) ?: return
        launchTracked { evalButtonClick(js, source, infoMap, name, loginExtensions) }
    }

    private fun launchTracked(block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(IO, start = CoroutineStart.LAZY, block = block)
        jobs.add(job)
        job.invokeOnCompletion { jobs.remove(job) }
        job.start()
        return job
    }

    private fun cancelJobs() {
        jobs.toList().forEach(Job::cancel)
        jobs.clear()
    }

    private fun clearControls(flexbox: FlexboxLayout) {
        flexbox.children.toList().forEach { child ->
            when (child) {
                is AutoCompleteTextView -> {
                    (child.getTag(R.id.text_watcher) as? TextWatcher)?.let(child::removeTextChangedListener)
                    child.setTag(R.id.text_watcher, null)
                    child.setOnClickListener(null)
                    child.setOnLongClickListener(null)
                    child.setAdapter(null)
                }
                is LinearLayout -> {
                    child.findViewById<AppCompatSpinner>(R.id.sp_type)?.apply {
                        onItemSelectedListener = null
                        adapter = null
                    }
                    child.setOnClickListener(null)
                    child.setOnLongClickListener(null)
                }
                is TextView -> {
                    child.setOnClickListener(null)
                    child.setOnLongClickListener(null)
                }
            }
            child.setOnTouchListener(null)
        }
        flexbox.removeAllViews()
        loginCallback = null
        loginExtensions = null
    }

    private suspend fun evalUiJs(js: String, source: BookSource, infoMap: InfoMap): String? {
        return try {
            runScriptWithContext {
                source.evalJS(js) { put("infoMap", infoMap) }.toString()
            }
        } catch (error: Exception) {
            AppLog.put(source.getTag() + " exploreUi err:" + (error.localizedMessage ?: error.toString()), error)
            null
        }
    }

    private suspend fun evalButtonClick(
        js: String,
        source: BaseSource,
        infoMap: InfoMap,
        name: String,
        extensions: SourceLoginJsExtensions?
    ) {
        try {
            runScriptWithContext {
                source.evalJS(js) {
                    put("java", extensions)
                    put("infoMap", infoMap)
                }
            }
        } catch (error: Exception) {
            AppLog.put("ExploreUI Button $name JavaScript error", error)
        }
    }

    private fun View.removeFromParent() {
        (parent as? ViewGroup)?.removeView(this)
    }
}
