package io.legado.app.ui.widget.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LegadoPressFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pressReversesAndReducedMotionRestoresFullSize() {
        val interactions = MutableInteractionSource()
        val enabled = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLegadoMotion provides LegadoMotion(enabled.value)) {
                Box(Modifier.size(100.dp).legadoPressFeedback(interactions).testTag("press"))
            }
        }
        val node = compose.onNodeWithTag("press")
        val width = node.fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.autoAdvance = false
        val first = PressInteraction.Press(Offset.Zero)
        runBlocking { interactions.emit(first) }
        compose.mainClock.advanceTimeBy(160)
        assertTrue(node.fetchSemanticsNode().boundsInRoot.width < width)
        runBlocking { interactions.emit(PressInteraction.Release(first)) }
        compose.mainClock.advanceTimeBy(32)
        val second = PressInteraction.Press(Offset.Zero)
        runBlocking { interactions.emit(second) }
        compose.mainClock.advanceTimeBy(32)
        runBlocking { interactions.emit(PressInteraction.Cancel(second)) }
        compose.mainClock.advanceTimeBy(800)
        assertEquals(width, node.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        val third = PressInteraction.Press(Offset.Zero)
        runBlocking { interactions.emit(third) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { enabled.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(width, node.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        runBlocking { interactions.emit(PressInteraction.Cancel(third)) }
    }
}
