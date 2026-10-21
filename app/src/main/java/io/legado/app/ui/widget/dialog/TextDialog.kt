package io.legado.app.ui.widget.dialog

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.CacheManager
import io.legado.app.help.IntentData
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.code.CodeTextTransfer
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.setHtml
import io.legado.app.utils.setLayout
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextDialog() : BaseDialogFragment(0) {

    enum class Mode {
        MD, HTML, TEXT
    }

    constructor(
        title: String,
        content: String?,
        mode: Mode = Mode.TEXT,
        time: Long = 0,
        autoClose: Boolean = false
    ) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("content", IntentData.put(content))
            putString("mode", mode.name)
            putLong("time", time)
        }
        isCancelable = false
        this.autoClose = autoClose
    }

    private var time = 0L
    private var autoClose: Boolean = false
    private var badgeSeconds by mutableStateOf<Int?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                val args = arguments
                val title = args?.getString("title").orEmpty()
                val content = IntentData.get<String>(args?.getString("content")).orEmpty()
                val mode = args?.getString("mode")
                TextDialogContent(
                    title = title,
                    content = content,
                    mode = mode,
                    badgeSeconds = badgeSeconds,
                    onClose = { dismissAllowingStateLoss() },
                    onEdit = { openEditor(title, content, mode) },
                    onImageLongClick = { source -> showDialogFragment(PhotoDialog(source)) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        time = arguments?.getLong("time", 0L) ?: 0L
        if (time > 0) {
            badgeSeconds = (time / 1000).toInt()
            viewLifecycleOwner.lifecycleScope.launch {
                while (time > 0) {
                    delay(1000)
                    time -= 1000
                    badgeSeconds = (time / 1000).toInt()
                    if (time <= 0) {
                        dialog?.setCancelable(true)
                        if (autoClose) dialog?.cancel()
                    }
                }
            }
        } else {
            view.post { dialog?.setCancelable(true) }
        }
    }

    private fun openEditor(title: String, content: String, mode: String?) {
        val intent = android.content.Intent(requireContext(), CodeEditActivity::class.java).apply {
            putExtra("title", title)
            putExtra(
                "languageName",
                if (mode == Mode.MD.name) "text.html.markdown" else "text.html.basic"
            )
        }
        if (content.length > CodeTextTransfer.MAX_INLINE_LENGTH) {
            intent.putExtra(CodeTextTransfer.EXTRA_READ_ONLY, true)
            CodeTextTransfer.launch(requireContext(), intent, content) {
                requireContext().startActivity(it)
            }
        } else {
            val cacheKey = "code_text_${System.currentTimeMillis()}"
            CacheManager.putMemory(cacheKey, content)
            intent.putExtra("cacheKey", cacheKey)
            startActivity<CodeEditActivity> { putExtras(intent) }
        }
    }
}

@Composable
internal fun TextDialogContent(
    title: String,
    content: String,
    mode: String?,
    badgeSeconds: Int?,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onImageLongClick: (String) -> Unit
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(GlideImagesPlugin.create(Glide.with(context)))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    var textView by remember { mutableStateOf<ScrollTextView?>(null) }

    val richContent = mode == TextDialog.Mode.MD.name || mode == TextDialog.Mode.HTML.name
    val plainText = if (content.length >= 32 * 1024) {
        content.take(32 * 1024) + "\n\n数据太大，无法全部显示…"
    } else {
        content
    }

    LaunchedEffect(textView, content, mode) {
        val view = textView ?: return@LaunchedEffect
        when (mode) {
            TextDialog.Mode.MD.name -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    view.setTextClassifier(TextClassifier.NO_OP)
                }
                val markdown = withContext(IO) { markwon.toMarkdown(content) }
                view.setMarkdown(markwon, markdown, onImageLongClick)
            }
            TextDialog.Mode.HTML.name -> view.setHtml(content)
            else -> Unit
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column {
            LegadoDialogHeader(title, onClose) {
                if (badgeSeconds != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badgeSeconds.toString(),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.ic_code),
                        contentDescription = stringResource(R.string.edit_content),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (richContent) {
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { viewContext ->
                        ScrollTextView(viewContext, null).apply {
                            id = R.id.text_view
                            setTextColor(textColor)
                            setTextIsSelectable(true)
                            setPadding(
                                (12 * resources.displayMetrics.density).toInt(),
                                (12 * resources.displayMetrics.density).toInt(),
                                (12 * resources.displayMetrics.density).toInt(),
                                (12 * resources.displayMetrics.density).toInt()
                            )
                            textView = this
                        }
                    },
                    update = { textView = it }
                )
            } else {
                SelectionContainer {
                    Text(
                        text = plainText,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TextDialogPreview() {
    LegadoTheme {
        TextDialogContent("Preview", "Plain text content", TextDialog.Mode.TEXT.name, null, {}, {}, {})
    }
}
