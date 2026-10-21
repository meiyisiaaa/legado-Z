package io.legado.app.ui.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EditSafetyTest {
    @Test
    fun ordinaryAccessibilityTextIsUnchanged() {
        val text = "普通输入😀"
        assertSame(text, EditSafety.accessibilityBeforeText(text))
    }

    @Test
    fun oversizedAccessibilityHistoryUsesThePlatformTextLimit() {
        val text = "a".repeat(100_001)
        assertEquals(text.take(100_000), EditSafety.accessibilityBeforeText(text))
    }

    @Test
    fun accessibilityHistoryDoesNotSplitASurrogatePair() {
        val prefix = "a".repeat(99_999)
        assertEquals(prefix, EditSafety.accessibilityBeforeText(prefix + "😀tail"))
    }
}
