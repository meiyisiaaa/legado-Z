package io.legado.app.ui.widget.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Drawing-only feedback leaves measurement and the 48dp touch target stable. */
@Composable
internal fun Modifier.legadoPressFeedback(interactions: InteractionSource): Modifier {
    val motion = LocalLegadoMotion.current
    val pressed by interactions.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && motion.enabled) 0.985f else 1f,
        motion.pressSpec(), label = "legado press")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
