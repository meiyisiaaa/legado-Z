package io.legado.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * [AiConfig] 单测。
 *
 * [AiConfig] 是 `io.legado.app.ai` 包内唯一允许触碰 Android API 的文件
 * （通过 `appCtx` + SharedPreferences 持久化），因此它**无法在纯 JVM 环境运行** ——
 * 必须借助 Robolectric 提供真实 `Context` 与 `SharedPreferences`。
 * 其余 10 个 ai 内核测试均为纯 JVM；本类是唯一需要 Robolectric 的例外。
 *
 * 覆盖：save ↔ snapshot 往返、isConfigured 判定、clear 清空、非法配置不写入。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiConfigTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        // 各用例相互独立：开始前先清掉上一例可能写入的偏好。
        AiConfig.clear()
    }

    @Test
    fun `save后snapshot可还原配置`() {
        val opts = AiTestFixtures.options().copy(
            endpoint = "https://api.example.com/v1",
            apiKey = "sk-robolectric-key",
            model = "gpt-4o-mini",
            temperature = 0.5f,
            autoRepair = false
        )

        val result = AiConfig.save(opts)
        if (result.hasError()) {
            assertEquals(AiConfigValidator.FIELD_API_KEY, result.errors().single().field)
            assertFalse("Keystore 不可用时不得写入半成品配置", AiConfig.isConfigured())
            return
        }
        assertTrue(AiConfig.isConfigured())

        val snap = AiConfig.snapshot()
        assertEquals("https://api.example.com/v1", snap.endpoint)
        assertEquals("sk-robolectric-key", snap.apiKey)
        assertEquals("gpt-4o-mini", snap.model)
        assertEquals(0.5f, snap.temperature)
        assertEquals(false, snap.autoRepair)
    }

    @Test
    fun `未配置时isConfigured为false`() {
        assertFalse(AiConfig.isConfigured())
        assertEquals("", AiConfig.snapshot().endpoint)
        assertEquals("", AiConfig.snapshot().apiKey)
    }

    @Test
    fun `clear清空全部偏好`() {
        val result = AiConfig.save(AiTestFixtures.options())
        if (!result.hasError()) assertTrue(AiConfig.isConfigured())

        AiConfig.clear()
        assertFalse(AiConfig.isConfigured())
        assertEquals("", AiConfig.snapshot().endpoint)
        assertEquals("", AiConfig.snapshot().apiKey)
    }

    @Test
    fun `配置非法时不写入偏好`() {
        val bad = AiTestFixtures.options().copy(temperature = 99f)
        val result = AiConfig.save(bad)

        assertTrue("非法配置应被拦截", result.hasError())
        assertFalse("非法配置不应写入任何偏好", AiConfig.isConfigured())
    }
}
