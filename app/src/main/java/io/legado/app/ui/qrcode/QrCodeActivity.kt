package io.legado.app.ui.qrcode

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.commit
import com.google.zxing.Result
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityQrcodeCaptureBinding
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.compose.LegadoTheme
import io.legado.app.utils.QRCodeUtils
import io.legado.app.utils.readBytes
import io.legado.app.utils.viewbindingdelegate.viewBinding

class QrCodeActivity : BaseActivity<ActivityQrcodeCaptureBinding>(), ScanResultCallback {

    override val binding by viewBinding(ActivityQrcodeCaptureBinding::inflate)

    private val selectQrImage = registerForActivityResult(HandleFileContract()) {
        it.uri?.readBytes(this)?.let { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            onScanResultCallback(QRCodeUtils.parseCodeResult(bitmap))
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            LegadoTheme {
                ScannerSurface()
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.qr_code_scan, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_choose_from_gallery -> selectQrImage.launch {
                mode = HandleFileContract.IMAGE
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onScanResultCallback(result: Result?) {
        val intent = Intent()
        intent.putExtra("result", result?.text)
        setResult(RESULT_OK, intent)
        finish()
    }

    @Composable
    private fun ScannerSurface() {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    id = R.id.fl_content
                    if (supportFragmentManager.findFragmentByTag("qrCodeFragment") == null) {
                        supportFragmentManager.commit {
                            setReorderingAllowed(true)
                            replace(R.id.fl_content, QrCodeFragment(), "qrCodeFragment")
                        }
                    }
                }
            }
        )
    }

}
