package io.legado.app.ui.association

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.viewModels
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.help.source.SourceVerificationHelp
import io.legado.app.model.ImageProvider
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment

/** 图片验证码输入；保留 Glide 的来源 Cookie/鉴权与图片回看链路。 */
class VerificationCodeDialog() : BaseDialogFragment(R.layout.dialog_verification_code_view) {

    constructor(
        imageUrl: String,
        sourceOrigin: String? = null,
        sourceName: String? = null,
        sourceType: Int
    ) : this() {
        arguments = Bundle().apply {
            putString("imageUrl", imageUrl)
            putString("sourceOrigin", sourceOrigin)
            putString("sourceName", sourceName)
            putInt("sourceType", sourceType)
        }
    }

    private val viewModel by viewModels<VerificationCodeViewModel>()
    private var sourceOrigin: String? = null
    private var imageUrl: String = ""
    private var sourceName: String = ""
    private var verificationCode by mutableStateOf("")
    private var showDeleteConfirm by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        val args = arguments ?: return
        viewModel.initData(args)
        sourceOrigin = args.getString("sourceOrigin")
        sourceName = args.getString("sourceName").orEmpty()
        imageUrl = args.getString("imageUrl") ?: return
        view.findViewById<ComposeView>(R.id.composeView).setContent {
            LegadoTheme {
                VerificationCodeContent(
                    sourceName = sourceName,
                    imageUrl = imageUrl,
                    code = verificationCode,
                    onCodeChange = { verificationCode = it },
                    onOpenImage = { showDialogFragment(PhotoDialog(imageUrl, sourceOrigin)) },
                    onSubmit = {
                        SourceVerificationHelp.setResult(sourceOrigin!!, verificationCode)
                        dismiss()
                    },
                    onDisableSource = { viewModel.disableSource { dismiss() } },
                    onDeleteSource = { showDeleteConfirm = true },
                    showDeleteConfirm = showDeleteConfirm,
                    onDeleteDismiss = { showDeleteConfirm = false },
                    onDelete = {
                        showDeleteConfirm = false
                        viewModel.deleteSource { dismiss() }
                    },
                    loadImage = ::loadImage
                )
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun loadImage(url: String, imageView: ImageView) {
        ImageProvider.remove(url)
        ImageLoader.loadBitmap(requireContext(), url).apply {
            sourceOrigin?.let {
                apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, it))
            }
        }.error(R.drawable.image_loading_error)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>,
                    isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    val bitmap = resource.copy(resource.config!!, true)
                    ImageProvider.put(url, bitmap)
                    return false
                }
            })
            .into(imageView)
    }

    override fun onDestroy() {
        sourceOrigin?.let { SourceVerificationHelp.checkResult(it) }
        super.onDestroy()
        activity?.finish()
    }
}

@Composable
private fun VerificationCodeContent(
    sourceName: String,
    imageUrl: String,
    code: String,
    onCodeChange: (String) -> Unit,
    onOpenImage: () -> Unit,
    onSubmit: () -> Unit,
    onDisableSource: () -> Unit,
    onDeleteSource: () -> Unit,
    showDeleteConfirm: Boolean,
    onDeleteDismiss: () -> Unit,
    onDelete: () -> Unit,
    loadImage: (String, ImageView) -> Unit
) {
    var menuExpanded by androidx.compose.runtime.remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.draw)) },
            text = { Text(stringResource(R.string.sure_del) + "\n" + sourceName) },
            confirmButton = {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete_source)) }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.input_verification_code),
                    style = MaterialTheme.typography.titleLarge
                )
                if (sourceName.isNotBlank()) {
                    Text(
                        sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onSubmit) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.ok),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.disable_source)) },
                        onClick = { menuExpanded = false; onDisableSource() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_source)) },
                        onClick = { menuExpanded = false; onDeleteSource() }
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 220.dp),
                factory = { context ->
                    AppCompatImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setOnClickListener { onOpenImage() }
                        loadImage(imageUrl, this)
                    }
                },
                update = { imageView -> imageView.setOnClickListener { onOpenImage() } }
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.verification_code)) },
            singleLine = true
        )
    }
}
