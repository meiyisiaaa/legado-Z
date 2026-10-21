package io.legado.app.ui.qrcode

import android.view.View
import android.widget.ImageView
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.zxing.BarcodeCameraScanFragment
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager
import com.king.zxing.analyze.MultiFormatAnalyzer
import io.legado.app.R
import kotlin.math.roundToInt

class QrCodeFragment : BarcodeCameraScanFragment() {

    override fun initUI() {
        super.initUI()
        val flashlight = ivFlashlight ?: return
        val minTouchTarget = (48 * flashlight.resources.displayMetrics.density).roundToInt()
        flashlight.minimumWidth = maxOf(flashlight.minimumWidth, minTouchTarget)
        flashlight.minimumHeight = maxOf(flashlight.minimumHeight, minTouchTarget)
        (flashlight as? ImageView)?.scaleType = ImageView.ScaleType.CENTER
        updateFlashlightAccessibilityLabel(flashlight)

        // Keep the library's torch toggle behavior and update the action label to match its state.
        flashlight.setOnClickListener {
            onClickFlashlight()
            updateFlashlightAccessibilityLabel(flashlight)
        }
    }

    private fun updateFlashlightAccessibilityLabel(flashlight: View) {
        flashlight.contentDescription = getString(
            if (flashlight.isSelected) R.string.qr_flashlight_turn_off
            else R.string.qr_flashlight_turn_on
        )
    }

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        //初始化解码配置
        val decodeConfig = DecodeConfig()
        //如果只有识别二维码的需求，这样设置效率会更高，不设置默认为DecodeFormatManager.DEFAULT_HINTS
        decodeConfig.hints = DecodeFormatManager.QR_CODE_HINTS
        //设置是否全区域识别，默认false
        decodeConfig.isFullAreaScan = true
        //设置识别区域比例，默认0.8，设置的比例最终会在预览区域裁剪基于此比例的一个矩形进行扫码识别
        decodeConfig.areaRectRatio = 0.8f

        //在启动预览之前，设置分析器，只识别二维码
        cameraScan.setAnalyzer(MultiFormatAnalyzer(decodeConfig))
    }

    override fun onScanResultCallback(result: AnalyzeResult<Result>) {
        cameraScan.setAnalyzeImage(false)
        (activity as? QrCodeActivity)?.onScanResultCallback(result.result)
    }

}
