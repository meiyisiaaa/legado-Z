package io.legado.app.ui.main

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.help.config.AppConfig

internal data class MainDestination(val page: Int, val label: Int, val icon: Int, val selectedIcon: Int)

internal val mainDestinations = listOf(
    MainDestination(0, R.string.bookshelf, R.drawable.ic_bottom_books_e, R.drawable.ic_bottom_books_s),
    MainDestination(1, R.string.discovery, R.drawable.ic_bottom_explore_e, R.drawable.ic_bottom_explore_s),
    MainDestination(2, R.string.rss, R.drawable.ic_bottom_rss_feed_e, R.drawable.ic_bottom_rss_feed_s),
    MainDestination(3, R.string.my, R.drawable.ic_bottom_person_e, R.drawable.ic_bottom_person_s)
)

@Composable
internal fun MainNavigation(
    pages: List<Int>, selected: Int, updating: Int, onSelect: (Int) -> Unit
) {
    val destinations = mainDestinations.filter { it.page in pages }
    val barColor = if (LocalInspectionMode.current) MaterialTheme.colorScheme.surfaceContainer
        else Color(ThemeStore.bottomBackground(LocalContext.current))
    val labelColor = if (barColor.luminance() > 0.179f) Color.Black else Color.White
    val ink = !LocalInspectionMode.current && AppConfig.isEInkMode
    NavigationBar(modifier = Modifier.drawWithContent {
        drawContent()
        if (ink) drawLine(Color.Black, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx())
    }.navigationBarsPadding().height(56.dp),
        windowInsets = WindowInsets(0), containerColor = barColor) {
        destinations.forEach { item ->
            val label = stringResource(item.label)
            NavigationBarItem(
                modifier = Modifier.semantics { contentDescription = label },
                selected = selected == item.page,
                colors = NavigationBarItemDefaults.colors(selectedTextColor = labelColor,
                    selectedIconColor = MaterialTheme.colorScheme.primary, indicatorColor = Color.Transparent,
                    unselectedIconColor = labelColor.copy(alpha = 0.75f), unselectedTextColor = labelColor.copy(alpha = 0.75f)),
                onClick = { onSelect(item.page) },
                icon = { DestinationIcon(item, updating, selected == item.page) },
                label = null
            )
        }
    }
}

@Composable
private fun DestinationIcon(item: MainDestination, updating: Int, selected: Boolean) {
    BadgedBox(badge = {
        if (item.page == 0 && updating > 0) Badge { Text(updating.toString()) }
    }) {
        Icon(painterResource(if (selected) item.selectedIcon else item.icon), contentDescription = null, Modifier.size(24.dp))
    }
}
