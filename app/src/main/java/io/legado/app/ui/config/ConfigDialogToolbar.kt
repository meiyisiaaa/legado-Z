package io.legado.app.ui.config

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils

/** Keeps config dialogs on Legado's compact, primary-color toolbar hierarchy. */
@Composable
internal fun ConfigDialogToolbar(
    title: String,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val primaryColorInt = if (preview) null else ThemeStore.primaryColor(context)
    val primaryColor = primaryColorInt?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val titleColor = primaryColorInt?.let {
        Color(context.getPrimaryTextColor(ColorUtils.isColorLight(it)))
    } ?: MaterialTheme.colorScheme.onPrimary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = primaryColor,
        contentColor = titleColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            actions()
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_close),
                    contentDescription = stringResource(R.string.close)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfigDialogToolbarPreview() {
    io.legado.app.ui.widget.compose.LegadoTheme {
        ConfigDialogToolbar("设置", {}) {
            TextButton(onClick = {}) { Text("导入") }
        }
    }
}
