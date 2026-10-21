package io.legado.app.ui.dict

import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.DictRule
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 词典查询结果。富文本仍由现有 ScrollTextView / Markwon / Glide 引擎渲染。 */
class DictDialog() : BaseDialogFragment(R.layout.dialog_dict) {

    constructor(word: String) : this() {
        arguments = Bundle().apply { putString("word", word) }
    }

    private val viewModel by viewModels<DictViewModel>()
    private var word: String? = null
    private var rules by mutableStateOf(emptyList<DictRule>())
    private var selectedIndex by mutableStateOf(0)
    private var loading by mutableStateOf(false)
    private var renderedText by mutableStateOf("")
    private var selectedRule: DictRule? = null
    private var glideImageGetter: GlideImageGetter? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        word = arguments?.getString("word")
        if (word.isNullOrEmpty()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }
        bindingCompose(view)
        viewModel.initData { loadedRules ->
            rules = loadedRules
            if (loadedRules.isEmpty()) {
                renderedText = getString(R.string.empty)
            } else {
                query(0)
            }
        }
    }

    private fun bindingCompose(view: View) {
        val composeView = view.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.composeView)
        composeView.setContent {
            LegadoTheme {
                DictContent(
                    rules = rules,
                    selectedIndex = selectedIndex,
                    loading = loading,
                    text = renderedText,
                    onSelect = ::query,
                    onCreateTextView = { context ->
                        val sidePadding = (16 * context.resources.displayMetrics.density).toInt()
                        val verticalPadding = (12 * context.resources.displayMetrics.density).toInt()
                        ScrollTextView(context, null).apply {
                            movementMethod = LinkMovementMethod()
                            setTextColor(context.resources.getColor(R.color.secondaryText, context.theme))
                            setPadding(sidePadding, verticalPadding, sidePadding, verticalPadding + sidePadding / 4)
                            glideImageGetter = GlideImageGetter(
                                requireContext(),
                                this,
                                this@DictDialog.lifecycle,
                                width - paddingLeft - paddingRight
                            )
                        }
                    },
                    onRender = ::renderText
                )
            }
        }
    }

    private fun query(index: Int) {
        val rule = rules.getOrNull(index) ?: return
        val queryWord = word ?: return
        selectedIndex = index
        selectedRule = rule
        loading = true
        viewModel.dict(rule, queryWord) { result ->
            renderedText = result
            loading = false
        }
    }

    private fun renderText(textView: ScrollTextView, value: String) {
        val rule = selectedRule
        if (rule == null) {
            textView.text = value
            return
        }
        val trimmed = value.trimStart()
        if (trimmed.startsWith("<md>")) {
            val lastIndex = trimmed.lastIndexOf("<")
            if (lastIndex < 4) {
                textView.text = trimmed
                return
            }
            val markdownText = trimmed.substring(4, lastIndex)
            viewLifecycleOwner.lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    textView.setTextClassifier(TextClassifier.NO_OP)
                }
                val markwon: Markwon
                val markdown = withContext(IO) {
                    val imageWidth = (textView.width - textView.paddingLeft - textView.paddingRight)
                        .coerceAtLeast(1)
                    markwon = Markwon.builder(requireContext())
                        .usePlugin(
                            GlideImagesPlugin.create(
                                Glide.with(requireContext())
                                    .applyDefaultRequestOptions(
                                        RequestOptions().override(imageWidth).encodeQuality(88)
                                    )
                            )
                        )
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(requireContext()))
                        .build()
                    markwon.toMarkdown(markdownText)
                }
                textView.setMarkdown(markwon, markdown) { source ->
                    showDialogFragment(PhotoDialog(source))
                }
            }
            return
        }
        val tagHandler = TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(rule, "button $name", click)
            }
        })
        val imageGetter = glideImageGetter ?: return
        textView.setHtml(
            value,
            imageGetter,
            tagHandler,
            imgOnLongClickListener = { source -> showDialogFragment(PhotoDialog(source)) },
            imgOnClickListener = { click -> viewModel.onButtonClick(rule, "image", click) }
        )
    }

    override fun onDestroyView() {
        glideImageGetter?.clear()
        glideImageGetter = null
        super.onDestroyView()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DictContent(
    rules: List<DictRule>,
    selectedIndex: Int,
    loading: Boolean,
    text: String,
    onSelect: (Int) -> Unit,
    onCreateTextView: (android.content.Context) -> ScrollTextView,
    onRender: (ScrollTextView, String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "词典 · ${rules.getOrNull(selectedIndex)?.name ?: "查询结果"}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp)
        )
        if (rules.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex.coerceIn(rules.indices),
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                rules.forEachIndexed { index, rule ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        text = { Text(rule.name, maxLines = 1) }
                    )
                }
            }
        }
        if (loading) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (rules.isEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 560.dp),
                    factory = onCreateTextView,
                    update = { onRender(it, text) }
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
