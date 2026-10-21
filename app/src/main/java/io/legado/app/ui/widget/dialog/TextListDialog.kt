package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.base.BaseDialogFragment
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoDialogHeader
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.setLayout

@Suppress("unused")
class TextListDialog() : BaseDialogFragment(0) {

    constructor(title: String, values: ArrayList<String>) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putStringArrayList("values", values)
        }
    }

    private var titleText by mutableStateOf("")
    private var values by mutableStateOf(emptyList<String>())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                TextListDialogContent(titleText, values, ::dismiss)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        titleText = arguments?.getString("title").orEmpty()
        values = arguments?.getStringArrayList("values")?.toList().orEmpty()
    }
}

@Composable
internal fun TextListDialogContent(title: String, values: List<String>, onClose: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyTextSizeSp = MaterialTheme.typography.bodyLarge.fontSize.value
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp
    ) {
        Column {
            LegadoDialogHeader(title, onClose)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(values, key = { index, _ -> index }) { _, text ->
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        factory = { context ->
                            TextView(context).apply {
                                id = View.generateViewId()
                                setTextIsSelectable(true)
                                setAutoLinkMask(Linkify.WEB_URLS)
                                linksClickable = true
                                setTextColor(textColor)
                                textSize = bodyTextSizeSp
                                val horizontalInset = (12 * resources.displayMetrics.density).toInt()
                                val verticalInset = (6 * resources.displayMetrics.density).toInt()
                                setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset)
                            }
                        },
                        update = { it.text = text }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TextListDialogPreview() {
    LegadoTheme { TextListDialogContent("Text list", listOf("A line", "https://example.com"), {}) }
}
