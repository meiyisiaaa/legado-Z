package io.legado.app.ui.widget.compose

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Typography.bookTitle: TextStyle
    get() = titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 26.sp)
