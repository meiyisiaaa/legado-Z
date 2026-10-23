package io.legado.app.ui.widget.compose

import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils as CoreColorUtils
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import kotlin.math.abs

private const val MIN_ACCENT_SURFACE_CONTRAST = 4.5
private const val ACCENT_LIGHTNESS_SEARCH_STEPS = 24

internal fun contrastSafeAccent(accent: Color, surfaces: List<Color>): Color {
    val surfaceColors = surfaces.map { it.toArgb() }
    fun hasEnoughContrast(candidate: Color): Boolean {
        val candidateColor = candidate.toArgb()
        return surfaceColors.all {
            CoreColorUtils.calculateContrast(candidateColor, it) >= MIN_ACCENT_SURFACE_CONTRAST
        }
    }

    if (hasEnoughContrast(accent)) return accent

    val hsl = FloatArray(3)
    CoreColorUtils.colorToHSL(accent.toArgb(), hsl)
    val originalLightness = hsl[2]

    fun colorAt(lightness: Float): Color = Color(
        CoreColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], lightness))
    )

    fun candidateToward(lighten: Boolean): Pair<Color, Float>? {
        var failingLightness = originalLightness
        var passingLightness = if (lighten) 1f else 0f
        if (!hasEnoughContrast(colorAt(passingLightness))) return null

        repeat(ACCENT_LIGHTNESS_SEARCH_STEPS) {
            val middle = (failingLightness + passingLightness) / 2f
            if (hasEnoughContrast(colorAt(middle))) passingLightness = middle
            else failingLightness = middle
        }

        val candidate = colorAt(passingLightness)
        return if (hasEnoughContrast(candidate)) candidate to passingLightness else null
    }

    val nearestCandidate = listOfNotNull(
        candidateToward(lighten = false),
        candidateToward(lighten = true)
    ).minByOrNull { abs(it.second - originalLightness) }
    if (nearestCandidate != null) return nearestCandidate.first

    // Custom foreground/background choices can make one shared 4.5:1 tone impossible.
    fun minimumContrast(candidate: Color): Double = surfaceColors.minOf {
        CoreColorUtils.calculateContrast(candidate.toArgb(), it)
    }
    return if (minimumContrast(Color.Black) >= minimumContrast(Color.White)) {
        Color.Black
    } else {
        Color.White
    }
}

data class LegadoMotion(val enabled: Boolean = true) {
    val press: Int get() = if (enabled) 120 else 0
    val state: Int get() = if (enabled) 240 else 0
    val container: Int get() = if (enabled) 360 else 0
    fun <T> pressSpec(): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1f, stiffness = 700f) else snap()
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
    val background = if (preview) Color(if (dark) 0xFF151D1B else 0xFFF7F7F2)
        else Color(ThemeStore.backgroundColor(context))
    val foreground = if (preview) Color(if (dark) 0xFFFFFFFF else 0xFF212121)
        else Color(ThemeStore.textColorPrimary(context))
    val secondaryForeground = if (ink) foreground else if (preview)
        Color(if (dark) 0xFFBDBDBD else 0xFF616161) else Color(ThemeStore.textColorSecondary(context))
    val accent = when {
        ink -> Color.Black
        preview -> Color(if (dark) 0xFFA3D4C1 else 0xFF24675B)
        else -> Color(ThemeStore.accentColor(context))
    }
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
    val surfaceVariant = lerp(background, foreground, if (ink) 0f else 0.055f)
    val surfaceContainerLowest = background
    val surfaceContainerLow = lerp(background, foreground, if (ink) 0f else 0.025f)
    val surfaceContainer = lerp(background, foreground, if (ink) 0f else 0.045f)
    val surfaceContainerHigh = lerp(background, foreground, if (ink) 0f else 0.07f)
    val surfaceContainerHighest = lerp(background, foreground, if (ink) 0f else 0.1f)
    val accentSurfaces = listOf(
        surface,
        surfaceVariant,
        surfaceContainerLowest,
        surfaceContainerLow,
        surfaceContainer,
        surfaceContainerHigh,
        surfaceContainerHighest
    ).map { it.compositeOver(background) }
    val accessibleAccent = remember(accent, background, foreground, ink) {
        contrastSafeAccent(accent, accentSurfaces)
    }
    val onAccent = remember(accessibleAccent) {
        val accentColor = accessibleAccent.toArgb()
        val blackContrast = CoreColorUtils.calculateContrast(Color.Black.toArgb(), accentColor)
        val whiteContrast = CoreColorUtils.calculateContrast(Color.White.toArgb(), accentColor)
        if (blackContrast >= whiteContrast) Color.Black else Color.White
    }
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    val coverField = when {
        ink -> Color.White
        preview -> Color(if (dark) 0xFF172E2A else 0xFF183F38)
        else -> Color(ThemeStore.primaryColor(context))
    }
    val onCoverField = if (CoreColorUtils.calculateContrast(Color.White.toArgb(), coverField.toArgb()) >=
        CoreColorUtils.calculateContrast(Color.Black.toArgb(), coverField.toArgb())) Color.White else Color.Black
    MaterialTheme(
        typography = legadoTypography,
        colorScheme = colors.copy(
            primary = accessibleAccent,
            onPrimary = onAccent,
            primaryContainer = lerp(background, accent, if (ink) 0f else 0.16f),
            onPrimaryContainer = foreground,
            secondary = accessibleAccent,
            onSecondary = onAccent,
            secondaryContainer = lerp(background, accent, if (ink) 0f else 0.1f),
            onSecondaryContainer = foreground,
            tertiary = accessibleAccent,
            onTertiary = onAccent,
            tertiaryContainer = coverField,
            onTertiaryContainer = onCoverField,
            inversePrimary = if (ink) Color.Black else lerp(Color(0xFFD1D69A), accent, 0.12f),
            inverseSurface = foreground,
            inverseOnSurface = background,
            error = if (ink) Color.Black else if (dark) Color(0xFFFFB4AA) else Color(0xFF9C362B),
            onError = if (ink) Color.White else if (dark) Color(0xFF5C1914) else Color.White,
            errorContainer = if (ink) Color.White else if (dark) Color(0xFF5C1914) else Color(0xFFFFDAD4),
            onErrorContainer = if (ink) Color.Black else if (dark) Color(0xFFFFDAD4) else Color(0xFF42110D),
            background = surface,
            onBackground = foreground,
            surface = surface,
            surfaceTint = Color.Transparent,
            onSurface = foreground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = secondaryForeground,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
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
