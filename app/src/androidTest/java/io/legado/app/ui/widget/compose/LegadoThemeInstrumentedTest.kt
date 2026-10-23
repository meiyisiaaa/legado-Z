package io.legado.app.ui.widget.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils as CoreColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun measuredLightAccentIsAdjustedForEverySurfaceToneWithoutChangingHue() {
        val accent = Color(0xFFE53935)
        val background = Color(0xFFEDEDED)
        val foreground = Color(0xFF212121)
        val surfaces = surfaceFamily(background, foreground)

        assertTrue(contrast(accent, background) < 4.5)
        val adjusted = contrastSafeAccent(accent, surfaces)

        assertTrue(surfaces.all { contrast(adjusted, it) >= 4.5 })
        assertEquals(accentHsl(accent)[0], accentHsl(adjusted)[0], 1f)
        assertEquals(accentHsl(accent)[1], accentHsl(adjusted)[1], 0.01f)
    }

    @Test
    fun readableAccentRemainsUnchanged() {
        val accent = Color(0xFF263238)
        val surfaces = surfaceFamily(Color(0xFFEDEDED), Color(0xFF212121))

        assertTrue(surfaces.all { contrast(accent, it) >= 4.5 })
        assertEquals(accent, contrastSafeAccent(accent, surfaces))
    }

    @Test
    fun lightPreviewAccentRolesMeetContrast() {
        assertPreviewAccentRoles(dark = false)
    }

    @Test
    fun darkPreviewAccentRolesMeetContrast() {
        assertPreviewAccentRoles(dark = true)
    }

    @Test fun mineralPresetsMatchTheNativeFallbackPalette() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val day = ThemeConfig.mineralDay
        val night = ThemeConfig.mineralNight
        listOf(day.primaryColor to R.color.mineral_primary,
            day.accentColor to R.color.mineral_accent,
            day.backgroundColor to R.color.mineral_paper,
            day.bottomBackground to R.color.mineral_bottom,
            night.primaryColor to R.color.mineral_primary_night,
            night.accentColor to R.color.mineral_accent_night,
            night.backgroundColor to R.color.mineral_paper_night,
            night.bottomBackground to R.color.mineral_bottom_night).forEach { (hex, resource) ->
            assertEquals(context.getColor(resource), android.graphics.Color.parseColor(hex))
        }
    }

    private fun assertPreviewAccentRoles(dark: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            val nightMode = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val capturedScheme = AtomicReference<ColorScheme?>()

        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalConfiguration provides configuration
            ) {
                LegadoTheme {
                    val scheme = MaterialTheme.colorScheme
                    SideEffect { capturedScheme.set(scheme) }
                }
            }
        }
        compose.waitForIdle()

        val scheme = requireNotNull(capturedScheme.get())
        val surfaces = listOf(
            scheme.surface.compositeOver(scheme.background),
            scheme.background,
            scheme.surfaceVariant.compositeOver(scheme.background),
            scheme.surfaceContainerLowest.compositeOver(scheme.background),
            scheme.surfaceContainerLow.compositeOver(scheme.background),
            scheme.surfaceContainer.compositeOver(scheme.background),
            scheme.surfaceContainerHigh.compositeOver(scheme.background),
            scheme.surfaceContainerHighest.compositeOver(scheme.background)
        )
        val originalAccent = Color(if (dark) 0xFFA3D4C1 else 0xFF24675B)

        assertEquals(contrastSafeAccent(originalAccent, surfaces), scheme.primary)
        assertEquals(scheme.primary, scheme.secondary)
        assertEquals(scheme.onPrimary, scheme.onSecondary)
        assertTrue(surfaces.all { contrast(scheme.primary, it) >= 4.5 })
        assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5)
        assertTrue(contrast(scheme.onSecondary, scheme.secondary) >= 4.5)
    }

    private fun surfaceFamily(background: Color, foreground: Color): List<Color> = listOf(
        background,
        lerp(background, foreground, 0.025f),
        lerp(background, foreground, 0.045f),
        lerp(background, foreground, 0.07f),
        lerp(background, foreground, 0.1f),
        lerp(background, foreground, 0.055f)
    )

    private fun accentHsl(color: Color): FloatArray = FloatArray(3).also {
        CoreColorUtils.colorToHSL(color.toArgb(), it)
    }

    private fun contrast(foreground: Color, background: Color): Double =
        CoreColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())

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
