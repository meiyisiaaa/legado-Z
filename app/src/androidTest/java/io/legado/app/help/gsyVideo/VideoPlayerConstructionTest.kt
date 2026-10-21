package io.legado.app.help.gsyVideo

import android.util.AttributeSet
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoPlayerConstructionTest {
    @Test
    fun allConstructionPathsInitializeComposeStateBeforeInstallingControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            listOf(
                VideoPlayer(context),
                VideoPlayer(context, true),
                VideoPlayer(context, null as AttributeSet?)
            ).forEach { player ->
                assertNotNull(player.findViewById<ComposeView>(R.id.compose_actions))
                val overlay = player.findViewById<ComposeView>(R.id.compose_overlay)
                assertNotNull(overlay)
                val start = player.findViewById<View>(R.id.start)
                assertNotNull(start)
                val originalStartVisibility = start.visibility
                assertEquals(View.INVISIBLE, overlay.visibility)
                player.showOverlayTip("Constructor regression")
                assertEquals(View.VISIBLE, overlay.visibility)
                assertEquals(View.INVISIBLE, start.visibility)
                player.showOverlayTip()
                assertEquals(View.INVISIBLE, overlay.visibility)
                assertEquals(originalStartVisibility, start.visibility)
                assertTrue(start.hasOnClickListeners())
            }
        }
    }
}
