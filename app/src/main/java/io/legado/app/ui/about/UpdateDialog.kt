package io.legado.app.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.update.AppUpdate
import io.legado.app.model.Download
import io.legado.app.ui.widget.compose.*
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin

class UpdateDialog() : BaseDialogFragment(0) {
    constructor(updateInfo: AppUpdate.UpdateInfo) : this() {
        arguments = Bundle().apply {
            putString("newVersion", updateInfo.tagName)
            putString("updateBody", updateInfo.updateLog)
            putString("url", updateInfo.downloadUrl)
            putString("name", updateInfo.fileName)
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        LegadoComposeView(requireContext()).apply {
            setContent { LegadoTheme {
                arguments?.getString("updateBody")?.let { body ->
                    UpdateContent(arguments?.getString("newVersion").orEmpty(), body,
                        arguments?.getString("url") != null && arguments?.getString("name") != null,
                        ::download, ::dismiss)
                }
            } }
        }
    override fun onStart() { super.onStart(); setLayout(0.9f, 0.9f) }
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (arguments?.getString("updateBody") == null) { toastOnUi("没有数据"); dismiss() }
    }
    private fun download() {
        val url = arguments?.getString("url") ?: return
        val name = arguments?.getString("name") ?: return
        Download.start(requireContext(), url, name)
        toastOnUi(R.string.download_start)
    }
}

@Composable
internal fun UpdateContent(version: String, body: String, canDownload: Boolean,
    onDownload: () -> Unit, onClose: () -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.navigationBarsPadding()) {
            LegadoDialogHeader(version, onClose)
            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                factory = { context -> ScrollTextView(context, null).apply { setTextIsSelectable(true) } },
                onRelease = { it.text = null }, update = { view ->
                    view.setTextColor(textColor)
                    if (view.tag != body) {
                        view.tag = body
                        Markwon.builder(view.context)
                            .usePlugin(GlideImagesPlugin.create(view.context))
                            .usePlugin(HtmlPlugin.create())
                            .usePlugin(TablePlugin.create(view.context)).build().setMarkdown(view, body)
                    }
                })
            Button(onDownload, enabled = canDownload,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.download_start))
            }
        }
    }
}
