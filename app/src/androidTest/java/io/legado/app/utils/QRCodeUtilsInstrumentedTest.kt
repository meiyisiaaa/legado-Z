package io.legado.app.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QRCodeUtilsInstrumentedTest {

    @Test
    fun parseLargeSquareQrPreservesPayload() {
        val payload = """{"bookSourceUrl":"https://qa.invalid/legado-z/qr-gallery/69eb7399d37542d3b940a5e8897db1fe","bookSourceName":"Legado QR Gallery QA 69eb7399d37542d3b940a5e8897db1fe","enabled":false,"enabledExplore":false}"""
        val bitmap = requireNotNull(QRCodeUtils.createQRCode(payload, heightPix = 1024))

        try {
            assertEquals(payload, QRCodeUtils.parseCodeResult(bitmap)?.text)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
