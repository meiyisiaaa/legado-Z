package io.legado.app.ui.widget.compose

import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.legado.app.help.config.AppConfig
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.lib.theme.ThemeStore

data class LegadoMotion(val enabled: Boolean = true) {
    val press: Int get() = if (enabled) 120 else 0
    val state: Int get() = if (enabled) 240 else 0
    val container: Int get() = if (enabled) 360 else 0
}

val LocalLegadoMotion = staticCompositionLocalOf { LegadoMotion() }

private fun uiText(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = weight,
    fontSize = size.sp, lineHeight = height.sp, letterSpacing = 0.sp
)

private val legadoTypography = Typography(
    displayLarge = uiText(32, 40, FontWeight.SemiBold),
    displayMedium = uiText(28, 36, FontWeight.SemiBold),
    displaySmall = uiText(26, 34, FontWeight.SemiBold),
    headlineLarge = uiText(24, 32, FontWeight.SemiBold),
    headlineMedium = uiText(22, 30, FontWeight.SemiBold),
    headlineSmall = uiText(20, 28, FontWeight.SemiBold),
    titleLarge = uiText(20, 28, FontWeight.Medium),
    titleMedium = uiText(16, 22, FontWeight.Medium),
    titleSmall = uiText(14, 20, FontWeight.Medium),
    bodyLarge = uiText(16, 24), bodyMedium = uiText(14, 22), bodySmall = uiText(13, 18),
    labelLarge = uiText(14, 20, FontWeight.Medium),
    labelMedium = uiText(12, 16, FontWeight.Medium),
    labelSmall = uiText(11, 14, FontWeight.Medium)
)

/** The existing preference store remains the authority during mixed View/Compose migration. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LegadoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val ink = !preview && AppConfig.isEInkMode
    val motionEnabled = !preview && !ink && ValueAnimator.areAnimatorsEnabled()
    val rippleConfiguration = LocalRippleConfiguration.current
    val dark = if (preview) isSystemInDarkTheme() else AppConfig.isNightTheme && !ink
    val background = if (preview) Color(if (dark) 0xFF212121 else 0xFFF5F5F5)
        else Color(ThemeStore.backgroundColor(context))
    val foreground = if (preview) Color(if (dark) 0xFFFFFFFF else 0xFF212121)
        else Color(ThemeStore.textColorPrimary(context))
    val secondaryForeground = if (ink) foreground else if (preview)
        Color(if (dark) 0xFFBDBDBD else 0xFF616161) else Color(ThemeStore.textColorSecondary(context))
    val accent = when {
        ink -> Color.Black
        preview -> Color(if (dark) 0xFFD84315 else 0xFFE53935)
        else -> Color(ThemeStore.accentColor(context))
    }
    val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White
    var hostContext = context
    while (hostContext is ContextWrapper && hostContext !is BaseActivity<*>) {
        val wrapped = hostContext.baseContext
        if (wrapped === hostContext) break
        hostContext = wrapped
    }
    val hostActivity = hostContext as? BaseActivity<*>
    val inActivityWindow = hostActivity != null && LocalView.current.rootView === hostActivity.window.decorView
    val surface = if (!preview && hostActivity?.hasImageBackground == true && inActivityWindow)
        Color.Transparent else background
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        typography = legadoTypography,
        colorScheme = colors.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(background, accent, if (ink) 0f else 0.16f),
            onPrimaryContainer = foreground,
            secondary = accent,
            secondaryContainer = lerp(background, accent, if (ink) 0f else 0.1f),
            onSecondaryContainer = foreground,
            background = surface,
            onBackground = foreground,
            surface = surface,
            surfaceTint = Color.Transparent,
            onSurface = foreground,
            surfaceVariant = lerp(background, foreground, if (ink) 0f else 0.055f),
            onSurfaceVariant = secondaryForeground,
            surfaceContainerLowest = background,
            surfaceContainerLow = lerp(background, foreground, if (ink) 0f else 0.025f),
            surfaceContainer = lerp(background, foreground, if (ink) 0f else 0.045f),
            surfaceContainerHigh = lerp(background, foreground, if (ink) 0f else 0.07f),
            surfaceContainerHighest = lerp(background, foreground, if (ink) 0f else 0.1f),
            outline = lerp(background, foreground, if (ink) 1f else 0.48f),
            outlineVariant = lerp(background, foreground, if (ink) 1f else 0.16f)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(24.dp)
        )
    ) {
        CompositionLocalProvider(
            LocalContentColor provides foreground,
            LocalLegadoMotion provides LegadoMotion(motionEnabled),
            LocalRippleConfiguration provides if (motionEnabled) rippleConfiguration else null,
            content = content
        )
    }
}

fun LegadoComposeView(context: Context): ComposeView = ComposeView(context).apply {
    id = R.id.legado_compose_host
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
}
