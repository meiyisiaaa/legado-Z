package io.legado.app.ui.widget.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegadoThemeInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun uncoloredTextAndIconUseLightPreviewForeground() {
        assertPreviewForeground(dark = false)
    }

    @Test
    fun uncoloredTextAndIconUseDarkPreviewForeground() {
        assertPreviewForeground(dark = true)
    }

    private fun assertPreviewForeground(dark: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val themeForeground = AtomicReference<Color?>()
        val inheritedForeground = AtomicReference<Color?>()
        val textForeground = AtomicReference<Color?>()

        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalConfiguration provides configuration
            ) {
                LegadoTheme {
                    val foreground = MaterialTheme.colorScheme.onBackground
                    val contentColor = LocalContentColor.current
                    SideEffect {
                        themeForeground.set(foreground)
                        inheritedForeground.set(contentColor)
                    }
                    Column {
                        Text(
                            text = "Default foreground",
                            modifier = Modifier.testTag("default_text"),
                            onTextLayout = { textForeground.set(it.layoutInput.style.color) }
                        )
                        Icon(
                            painter = ColorPainter(Color.White),
                            contentDescription = "default icon",
                            modifier = Modifier.testTag("default_icon").size(24.dp)
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val expected = if (dark) Color.White else Color(0xFF212121)
        assertEquals(expected, themeForeground.get())
        assertEquals(expected, inheritedForeground.get())
        assertEquals(expected, textForeground.get())

        val iconPixels = compose.onNodeWithTag("default_icon").captureToImage().toPixelMap()
        assertEquals(expected, iconPixels[iconPixels.width / 2, iconPixels.height / 2])
    }
}
