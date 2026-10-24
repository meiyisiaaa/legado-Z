package io.legado.app.ui.widget

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class SearchViewContrastInstrumentedTest {

    @Test
    fun hintColorsFollowInputColorsInLightAndDarkThemesAndQueryStillClears() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val enabledColor = Color.rgb(36, 52, 71)
        val disabledColor = Color.rgb(128, 128, 128)
        val enabledState = intArrayOf(android.R.attr.state_enabled)
        val disabledState = intArrayOf(-android.R.attr.state_enabled)
        val inputColors = ColorStateList(
            arrayOf(disabledState, enabledState),
            intArrayOf(disabledColor, enabledColor)
        )

        listOf(R.style.AppTheme_Light, R.style.AppTheme_Dark).forEach { theme ->
            instrumentation.runOnMainSync {
                val themedContext = ContextThemeWrapper(targetContext, theme)
                val parent = FrameLayout(themedContext)
                val searchView = LayoutInflater.from(themedContext)
                    .inflate(R.layout.view_search, parent, false) as SearchView
                parent.addView(searchView)
                val input = searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
                searchView.queryHint = "hint fixture"
                input.setTextColor(inputColors)
                val minTouchHeight = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    48f,
                    searchView.resources.displayMetrics
                ).roundToInt()
                val narrowWindowWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    110f,
                    searchView.resources.displayMetrics
                ).roundToInt()
                assertTrue("search touch target should be at least 48dp", searchView.layoutParams.height >= minTouchHeight)
                val searchParams = searchView.layoutParams as ViewGroup.MarginLayoutParams
                val parentHeight = searchParams.height + searchParams.topMargin + searchParams.bottomMargin

                measureAndLayout(parent, 600, parentHeight)
                measureAndLayout(parent, 600, parentHeight)
                assertTrue("search input target should be at least 48dp", input.measuredHeight >= minTouchHeight)

                assertEquals(
                    input.textColors.getColorForState(enabledState, Color.TRANSPARENT),
                    input.hintTextColors.getColorForState(enabledState, Color.TRANSPARENT)
                )
                assertEquals(enabledColor, input.currentTextColor)
                assertEquals(enabledColor, input.currentHintTextColor)

                input.isEnabled = false
                assertEquals(
                    input.textColors.getColorForState(disabledState, Color.TRANSPARENT),
                    input.hintTextColors.getColorForState(disabledState, Color.TRANSPARENT)
                )
                assertEquals(disabledColor, input.currentTextColor)
                assertEquals(disabledColor, input.currentHintTextColor)

                input.isEnabled = true
                searchView.setQuery("query fixture", false)
                assertEquals("query fixture", searchView.query.toString())
                val clearButton = searchView.findViewById<View>(androidx.appcompat.R.id.search_close_btn)
                forceLayoutTree(parent)
                measureAndLayout(parent, narrowWindowWidth, parentHeight)
                assertTrue(
                    "search input width ${input.measuredWidth}px should be at least ${minTouchHeight}px " +
                        "inside a 110dp toolbar",
                    input.measuredWidth >= minTouchHeight
                )
                assertTrue(
                    "clear action width ${clearButton.measuredWidth}px should be at least ${minTouchHeight}px",
                    clearButton.measuredWidth >= minTouchHeight
                )
                assertTrue(
                    "clear action height ${clearButton.measuredHeight}px should be at least ${minTouchHeight}px",
                    clearButton.measuredHeight >= minTouchHeight
                )
                assertTrue("query should expose the clear action", clearButton.performClick())
                assertEquals("", searchView.query.toString())
            }
        }
    }

    @Test
    fun centeredHintIconUsesTheCurrentHintPaintColor() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val drawable = ColorDrawable(Color.WHITE).apply {
                setBounds(0, 0, 10, 10)
            }
            val span = SearchView.CenteredImageSpan(drawable)
            val paint = Paint().apply { textSize = 16f }
            val firstColor = Color.rgb(22, 96, 140)
            val secondColor = Color.rgb(94, 50, 132)

            paint.color = firstColor
            span.draw(canvas, " ", 0, 1, 10f, 0, 20, 40, paint)
            assertPixelsContainOnlyTint(bitmap, firstColor, Color.WHITE)

            bitmap.eraseColor(Color.TRANSPARENT)
            paint.color = secondColor
            span.draw(canvas, " ", 0, 1, 10f, 0, 20, 40, paint)
            assertPixelsContainOnlyTint(bitmap, secondColor, firstColor)
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertPixelsContainOnlyTint(bitmap: Bitmap, expectedColor: Int, otherColor: Int) {
        var containsExpected = false
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (color == expectedColor) containsExpected = true
                assertFalse("span retained a previous or untinted color", color == otherColor)
            }
        }
        assertTrue("span did not draw with the current paint color", containsExpected)
    }

    private fun measureAndLayout(parent: FrameLayout, width: Int, height: Int) {
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, width, height)
    }

    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                forceLayoutTree(view.getChildAt(index))
            }
        }
    }
}
