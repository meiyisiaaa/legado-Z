package io.legado.app.ui.book.source.manage

import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BookSourceMainJsExportTest {

    @Test
    fun acceptsOneNonBlankMainJsAndPreservesItsContents() {
        val script = "\nfunction search() { return '结果'; }\n"

        val export = requireMainJsExport(
            listOf(BookSource(bookSourceName = "测试源", mainJs = script))
        )

        assertEquals("测试源", export.sourceName)
        assertEquals(script, export.script)
    }

    @Test
    fun requiresExactlyOneSource() {
        assertFailure(MainJsExportFailure.SELECT_ONE) {
            requireMainJsExport(emptyList())
        }
        assertFailure(MainJsExportFailure.SELECT_ONE) {
            requireMainJsExport(listOf(BookSource(), BookSource()))
        }
    }

    @Test
    fun rejectsMissingSourceAndBlankMainJs() {
        assertFailure(MainJsExportFailure.SOURCE_MISSING) {
            requireMainJsExport(listOf(null))
        }
        assertFailure(MainJsExportFailure.SCRIPT_EMPTY) {
            requireMainJsExport(listOf(BookSource(mainJs = " \n\t")))
        }
    }

    private inline fun assertFailure(
        expected: MainJsExportFailure,
        action: () -> Unit
    ) {
        try {
            action()
            fail("expected export validation to fail")
        } catch (error: MainJsExportException) {
            assertEquals(expected, error.reason)
        }
    }
}
