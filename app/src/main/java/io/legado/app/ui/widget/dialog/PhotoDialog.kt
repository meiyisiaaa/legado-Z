package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.help.book.BookHelp
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.glide.OkHttpModelLoader
import io.legado.app.model.BookCover
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.compose.LegadoComposeView
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.ui.widget.compose.LocalLegadoMotion
import io.legado.app.ui.widget.image.PhotoView
import io.legado.app.utils.setLayout

/** 显示图片；保留 PhotoView 的缩放、拖动和旋转手势。 */
class PhotoDialog() : BaseDialogFragment(0) {

    constructor(src: String, sourceOrigin: String? = null, isBook: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString("src", src)
            putString("sourceOrigin", sourceOrigin)
            putBoolean("isBook", isBook)
        }
    }

    private var photoView: PhotoView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LegadoComposeView(requireContext()).apply {
        setContent {
            LegadoTheme {
                PhotoDialogContent(::loadImage)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = Unit

    @SuppressLint("CheckResult")
    private fun loadImage(view: PhotoView) {
        photoView = view
        val args = arguments ?: return
        val src = args.getString("src") ?: return
        ImageProvider.get(src)?.let {
            view.setImageBitmap(it)
            return
        }
        val isBook = args.getBoolean("isBook")
        val file = if (isBook) ReadBook.book?.let { book ->
            BookHelp.getImage(book, src)
        } else null
        if (file?.exists() == true) {
            ImageLoader.load(requireContext(), file)
                .error(R.drawable.image_loading_error)
                .dontTransform()
                .downsample(DownsampleStrategy.NONE)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(view)
        } else {
            ImageLoader.load(requireContext(), src).apply {
                args.getString("sourceOrigin")?.let { sourceOrigin ->
                    apply(RequestOptions().set(OkHttpModelLoader.sourceOriginOption, sourceOrigin))
                }
            }.error(if (isBook) BookCover.defaultDrawable else R.drawable.image_loading_error)
                .dontTransform()
                .downsample(DownsampleStrategy.NONE)
                .into(view)
        }
    }

    override fun onDestroyView() {
        photoView?.let { Glide.with(this).clear(it) }
        photoView = null
        super.onDestroyView()
    }
}

@Composable
internal fun PhotoDialogContent(onPhotoViewCreated: (PhotoView) -> Unit) {
    val animationEnabled = LocalLegadoMotion.current.enabled
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PhotoView(context).apply {
                    id = R.id.photo_view
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setAnimDuring(if (animationEnabled) getDefaultAnimDuring() else 0)
                    onPhotoViewCreated(this)
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PhotoDialogPreview() {
    LegadoTheme { PhotoDialogContent {} }
}
