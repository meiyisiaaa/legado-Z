package io.legado.app.ui.widget.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.activity

/** Matches TitleBar so mixed View/Compose screens retain the same system-bar contrast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegadoTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val ink = !preview && AppConfig.isEInkMode
    val primary = if (preview) MaterialTheme.colorScheme.primary else Color(ThemeStore.primaryColor(context))
    val view = LocalView.current
    val inActivityWindow = view.activity?.window?.decorView === view.rootView
    val background = when {
        ink -> Color.White
        !preview && inActivityWindow && ThemeStore.transparentNavBar(context) -> Color.Transparent
        else -> primary
    }
    val foreground = if (ink) Color.Black else if (preview) MaterialTheme.colorScheme.onPrimary else
        Color(context.getPrimaryTextColor(ColorUtils.isColorLight(primary.toArgb())))
    TopAppBar(
        title = title,
        modifier = modifier.drawWithContent {
            drawContent()
            if (ink) drawLine(Color.Black, Offset(0f, size.height - 0.5.dp.toPx()),
                Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
        },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = 56.dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = background,
            scrolledContainerColor = background,
            navigationIconContentColor = foreground,
            titleContentColor = foreground,
            actionIconContentColor = foreground
        )
    )
}
