package io.legado.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * MD5Utils 首个 JVM 单测样本。
 * 验证纯函数行为，不依赖 Android 上下文，可在 `./gradlew :app:testDebugUnitTest` 下运行。
 */
class MD5UtilsTest {

    @Test
    fun `md5Encode 空串符合标准 MD5`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", MD5Utils.md5Encode(""))
    }

    @Test
    fun `md5Encode abc 符合标准 MD5`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", MD5Utils.md5Encode("abc"))
    }

    @Test
    fun `md5Encode16 是完整 MD5 的中间子串`() {
        val full = MD5Utils.md5Encode("abc")
        assertEquals(full.substring(8, 24), MD5Utils.md5Encode16("abc"))
    }
}
